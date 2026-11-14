package dev.galios.core

/**
 * The owner's tuning switches: boot settings for iOS that GalIOS writes to the stage as `galios-bootargs` and the
 * runtime (GalIOS's 0015) puts in front of its defaults. The runtime accepts only the keys in [KEYS] and only letters,
 * digits and `._,=-`; [allowed] applies the same rule here, so the app never writes a word the runtime would drop.
 * Each switch is reversible: turning it off removes its words, and the next boot is as before.
 */
object Tuning {
    data class Option(val id: String, val words: List<String>, val title: String, val text: String)

    /** The same list as galios_tuning_keys in runtime/patches/0015. */
    val KEYS = setOf(
        "sheng-vsync", "oneios-disable-jobs", "oneios-enable-jobs", "oneios-gpu-read-wait", "oneios-hid-queue-kib",
        "oneios-ubt", "oneios-ubt-at", "oneios-ubt-every", "oneios-ubt-count", "oneios-iouc", "oneios-sysfail",
        "oneios-log-persist", "oneios-bl-mode",
    )
    private val WORD = Regex("""[A-Za-z0-9._,=-]+""")

    val COOL_60HZ = Option(
        "cool-60hz", listOf("sheng-vsync=60"), "60 Hz cool mode",
        "iOS draws 60 frames a second instead of 120: about half the compositor's work, the hottest process in your log. Motion is a little less fluid; the phone stays cooler and steadier.",
    )

    val PAUSE_INDEXING = Option(
        "pause-indexing",
        listOf("oneios-disable-jobs=com.apple.accessibility.axassetsd,com.apple.searchd,com.apple.spotlight.IndexAgent"),
        "Pause search indexing",
        "Stops Spotlight's indexer, which ran a whole core for minutes in your log. Search finds less until you turn it back on.",
    )

    val ALL = listOf(COOL_60HZ, PAUSE_INDEXING)

    fun allowed(word: String): Boolean {
        if (!WORD.matches(word)) return false
        val eq = word.indexOf('=')
        if (eq <= 0 || eq == word.length - 1) return false
        return word.substring(0, eq) in KEYS
    }

    fun isOn(words: Set<String>, o: Option) = o.words.all { it in words }

    fun with(words: Set<String>, o: Option, on: Boolean): Set<String> = if (on) words + o.words else words - o.words.toSet()

    /** The file's text: the words in a stable order, one line; at most 300 bytes, as the runtime takes. */
    fun text(words: Set<String>): String = words.sorted().joinToString(" ")

    const val MAX_BYTES = 300
}
