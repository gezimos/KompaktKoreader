// Kompakt: the volume keys turn the page while the phone is locked.

package org.koreader.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import java.io.File

/**
 * On a Mudita Kompakt running KompaktOS, a book left on the e-ink screen when the phone locks
 * stays there. While it does, the system sends each volume key press here instead of waking
 * the phone. Lua turns the page as that key would, draws it off screen, and the page goes back
 * to the system, which paints it on the sleeping screen. Only the system may send the key
 * (DEVICE_POWER); the system decides when, so nothing here runs on other phones.
 */
class KompaktReader : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = when (intent.getIntExtra(EXTRA_KEYCODE, 0)) {
            KeyEvent.KEYCODE_VOLUME_UP -> CMD_VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> CMD_VOLUME_DOWN
            else -> return
        }
        val rc = try {
            nativeSendCommand(cmd)
        } catch (t: Throwable) {
            Log.e(TAG, "reader not running: $t")
            return
        }
        if (rc != 0) Log.e(TAG, "cannot reach the reader ($rc)")
    }

    companion object {
        private const val TAG = "KompaktReader"

        const val EXTRA_KEYCODE = "keycode"

        // Read by Lua from the command pipe (frontend/device/android/device.lua).
        private const val CMD_VOLUME_UP = 120
        private const val CMD_VOLUME_DOWN = 121

        private val PAGE: Uri = Uri.parse("content://com.kompakt.sleepscreen/reader_page")

        /** The display's rotation while the reader was last on screen: which way up the page goes. */
        @Volatile
        private var rotation = 0

        @JvmStatic
        fun noteRotation(activity: Activity) {
            @Suppress("DEPRECATION")
            rotation = activity.windowManager.defaultDisplay.rotation
        }

        /** Lua: the page drawn while locked, a PNG file, to the system; the file is deleted. 0 if sent. */
        @JvmStatic
        fun sendPage(context: Context, path: String): Int {
            val file = File(path)
            return try {
                val uri = PAGE.buildUpon()
                    .appendQueryParameter("rotation", rotation.toString())
                    .build()
                val out = context.contentResolver.openOutputStream(uri, "w") ?: return -1
                out.use { o -> file.inputStream().use { it.copyTo(o) } }
                0
            } catch (t: Throwable) {
                Log.e(TAG, "cannot hand the page over: $t")
                -1
            } finally {
                file.delete()
            }
        }

        @JvmStatic
        private external fun nativeSendCommand(cmd: Int): Int
    }
}
