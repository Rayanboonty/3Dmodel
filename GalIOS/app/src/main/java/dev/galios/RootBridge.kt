package dev.galios

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * GalIOS Studio's way to root when the phone's `su` does not answer over USB (a root app that grants apps but not
 * adb's shell). Studio, as adb's shell user, writes a script to [DIR]/<id>.sh and calls
 * `content call --uri content://dev.galios.bridge --method run --arg <id>`. GalIOS runs that script with its own
 * root (`su -c`), detached, so it outlives GalIOS: its output goes to <id>.out and its exit code to <id>.rc.
 *
 * Only adb's shell (uid 2000) and root may call it, and the script must lie in [DIR], which only the shell user and
 * root can write to. `ping` checks that GalIOS gets root at all.
 */
class RootBridge : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val out = Bundle()
        val caller = Binder.getCallingUid()
        if (caller != SHELL_UID && caller != Process.ROOT_UID) {
            out.putInt("rc", 126)
            out.putString("msg", "refused: only adb's shell may use the GalIOS bridge")
            return out
        }
        when (method) {
            "ping" -> {
                val r = runSu("id -u", 60)
                out.putInt("rc", if (r.first == 0 && r.second.trim() == "0") 0 else 1)
                out.putString("msg", if (r.first == 0) "uid=" + r.second.trim() else "su: " + r.second.trim().take(300))
            }
            "run" -> {
                val id = arg.orEmpty()
                if (!ID.matches(id)) {
                    out.putInt("rc", 2); out.putString("msg", "bad id"); return out
                }
                // Detached (setsid, its own session): it keeps running if Android stops GalIOS.
                val cmd = "cd $DIR && [ -f $id.sh ] && { setsid sh -c 'sh $id.sh > $id.out 2>&1 < /dev/null; " +
                    "echo \$? > $id.rc.tmp; chmod 0644 $id.out $id.rc.tmp; mv -f $id.rc.tmp $id.rc' " +
                    "< /dev/null > /dev/null 2>&1 & } && echo started"
                val r = runSu(cmd, 60)
                val started = r.first == 0 && r.second.contains("started")
                out.putInt("rc", if (started) 0 else 1)
                out.putString("msg", if (started) "started" else "su: " + r.second.trim().take(300))
            }
            else -> {
                out.putInt("rc", 2); out.putString("msg", "unknown method")
            }
        }
        return out
    }

    private fun runSu(command: String, seconds: Long): Pair<Int, String> = try {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        p.outputStream.close()
        val text = StringBuilder()
        val reader = Thread { text.append(p.inputStream.bufferedReader().readText()) }.apply { start() }
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            Pair(124, "no answer from su in $seconds s (allow GalIOS in your root app)")
        } else {
            reader.join(2000)
            Pair(p.exitValue(), synchronized(text) { text.toString() })
        }
    } catch (e: Exception) {
        Pair(127, e.message ?: e.toString())
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        const val DIR = "/data/local/tmp/galios-bridge"
        const val SHELL_UID = 2000
        val ID = Regex("[a-z0-9-]{1,40}")
    }
}
