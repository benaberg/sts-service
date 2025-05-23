package fi.benaberg.sts.service.handlers

import fi.benaberg.sts.service.util.LogUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*

/**
 * Handles storing temperature readings to disk.
 */
class StorageHandler(private val dataDirPath: Path) {

    companion object {
        const val READING_FILE = "stored_reading.json"
    }

    @Throws(IOException::class, SecurityException::class)
    fun storeData(jsonObject: JSONObject) {
        LogUtil.write("Storing received temperature...")

        // Resolve file
        val file = dataDirPath.toFile().resolve(READING_FILE)

        // Verify that parent directories exist
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        // Store temperature
        file.writeText(jsonObject.toString(), StandardCharsets.UTF_8)
        LogUtil.write("Successfully stored temperature data: $jsonObject ")
    }

    @Throws(JSONException::class)
    fun readData(): JSONObject? {
        LogUtil.write("Reading stored temperature...")

        // Resolve file
        val file = dataDirPath.toFile().resolve(READING_FILE)

        // Return null if no temperature reading exists
        if (!file.exists()) {
            LogUtil.write("No stored temperature found.")
            return null
        }

        // Read file contents
        val jsonObject = JSONObject(file.readText())
        LogUtil.write("Successfully read stored temperature!")

        return jsonObject
    }
}