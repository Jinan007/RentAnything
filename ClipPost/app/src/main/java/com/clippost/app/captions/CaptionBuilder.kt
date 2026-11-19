package com.clippost.app.captions

/** A few words shown together on screen. visibleUntilMs is when this caption disappears. */
data class CaptionLine(val words: List<Word>, val visibleUntilMs: Long) {
    val startMs: Long get() = words.first().startMs
}

/** Groups the recognized words into short, readable captions. The same rules apply to every video. */
object CaptionBuilder {

    private const val MAX_WORDS = 4        // at most this many words per caption
    private const val MAX_CHARS = 24       // ...and at most this many letters, so it fits a phone screen
    private const val MAX_GAP_MS = 600L    // a pause longer than this starts a new caption
    private const val HOLD_MS = 250L       // keep a caption visible a moment after the last word

    fun group(words: List<Word>): List<CaptionLine> {
        val groups = mutableListOf<List<Word>>()
        var current = mutableListOf<Word>()

        for (word in words) {
            val last = current.lastOrNull()
            if (last != null) {
                val letters = current.sumOf { it.text.length + 1 } + word.text.length
                val tooLong = current.size >= MAX_WORDS || letters > MAX_CHARS
                val longPause = word.startMs - last.endMs > MAX_GAP_MS
                if (tooLong || longPause) {
                    groups.add(current)
                    current = mutableListOf()
                }
            }
            current.add(word)
        }
        if (current.isNotEmpty()) groups.add(current)

        return groups.mapIndexed { index, group ->
            val nextStart = groups.getOrNull(index + 1)?.first()?.startMs ?: Long.MAX_VALUE
            CaptionLine(group, minOf(group.last().endMs + HOLD_MS, nextStart))
        }
    }
}
