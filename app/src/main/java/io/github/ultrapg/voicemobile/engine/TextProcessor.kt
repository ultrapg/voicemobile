package io.github.ultrapg.voicemobile.engine

/**
 * 1:1 Kotlin port of voicecli's Markdown sanitizer and linguistic sentence/clause chunker
 * (abbreviation-aware, decimal-aware, ellipsis-aware, CJK punctuation-aware).
 */
object TextProcessor {

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "ave", "blvd",
        "vs", "etc", "e.g", "i.e", "a.m", "p.m", "u.s", "u.k", "ph.d", "m.d",
        "fig", "inc", "ltd", "co", "corp",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec", "no", "vol", "pp", "ed", "al", "gen", "col"
    )

    private val FENCED_CODE_REGEX = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val IMAGE_LINK_REGEX = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val MARKDOWN_LINK_REGEX = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val HEADER_REGEX = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
    private val BLOCKQUOTE_REGEX = Regex("(?m)^\\s{0,3}>+\\s?")
    private val LIST_MARKER_REGEX = Regex("(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+")
    private val HR_REGEX = Regex("(?m)^\\s*(?:[-*_]\\s*){3,}$")
    private val BOLD_ITALIC_REGEX = Regex("(\\*\\*\\*|___|~~|\\*\\*|__|\\*)([^*_~]+?)\\1")
    private val MULTI_SPACE_REGEX = Regex("[ \\t]{2,}")
    private val MULTI_NEWLINE_REGEX = Regex("\\n{3,}")

    fun cleanMarkdown(input: String): String {
        if (input.isBlank()) return ""
        var s = input
        // Remove fenced code blocks cleanly
        s = FENCED_CODE_REGEX.replace(s, " ")
        // Keep alt text of images and visible text of links
        s = IMAGE_LINK_REGEX.replace(s) { it.groupValues[1] }
        s = MARKDOWN_LINK_REGEX.replace(s) { it.groupValues[1] }
        // Keep inline code text without backticks
        s = INLINE_CODE_REGEX.replace(s) { it.groupValues[1] }
        // Strip HTML tags & horizontal rules
        s = HTML_TAG_REGEX.replace(s, " ")
        s = HR_REGEX.replace(s, "\n")
        // Strip ATX headers, blockquotes, and list markers
        s = HEADER_REGEX.replace(s, "")
        s = BLOCKQUOTE_REGEX.replace(s, "")
        s = LIST_MARKER_REGEX.replace(s, "")
        // Strip nested emphasis markers (run twice for nested **bold _italic_**)
        repeat(2) {
            s = BOLD_ITALIC_REGEX.replace(s) { it.groupValues[2] }
        }
        // Clean stray standalone asterisks/underscores around words
        s = s.replace("**", "").replace("__", "").replace("~~", "")
        s = MULTI_SPACE_REGEX.replace(s, " ")
        s = MULTI_NEWLINE_REGEX.replace(s, "\n\n")
        return s.lines().joinToString("\n") { it.trim() }.trim()
    }

    fun prepareChunks(
        rawText: String,
        cleanMarkdownEnabled: Boolean,
        chunkMode: ChunkMode,
        maxChunkChars: Int = 600,
        autoChunkThreshold: Int = 800
    ): List<String> {
        val text = if (cleanMarkdownEnabled) cleanMarkdown(rawText) else rawText.trim()
        if (text.isBlank()) return emptyList()

        val safeLimit = maxChunkChars.coerceIn(120, 2000)
        val shouldChunk = when (chunkMode) {
            ChunkMode.FORCE_OFF -> false
            ChunkMode.FORCE_ON -> text.length > safeLimit
            ChunkMode.AUTO -> text.length > autoChunkThreshold
        }

        if (!shouldChunk) {
            return listOf(text)
        }
        return splitIntoChunks(text, safeLimit)
    }

    fun splitIntoChunks(text: String, maxChars: Int): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChars) return listOf(normalized)

        val sentences = splitIntoSentences(normalized)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flushCurrent() {
            val built = current.toString().trim()
            if (built.isNotEmpty()) {
                chunks += built
            }
            current.setLength(0)
        }

        for (sentence in sentences) {
            val s = sentence.trim()
            if (s.isEmpty()) continue

            if (s.length > maxChars) {
                flushCurrent()
                chunks += splitLongSentenceByClauses(s, maxChars)
                continue
            }

            if (current.isEmpty()) {
                current.append(s)
            } else if (current.length + 1 + s.length <= maxChars) {
                current.append(' ').append(s)
            } else {
                flushCurrent()
                current.append(s)
            }
        }
        flushCurrent()
        return chunks
    }

    internal fun splitIntoSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        val len = text.length
        var i = 0

        while (i < len) {
            val ch = text[i]

            // Paragraph break acts as natural sentence boundary
            if (ch == '\n' && i + 1 < len && text[i + 1] == '\n') {
                val seg = buf.toString().trim()
                if (seg.isNotEmpty()) out += seg
                buf.setLength(0)
                while (i < len && text[i].isWhitespace()) i++
                continue
            }

            buf.append(if (ch == '\n') ' ' else ch)

            if (isSentenceTerminator(text, i)) {
                // Consume trailing closing quotes/brackets or multi-punctuation (e.g. ?!, ...", ”)
                while (i + 1 < len && isTrailingPunctOrQuote(text[i + 1])) {
                    i++
                    buf.append(text[i])
                }
                val seg = buf.toString().trim()
                if (seg.isNotEmpty()) out += seg
                buf.setLength(0)
                while (i + 1 < len && text[i + 1].isWhitespace()) {
                    i++
                }
            }
            i++
        }

        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) out += tail
        return out
    }

    private fun isSentenceTerminator(text: String, idx: Int): Boolean {
        val ch = text[idx]
        if (ch == '。' || ch == '！' || ch == '？' || ch == '；') {
            return true
        }
        if (ch != '.' && ch != '!' && ch != '?') {
            return false
        }

        val len = text.length
        if (ch == '.') {
            // 1. Decimal numbers like 3.14 or $9.99
            if (idx > 0 && idx + 1 < len && text[idx - 1].isDigit() && text[idx + 1].isDigit()) {
                return false
            }
            // 2. Ellipsis (...) — only break on the final dot if followed by whitespace + uppercase/end
            if (idx + 1 < len && text[idx + 1] == '.') {
                return false
            }
            // 3. Common abbreviations (Mr., Dr., e.g., i.e., vs.) & single initials (J. R. R.)
            val wordBefore = extractWordBeforeDot(text, idx)
            if (wordBefore.isNotEmpty()) {
                val lower = wordBefore.lowercase()
                if (lower in ABBREVIATIONS) return false
                if (wordBefore.length == 1 && wordBefore[0].isUpperCase()) return false
            }
        }

        // Must be at end of text or followed by quote/whitespace
        if (idx + 1 >= len) return true
        val next = text[idx + 1]
        return next.isWhitespace() || isTrailingPunctOrQuote(next)
    }

    private fun extractWordBeforeDot(text: String, dotIdx: Int): String {
        var start = dotIdx - 1
        while (start >= 0 && (text[start].isLetter() || text[start] == '.')) {
            start--
        }
        return text.substring(start + 1, dotIdx).trim('.')
    }

    private fun isTrailingPunctOrQuote(c: Char): Boolean =
        c == '"' || c == '\'' || c == '”' || c == '’' || c == ')' || c == ']' || c == '!' || c == '?'

    private fun splitLongSentenceByClauses(sentence: String, maxChars: Int): List<String> {
        val parts = mutableListOf<String>()
        val clauseDelimiters = setOf(',', ';', ':', '—', '–', '，', '、')

        var remaining = sentence.trim()
        while (remaining.length > maxChars) {
            var splitIdx = -1
            val searchLimit = maxChars.coerceAtMost(remaining.length - 1)

            // Prefer splitting right after a clause delimiter in the second half of maxChars
            for (i in searchLimit downTo (maxChars / 3)) {
                if (remaining[i] in clauseDelimiters) {
                    splitIdx = i + 1
                    break
                }
            }
            // Fallback: split on whitespace
            if (splitIdx == -1) {
                for (i in searchLimit downTo (maxChars / 4)) {
                    if (remaining[i].isWhitespace()) {
                        splitIdx = i
                        break
                    }
                }
            }
            // Hard fallback for unbroken CJK / long tokens
            if (splitIdx <= 0) {
                splitIdx = maxChars
            }

            val head = remaining.substring(0, splitIdx).trim()
            if (head.isNotEmpty()) parts += head
            remaining = remaining.substring(splitIdx).trim()
        }
        if (remaining.isNotEmpty()) parts += remaining
        return parts
    }
}
