package io.github.ultrapg.voicemobile

import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.data.ModelPreset
import io.github.ultrapg.voicemobile.data.ModelQuantization
import io.github.ultrapg.voicemobile.data.ModelRole
import io.github.ultrapg.voicemobile.data.ModelSizeTier
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import io.github.ultrapg.voicemobile.engine.TtsLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelsCatalogTest {

    @Test
    fun presetAliasesAndSizeQuantMatrixCoverAllEightConfigurations() {
        assertEquals(8, ModelPreset.entries.size)
        assertEquals(ModelPreset.TINY_06B_Q4, ModelPreset.fromId("0.6b-q4"))
        assertEquals(ModelPreset.TINY_06B_Q4, ModelPreset.fromId("tiny"))
        assertEquals(ModelPreset.FAST_06B_Q8, ModelPreset.fromId("0.6b"))
        assertEquals(ModelPreset.FAST_06B_Q8, ModelPreset.fromId("fast"))
        assertEquals(ModelPreset.FAST_06B_BF16, ModelPreset.fromId("0.6b-bf16"))
        assertEquals(ModelPreset.FAST_06B_F32, ModelPreset.fromId("0.6b-f32"))
        assertEquals(ModelPreset.FULL_17B_Q4, ModelPreset.fromId("q4"))
        assertEquals(ModelPreset.FULL_17B_Q8, ModelPreset.fromId("q8"))
        assertEquals(ModelPreset.FULL_17B_BF16, ModelPreset.fromId("bf16"))
        assertEquals(ModelPreset.FULL_17B_F32, ModelPreset.fromId("f32"))

        for (tier in ModelSizeTier.entries) {
            for (quant in ModelQuantization.entries) {
                val preset = ModelPreset.forSizeAndQuant(tier, quant)
                assertEquals(tier, preset.sizeTier)
                assertEquals(quant, preset.quantization)
            }
        }
    }

    @Test
    fun completeCatalogContainsAll24OfficialQwen3TtsGgufModels() {
        assertEquals(24, ModelsCatalog.ALL_MODELS.size)
        assertEquals(24, ModelsCatalog.ALL_MODELS.map { it.fileName }.distinct().size)

        val size06b = ModelsCatalog.ALL_MODELS.filter { it.sizeTier == ModelSizeTier.SIZE_06B }
        val size17b = ModelsCatalog.ALL_MODELS.filter { it.sizeTier == ModelSizeTier.SIZE_17B }
        val codecs = ModelsCatalog.codecModels()

        assertEquals(8, size06b.size)
        assertEquals(12, size17b.size)
        assertEquals(4, codecs.size)

        assertEquals(8, ModelsCatalog.modelsForMode(SynthesisMode.SPEAK).size)
        assertEquals(8, ModelsCatalog.modelsForMode(SynthesisMode.CLONE).size)
        assertEquals(4, ModelsCatalog.modelsForMode(SynthesisMode.DESIGN).size)

        val allCatalogFileNames = ModelsCatalog.ALL_MODELS.map { it.fileName }.toSet()
        for (preset in ModelPreset.entries) {
            assertTrue(preset.allRequiredFiles().all { it in allCatalogFileNames })
        }
    }

    @Test
    fun talkerFileForEachModeMatchesHuggingFaceInventory() {
        val preset06 = ModelPreset.TINY_06B_Q4
        assertEquals(
            "qwen-talker-0.6b-customvoice-Q4_K_M.gguf",
            preset06.talkerFileForMode(SynthesisMode.SPEAK)
        )
        assertEquals(
            "qwen-talker-0.6b-base-Q4_K_M.gguf",
            preset06.talkerFileForMode(SynthesisMode.CLONE)
        )
        assertEquals(
            "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
            preset06.talkerFileForMode(SynthesisMode.DESIGN)
        )

        val preset17 = ModelPreset.FULL_17B_Q8
        assertEquals(
            "qwen-talker-1.7b-customvoice-Q8_0.gguf",
            preset17.talkerFileForMode(SynthesisMode.SPEAK)
        )
        assertEquals(
            "qwen-talker-1.7b-base-Q8_0.gguf",
            preset17.talkerFileForMode(SynthesisMode.CLONE)
        )
        assertEquals(
            "qwen-talker-1.7b-voicedesign-Q8_0.gguf",
            preset17.talkerFileForMode(SynthesisMode.DESIGN)
        )
    }

    @Test
    fun languageIdsMatchCppAbi() {
        assertEquals(2050, TtsLanguage.ENGLISH.languageId)
        assertEquals(2055, TtsLanguage.CHINESE.languageId)
        assertEquals(2058, TtsLanguage.JAPANESE.languageId)
        assertEquals(-1, TtsLanguage.AUTO.languageId)
    }

    @Test
    fun defaultPresetCustomOverridesAndSpeakInstructionGuardMatchCapabilities() {
        val defaults = AppSettings()
        assertEquals(ModelPreset.FAST_06B_Q8, defaults.preset)
        assertEquals(0.40f, defaults.streamChunkSec, 1e-4f)
        assertFalse(defaults.hasCustomModelOverrides)
        assertFalse(defaults.supportsSpeakInstruction)
        assertFalse(defaults.copy(preset = ModelPreset.TINY_06B_Q4).supportsSpeakInstruction)
        assertTrue(defaults.copy(preset = ModelPreset.FULL_17B_Q4).supportsSpeakInstruction)
        assertTrue(defaults.copy(preset = ModelPreset.FULL_17B_Q8).supportsSpeakInstruction)

        val overriddenTo17b = defaults.copy(
            customSpeakModel = "qwen-talker-1.7b-customvoice-Q4_K_M.gguf"
        )
        assertTrue(overriddenTo17b.hasCustomModelOverrides)
        assertTrue(overriddenTo17b.supportsSpeakInstruction)
        assertEquals("1.7B · Q4_K_M", overriddenTo17b.activeModelSummaryChip())

        assertEquals(ModelRole.CODEC, ModelsCatalog.inferRoleForFile("qwen-tokenizer-12hz-Q4_K_M.gguf"))
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
