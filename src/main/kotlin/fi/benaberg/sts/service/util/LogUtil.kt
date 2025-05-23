package fi.benaberg.sts.service.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles logging. Currently, the implementation only prints everything to console.
 */
class LogUtil {

    companion object {

        private val sdf = SimpleDateFormat("dd-MM-yyy HH:mm:ss.SSS ")

        private fun getFormattedTime(millis: Long):String {
            return sdf.format(Date(millis))
        }

        fun write(message: String) {
            println(getFormattedTime(System.currentTimeMillis()) + message)
        }
    }
}