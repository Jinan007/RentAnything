package com.clippost.app.captions

import com.clippost.app.MAX_TITLE

/** The text the automatic mode writes for one clip. */
data class Metadata(
    val title: String,
    val caption: String,
    val tags: List<String>,
    val comment: String
)

/**
 * Writes a title, caption, tags and a comment on the phone, from the speech and the file name. No AI.
 * It is simple, so the wording is plain. Anything you type yourself is kept instead.
 */
object TemplateWriter {

    private val STOPWORDS = setOf(
        "that", "this", "with", "have", "just", "like", "what", "when", "they", "them", "there", "then",
        "than", "from", "your", "yours", "about", "would", "could", "should", "really", "gonna", "going",
        "right", "know", "think", "okay", "well", "make", "want", "into", "over", "here", "were", "been",
        "being", "little", "nice", "good", "look", "looks", "come", "coming", "thing", "things", "time",
        "still", "much", "some", "more", "very", "also", "only", "even", "back", "down", "guys", "dude",
        "bro", "yeah", "oops", "wait", "bruh", "damn", "shit", "fuck", "gotta", "ahead"
    )

    /** Words that come from phone file names, not from the video. */
    private val NOISE = setOf("vid", "img", "screenshot", "recording", "mov", "pxl", "dsc", "video", "clip", "copy")

    /** Game names we recognise (lowercase word -> name shown to viewers). Checked in this order. */
    private val GAMES = linkedMapOf(
        "gta6" to "GTA 6", "gta5" to "GTA 5", "gta" to "GTA",
        "minecraft" to "Minecraft", "fortnite" to "Fortnite", "valorant" to "Valorant",
        "roblox" to "Roblox", "warzone" to "Call of Duty", "fifa" to "EA FC",
        "rocket" to "Rocket League", "apex" to "Apex Legends"
    )

    private val COMMENTS = listOf(
        "What would you have done here? Tell me in the comments.",
        "Did this ever happen to you? Let me know below.",
        "Rate this from 1 to 10 in the comments!"
    )

    /**
     * @param fileName the clip's file name, e.g. "VID_20261119.mp4" or "gta_train_stop.mp4"
     * @param words every spoken word with its time (may be empty if there was no speech)
     */
    fun write(fileName: String, words: List<Word>): Metadata {
        val nameTokens = tokens(fileName.substringBeforeLast('.'))
        val spokenTokens = words.flatMap { tokens(it.text) }
        val game = GAMES.entries.firstOrNull { it.key in nameTokens || it.key in spokenTokens }?.value
        val keywords = topKeywords(spokenTokens, 3)

        val base = if (keywords.isNotEmpty()) {
            keywords.joinToString(" ") { cap(it) }
        } else {
            cleanName(nameTokens).ifBlank { "New clip" }
        }
        val title = listOfNotNull(base, game).joinToString(" | ").take(MAX_TITLE).trim()

        val snippet = words.take(20).joinToString(" ") { it.text }.trim()
        val lead = if (snippet.isEmpty()) "A quick clip from my channel." else sentence(cap(snippet))
        val follow = if (game != null) {
            "More $game clips on my channel. Subscribe so you don't miss them."
        } else {
            "More clips on my channel. Subscribe so you don't miss them."
        }

        val tags = (listOfNotNull(game) + keywords + listOf("shorts", "gaming"))
            .map { it.lowercase() }
            .distinct()
            .take(10)

        // The same title always gets the same comment, so re-running an upload gives the same text.
        val comment = COMMENTS[(title.hashCode() and Int.MAX_VALUE) % COMMENTS.size]

        return Metadata(title = title, caption = "$lead\n\n$follow", tags = tags, comment = comment)
    }

    /** Lowercase letter and digit pieces of some text. */
    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** The most repeated meaningful words, most common first (ties keep the order they were said in). */
    private fun topKeywords(tokens: List<String>, count: Int): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (token in tokens) {
            val usable = token.length >= 4 && token !in STOPWORDS && token !in NOISE &&
                token.any { it.isLetter() }
            if (usable) counts[token] = (counts[token] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.take(count).map { it.key }
    }

    /** A file name with the phone's junk removed, e.g. "VID_20261119_gta train stop" -> "Gta Train Stop". */
    private fun cleanName(tokens: List<String>): String =
        tokens.filter { it !in NOISE && !it.all(Char::isDigit) }.joinToString(" ") { cap(it) }

    private fun cap(text: String): String = text.replaceFirstChar { it.uppercaseChar() }

    private fun sentence(text: String): String = if (text.last() in ".!?") text else "$text."
}
