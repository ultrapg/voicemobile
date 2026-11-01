package io.github.ultrapg.voicemobile

import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.data.ModelPreset
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.engine.AcceleratorBackend
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import io.github.ultrapg.voicemobile.engine.TtsLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelsCatalogTest {

    @Test
    fun presetAliasesMatchVoiceCliConventions() {
        assertEquals(ModelPreset.TINY_06B_Q4, ModelPreset.fromId("0.6b-q4"))
        assertEquals(ModelPreset.TINY_06B_Q4, ModelPreset.fromId("tiny"))
        assertEquals(ModelPreset.FAST_06B_Q8, ModelPreset.fromId("0.6b"))
        assertEquals(ModelPreset.FAST_06B_Q8, ModelPreset.fromId("fast"))
        assertEquals(ModelPreset.FULL_17B_Q4, ModelPreset.fromId("q4"))
        assertEquals(ModelPreset.FULL_17B_Q8, ModelPreset.fromId("q8"))
        assertEquals(ModelPreset.FULL_17B_BF16, ModelPreset.fromId("bf16"))
    }

    @Test
    fun talkerFileForEachModeMatchesHuggingFaceInventory() {
        val preset = ModelPreset.TINY_06B_Q4
        assertEquals(
            "qwen-talker-0.6b-customvoice-Q4_K_M.gguf",
            preset.talkerFileForMode(SynthesisMode.SPEAK)
        )
        assertEquals(
            "qwen-talker-0.6b-base-Q4_K_M.gguf",
            preset.talkerFileForMode(SynthesisMode.CLONE)
        )
        assertEquals(
            "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
            preset.talkerFileForMode(SynthesisMode.DESIGN)
        )
    }

    @Test
    fun languageIdsAndAcceleratorNativeIdsMatchCppAbi() {
        assertEquals(2050, TtsLanguage.ENGLISH.languageId)
        assertEquals(2055, TtsLanguage.CHINESE.languageId)
        assertEquals(2058, TtsLanguage.JAPANESE.languageId)
        assertEquals(-1, TtsLanguage.AUTO.languageId)

        assertEquals(0, AcceleratorBackend.AUTO.nativeId)
        assertEquals(1, AcceleratorBackend.CPU.nativeId)
        assertEquals(2, AcceleratorBackend.GPU.nativeId)
        assertEquals(3, AcceleratorBackend.NPU.nativeId)
    }

    @Test
    fun defaultPresetAndSpeakInstructionGuardMatchCapabilities() {
        val defaults = AppSettings()
        assertEquals(ModelPreset.FAST_06B_Q8, defaults.preset)
        assertEquals(0.40f, defaults.streamChunkSec, 1e-4f)
        assertFalse(defaults.supportsSpeakInstruction)
        assertFalse(defaults.copy(preset = ModelPreset.TINY_06B_Q4).supportsSpeakInstruction)
        assertTrue(defaults.copy(preset = ModelPreset.FULL_17B_Q4).supportsSpeakInstruction)
        assertTrue(defaults.copy(preset = ModelPreset.FULL_17B_Q8).supportsSpeakInstruction)
    }

    @Test
    fun ggufMagicVerificationAcceptsValidHeaderAndRejectsInvalidOrTruncatedCatalogFile() {
        val tempDir = Files.createTempDirectory("voicemobile_test").toFile()
        try {
            val valid = tempDir.resolve("custom_imported.gguf")
            val validBytes = ByteArray(128)
            validBytes[0] = 'G'.code.toByte()
            validBytes[1] = 'G'.code.toByte()
            validBytes[2] = 'U'.code.toByte()
            validBytes[3] = 'F'.code.toByte()
            valid.writeBytes(validBytes)
            assertTrue(ModelsCatalog.isValidGgufFile(valid))

            // Known catalog filename that was truncated mid-download must be rejected even with GGUF magic
            val truncatedCatalog = tempDir.resolve("qwen-tokenizer-12hz-Q8_0.gguf")
            truncatedCatalog.writeBytes(validBytes)
            assertFalse(ModelsCatalog.isValidGgufFile(truncatedCatalog))

            val invalid = tempDir.resolve("invalid.gguf")
            invalid.writeBytes(ByteArray(128) { 0x42 })
            assertFalse(ModelsCatalog.isValidGgufFile(invalid))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
