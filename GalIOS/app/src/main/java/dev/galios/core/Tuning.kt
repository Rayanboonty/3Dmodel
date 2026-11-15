package dev.galios.core

/**
 * The owner's tuning switches: boot settings for iOS that GalIOS writes to the stage as `galios-bootargs` and the
 * runtime (GalIOS's 0015) puts in front of its defaults. The runtime accepts only the keys in [KEYS] and only letters,
 * digits and `._,=-`; [allowed] applies the same rule here, so the app never writes a word the runtime would drop.
 * Each switch is reversible: turning it off removes its words and adds its [Option.undo] words, and the next boot is
 * as before.
 */
object Tuning {
    /**
     * A switch. [words] go in while it is on. [undo] goes in while it is off, for settings iOS keeps by itself once
     * given (the disabled jobs are written to iOS's own disabled.plist: only oneios-enable-jobs brings them back).
     */
    data class Option(val id: String, val words: List<String>, val title: String, val text: String, val undo: List<String> = emptyList())

    /** The same list as galios_tuning_keys in runtime/patches/0015 (and 0023's ca-triple-buffer). */
    val KEYS = setOf(
        "sheng-vsync", "oneios-disable-jobs", "oneios-enable-jobs", "oneios-gpu-read-wait", "oneios-hid-queue-kib",
        "oneios-ubt", "oneios-ubt-at", "oneios-ubt-every", "oneios-ubt-count", "oneios-iouc", "oneios-sysfail",
        "oneios-log-persist", "oneios-bl-mode", "ca-triple-buffer",
    )
    private val WORD = Regex("""[A-Za-z0-9._,=-]+""")

    val COOL_60HZ = Option(
        "cool-60hz", listOf("sheng-vsync=60"), "60 Hz cool mode",
        "iOS draws 60 frames a second instead of 120: about half the compositor's work, the hottest process in your log. Motion is a little less fluid; the phone stays cooler and steadier.",
    )

    /** The background services a phone that is not an iPhone gains nothing from, and which kept cores busy in the logs. */
    private const val LIGHT_JOBS = "com.apple.searchd,com.apple.spotlight.IndexAgent,com.apple.photoanalysisd,com.apple.mediaanalysisd,com.apple.analyticsd"

    val PAUSE_INDEXING = Option(
        "pause-indexing",
        listOf("oneios-disable-jobs=com.apple.accessibility.axassetsd,$LIGHT_JOBS"),
        "Lighter iOS",
        "Stops the background services that kept cores busy and the phone hot in your logs: Spotlight's indexer, photo and media analysis, and analytics. Smoother and cooler; search finds less and Photos' memories wait until you turn it off.",
        undo = listOf("oneios-enable-jobs=$LIGHT_JOBS"),
    )

    val TRIPLE_BUFFER_OFF = Option(
        "triple-buffer-off", listOf("ca-triple-buffer=0"), "Triple buffering off",
        "The newest runtime draws one frame ahead (triple buffering, as Droid-VM does), which made animations smoother there. Turn this on only if motion looks delayed or uneven.",
    )

    val ALL = listOf(COOL_60HZ, PAUSE_INDEXING, TRIPLE_BUFFER_OFF)

    fun allowed(word: String): Boolean {
        if (!WORD.matches(word)) return false
        val eq = word.indexOf('=')
        if (eq <= 0 || eq == word.length - 1) return false
        return word.substring(0, eq) in KEYS
    }

    fun isOn(words: Set<String>, o: Option) = o.words.all { it in words }

    private fun key(word: String) = word.substringBefore('=')

    /**
     * The words with [o] turned [on] or off. Every word with one of the option's keys goes first, so an older version
     * of the same switch (another list of jobs, say) never stays behind: the runtime takes the first of a key it finds.
     */
    fun with(words: Set<String>, o: Option, on: Boolean): Set<String> {
        val keys = (o.words + o.undo).map(::key).toSet()
        val rest = words.filterNot { key(it) in keys }.toSet()
        return if (on) rest + o.words else rest + o.undo
    }

    /** The file's text: the words in a stable order, one line; at most 300 bytes, as the runtime takes. */
    fun text(words: Set<String>): String = words.sorted().joinToString(" ")

    const val MAX_BYTES = 300
}
