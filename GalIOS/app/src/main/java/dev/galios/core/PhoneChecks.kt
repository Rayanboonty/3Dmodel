package dev.galios.core

/** The phone, by the three ids OneOS's switch checks. */
data class Device(val model: String, val device: String, val soc: String) {
    val isS25Ultra get() = model.startsWith("SM-S938") && device == "pa3q" && soc.equals("SM8750", ignoreCase = true)
    val isS24Ultra get() = model.startsWith("SM-S928") && device == "e3q" && soc.equals("SM8650", ignoreCase = true)
    val supported get() = isS25Ultra || isS24Ultra
    val label: String
        get() = when {
            isS25Ultra -> "Galaxy S25 Ultra"
            isS24Ultra -> "Galaxy S24 Ultra"
            else -> model
        }
    val chip: String
        get() = when {
            isS25Ultra -> "Snapdragon 8 Elite · Adreno 830"
            isS24Ultra -> "Snapdragon 8 Gen 3 · Adreno 750"
            else -> soc
        }
}

/** What root sees: the things the switch needs that only root can look at. Read only. */
data class PhoneChecks(
    val root: Boolean,
    val busybox: String?,
    val resetprop: String?,
    val volumeUp: Boolean,
    val volumeDown: Boolean,
    val freeBytes: Long?,
    val gpu: String?,
) {
    val allGood get() = root && busybox != null && resetprop != null && volumeUp && volumeDown

    companion object {
        val NONE = PhoneChecks(false, null, null, false, false, null, null)

        const val SCRIPT = """
echo "uid=${'$'}(id -u)"
for c in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do [ -x "${'$'}c" ] && { echo "busybox=${'$'}c"; break; }; done
for c in /data/adb/ksu/bin/resetprop /data/adb/ap/bin/resetprop /debug_ramdisk/resetprop /sbin/resetprop; do [ -x "${'$'}c" ] && { echo "resetprop=${'$'}c"; break; }; done
k=${'$'}(getevent -pl 2>/dev/null)
case "${'$'}k" in *KEY_VOLUMEUP*) echo "volup=1" ;; esac
case "${'$'}k" in *KEY_VOLUMEDOWN*) echo "voldown=1" ;; esac
echo "free=${'$'}(stat -f -c '%a %S' /data 2>/dev/null)"
echo "gpu=${'$'}(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null)"
"""

        fun run(shell: RootShell): PhoneChecks = try {
            parse(shell.exec(SCRIPT.trim()).lines)
        } catch (_: Exception) {
            NONE
        }

        fun parse(lines: List<String>): PhoneChecks {
            val kv = lines.mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1).trim() } }.toMap()
            val free = kv["free"]?.split(' ')?.takeIf { it.size == 2 }?.let { (a, s) -> a.toLongOrNull()?.times(s.toLongOrNull() ?: 0) }
            return PhoneChecks(
                root = kv["uid"] == "0",
                busybox = kv["busybox"]?.ifBlank { null },
                resetprop = kv["resetprop"]?.ifBlank { null },
                volumeUp = kv["volup"] == "1",
                volumeDown = kv["voldown"] == "1",
                freeBytes = free,
                gpu = kv["gpu"]?.ifBlank { null },
            )
        }
    }
}

/**
 * Puts OneOS's boot engine in the stage: the root script, then the session scripts (the switch's phone check
 * adapted for the S25 Ultra) and the boot screen. Run again before every boot, so a script staged by another app
 * since (OneOS's own S24-only one) is replaced.
 */
object EngineSetup {
    data class Done(val switchAdapted: Boolean)

    fun install(engine: OneosEngine, stage: StageClient): Done {
        stage.installRootScript(engine.read(OneosEngine.ROOT_SCRIPT)).orThrow()
        return stageScripts(engine, stage)
    }

    fun stageScripts(engine: OneosEngine, stage: StageClient): Done {
        val adapted = when (val r = SwitchScriptAdapter.adapt(engine.read(OneosEngine.SWITCH))) {
            is SwitchScriptAdapter.Result.Adapted -> r.script to true
            is SwitchScriptAdapter.Result.AlreadySupports -> r.script to false
            is SwitchScriptAdapter.Result.Unrecognised ->
                throw ImportException("OneOS ${engine.versionName}: ${r.why}. GalIOS knows the OneOS iOS edition of November 2026.")
        }
        stage.stageScript("oneios_switch.sh", adapted.first).orThrow()
        stage.stageScript("oneios_return.sh", engine.read(OneosEngine.RETURN)).orThrow()
        for (asset in OneosEngine.BOOT_SCREEN) stage.stageScript(asset.substringAfterLast('/'), engine.read(asset)).orThrow()
        return Done(adapted.second)
    }
}
