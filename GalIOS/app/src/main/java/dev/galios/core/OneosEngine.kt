package dev.galios.core

import java.io.File
import java.util.zip.ZipFile

/**
 * The boot engine GalIOS drives: the OneOS app (akshaynexus/OneOS, its iOS 27 edition), installed on the phone.
 * GalIOS ships none of OneOS's files. It reads the root script and the session scripts from OneOS's own APK, the
 * way OneOS stages them, so the engine is always the one the user installed.
 */
class OneosEngine(val apk: File, val versionName: String, val versionCode: Long) {

    fun read(asset: String): ByteArray = ZipFile(apk).use { zip ->
        val entry = zip.getEntry("assets/$asset") ?: throw MissingEngineFile(asset)
        zip.getInputStream(entry).use { it.readBytes() }
    }

    /** True when this OneOS build carries everything the iOS boot needs. */
    fun hasIos(): Boolean = ZipFile(apk).use { zip -> REQUIRED.all { zip.getEntry("assets/$it") != null } }

    class MissingEngineFile(asset: String) :
        Exception("This OneOS build has no $asset: install OneOS's iOS 27 edition (akshaynexus/OneOS, oneios releases)")

    companion object {
        const val PACKAGE = "com.fahadali.oneos"
        const val ROOT_SCRIPT = "root/oneios_root.sh"
        const val SWITCH = "oneios/oneios_switch.sh"
        const val RETURN = "oneios/oneios_return.sh"

        /** The boot screen OneOS shows from One UI's stop until iOS draws: staged into the stage's bin/. */
        val BOOT_SCREEN = listOf("oneios/boot_status.sh", "oneios/OneOSBootStatus.dex", "oneios/oneos_boot_icon.png")
        val REQUIRED = listOf(ROOT_SCRIPT, SWITCH, RETURN) + BOOT_SCREEN

        const val RELEASES_URL = "https://github.com/akshaynexus/OneOS/releases"
    }
}

/**
 * OneOS's switch script lets iOS start on the Galaxy S24 Ultra only: its phone check names SM-S928, e3q and SM8650.
 * GalIOS adapts that one check, on the phone, before staging the script: the S24 Ultra stays accepted as before and
 * the S25 Ultra (SM-S938, pa3q, SM8750) is accepted beside it, each against its own codename and SoC. Nothing else in
 * the script changes. If the check is not where GalIOS expects it (a newer OneOS), GalIOS refuses instead of guessing.
 */
object SwitchScriptAdapter {
    sealed class Result {
        data class Adapted(val script: ByteArray) : Result()
        data class AlreadySupports(val script: ByteArray) : Result()
        data class Unrecognised(val why: String) : Result()
    }

    private val GALIOS_CHECK = listOf(
        "# GalIOS: this phone check accepts the Galaxy S25 Ultra beside the S24 Ultra.",
        "case \"\$P_MODEL\" in",
        "    SM-S928*) [ \"\$P_DEVICE\" = e3q ] && [ \"\$P_SOC\" = SM8650 ] ||",
        "        refuse \"iOS on the Galaxy S24 Ultra needs e3q, SM8650; this phone is \${P_DEVICE:-?}, \${P_SOC:-?}\" ;;",
        "    SM-S938*) [ \"\$P_DEVICE\" = pa3q ] && [ \"\$P_SOC\" = SM8750 ] ||",
        "        refuse \"iOS on the Galaxy S25 Ultra needs pa3q, SM8750; this phone is \${P_DEVICE:-?}, \${P_SOC:-?}\" ;;",
        "    *) refuse \"iOS runs on the Galaxy S24 Ultra and S25 Ultra only; this phone is \${P_MODEL:-unknown}\" ;;",
        "esac",
    )

    /** The S25 Ultra's changes: the phone check (adaptPhoneCheck), then the read-only step (adaptReadOnly). */
    fun adapt(script: ByteArray): Result {
        val phone = adaptPhoneCheck(script)
        val base = when (phone) {
            is Result.Unrecognised -> return phone
            is Result.Adapted -> phone.script
            is Result.AlreadySupports -> phone.script
        }
        val ro = adaptReadOnly(base)
        return when {
            ro != null -> Result.Adapted(ro)
            phone is Result.Adapted -> phone
            else -> Result.AlreadySupports(base)
        }
    }

