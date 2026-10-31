package io.github.ultrapg.voicemobile

import io.github.ultrapg.voicemobile.engine.ChunkMode
import io.github.ultrapg.voicemobile.engine.TextProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessorTest {

    @Test
    fun cleanMarkdown_stripsCodeLinksHeadersAndEmphasis() {
        val raw = """
            # Welcome to **Voice Mobile**
            
            Visit [Qwen3-TTS](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) for `GGUF` weights.
            
            ```kotlin
            val ignored = 42
            ```
            
            - First *clean* bullet
            - Second __clean__ item
        """.trimIndent()

        val cleaned = TextProcessor.cleanMarkdown(raw)
        assertFalse(cleaned.contains("https://"))
        assertFalse(cleaned.contains("```"))
        assertFalse(cleaned.contains("ignored"))
        assertFalse(cleaned.contains("**"))
        assertTrue(cleaned.contains("Welcome to Voice Mobile"))
        assertTrue(cleaned.contains("Visit Qwen3-TTS for GGUF weights."))
        assertTrue(cleaned.contains("First clean bullet"))
    }

    @Test
    fun splitIntoSentences_preservesAbbreviationsDecimalsAndEllipsis() {
        val input = "Dr. Smith paid $3.14 at 10 a.m. in St. Louis... Then he left! Right?"
        val sentences = TextProcessor.splitIntoSentences(input)
        assertEquals(3, sentences.size)
        assertEquals("Dr. Smith paid $3.14 at 10 a.m. in St. Louis...", sentences[0])
        assertEquals("Then he left!", sentences[1])
        assertEquals("Right?", sentences[2])
    }

    @Test
    fun splitIntoSentences_handlesCjkPunctuation() {
        val input = "你好世界。今天天气很好！真的吗？"
        val sentences = TextProcessor.splitIntoSentences(input)
        assertEquals(listOf("你好世界。", "今天天气很好！", "真的吗？"), sentences)
    }

    @Test
    fun prepareChunks_respectsAutoAndForceModes() {
        val paragraph = buildString {
            repeat(8) { idx ->
                append("Sentence number ${idx + 1} runs offline on Qualcomm Hexagon NPU with crisp audio fidelity. ")
            }
        }
        val singlePass = TextProcessor.prepareChunks(
            rawText = paragraph,
            cleanMarkdownEnabled = true,
            chunkMode = ChunkMode.FORCE_OFF,
            maxChunkChars = 200
        )
        assertEquals(1, singlePass.size)

        val chunked = TextProcessor.prepareChunks(
            rawText = paragraph,
            cleanMarkdownEnabled = true,
            chunkMode = ChunkMode.FORCE_ON,
            maxChunkChars = 200
        )
        assertTrue(chunked.size >= 3)
        assertTrue(chunked.all { it.length <= 200 })
    }
}