    const val RO_MARKER = "# GalIOS (S25 Ultra): busybox's read-only bind, then Android's own mount;"

    private val RO_BLOCK = listOf(
        RO_MARKER,
        "# a script file that still cannot be made read-only here is left to root alone (0500) with a warning",
        "# instead of ending the session (the S25 Ultra's kernel refused it: status 70). Folders stay strict.",
        "if \"\$BB\" mount --bind \"/data/\$d\" \"/data/\$d\" 2>&1; then",
        "    \"\$BB\" mount -o remount,bind,ro \"/data/\$d\" 2>&1 || /system/bin/mount -o remount,ro,bind \"/data/\$d\" 2>&1 || {",
        "        [ -f \"/data/\$d\" ] || die_inner \"could not make /data/\$d read-only\"",
        "        chmod 0500 \"/data/\$d\"",
        "        echo \"oneios inner: \$(date +%T) warning: /data/\$d could not be made read-only here; kept root's alone\"",
        "    }",
        "else",
        "    [ -f \"/data/\$d\" ] || die_inner \"could not make /data/\$d read-only\"",
        "    chmod 0500 \"/data/\$d\"",
        "    echo \"oneios inner: \$(date +%T) warning: /data/\$d could not be bound here; kept root's alone\"",
        "fi",
    )

    /**
     * OneOS's two-line read-only step (bind, remount read-only, else end the session) with a fallback for the two script
     * files, which the S25 Ultra's kernel would not bind read-only. Null when the step is not there or already adapted.
     */
    fun adaptReadOnly(script: ByteArray): ByteArray? {
        val text = script.toString(Charsets.UTF_8)
        if (RO_MARKER in text) return null
        val lines = text.split('\n')
        val i = (0 until lines.size - 1).firstOrNull {
            lines[it].trim() == "\"\$BB\" mount --bind \"/data/\$d\" \"/data/\$d\" && \"\$BB\" mount -o remount,bind,ro \"/data/\$d\" ||" &&
                lines[it + 1].trim() == "die_inner \"could not make /data/\$d read-only\""
        } ?: return null
        val indent = lines[i].takeWhile { it == ' ' || it == '\t' }
        val out = lines.subList(0, i) + RO_BLOCK.map { indent + it } + lines.subList(i + 2, lines.size)
        return out.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    private fun adaptPhoneCheck(script: ByteArray): Result {
        val text = script.toString(Charsets.UTF_8)
        if (Regex("""^\s*SM-S938\*\)""", RegexOption.MULTILINE).containsMatchIn(text)) return Result.AlreadySupports(script)
        val lines = text.split('\n')
        // The model line: a one-line case on $P_MODEL whose only accepted pattern is SM-S928*.
        val model = lines.indexOfFirst { l ->
            val t = l.trim()
            t.startsWith("case \"\$P_MODEL\" in SM-S928*)") && t.endsWith("esac") && "refuse" in t
        }
        if (model < 0) return Result.Unrecognised("the switch script's phone check is not the one GalIOS knows (no SM-S928 model line)")
        // Then the codename and SoC test, and its refuse line.
        val soc = model + 1
        val refuse = model + 2
        val socOk = lines.getOrNull(soc)?.trim()?.let { it.startsWith("[ \"\$P_DEVICE\" = e3q ]") && "SM8650" in it && it.endsWith("||") } == true
        val refuseOk = lines.getOrNull(refuse)?.trim()?.startsWith("refuse ") == true
        if (!socOk || !refuseOk) return Result.Unrecognised("the switch script's codename and SoC check is not the one GalIOS knows")
        // Another S24-only test in the code (not a comment) would still refuse the S25 Ultra: say so rather than
        // stage half a fix.
        val elsewhere = lines.withIndex().firstOrNull { (i, l) ->
            i !in model..refuse && !l.trimStart().startsWith("#") && listOf("SM-S928", "\"e3q\"", "= e3q", "SM8650").any { it in l }
        }
        if (elsewhere != null) {
            return Result.Unrecognised("the switch script also checks for the S24 Ultra at its line ${elsewhere.index + 1}")
        }
        val indent = lines[model].takeWhile { it == ' ' || it == '\t' }
        val out = lines.subList(0, model) + GALIOS_CHECK.map { indent + it } + lines.subList(refuse + 1, lines.size)
        return Result.Adapted(out.joinToString("\n").toByteArray(Charsets.UTF_8))
    }
}
