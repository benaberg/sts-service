package fi.benaberg.sts.service.handlers

import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.def.StsFormatException
import fi.benaberg.sts.service.model.TemperatureReading
import fi.benaberg.sts.service.LogRef
import fi.benaberg.sts.service.util.StsFormatUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.io.path.readBytes

/**
 * Handles storing temperature readings to disk.
 */
class StorageHandler(private val log: LogRef, private val applicationDataDirPath: Path, private val ltsDataDirPath: Path) {

    private val currentTemperatureReading = TemperatureReading(-1, 0)
    private var storedReadings = 0

    companion object {
        const val LAST_READING_FILE = "last_reading.json"      // The last received reading will always be written to this file and read on start-up
        const val STORED_READINGS_FILE = "stored_readings.sts" // All received readings are stored in this file using the custom, binary STS format
        const val CORRUPT_READINGS = "corrupt"                 // Folder to store corrupt temperature readings files in
    }

    init {
        try {
            val storedReading = readLastReceived()
            if (storedReading != null) {
                currentTemperatureReading.temperature = storedReading.getInt(Constants.TEMPERATURE)
                currentTemperatureReading.timestamp = storedReading.getLong(Constants.TIMESTAMP)
            }
            storedReadings = readStoredReadings().size
            log.write("Read $storedReadings currently stored readings from disk.")
        }
        catch (exception: Exception) {
            when (exception) {
                is JSONException -> {
                    log.write("Error while reading stored temperature reading: ${exception.message}")
                }
                is StsFormatException -> {
                    log.write("Error while reading stored temperature readings: ${exception.message}. Moving file to corrupt files.")
                    handleCorruptReadingsFile()
                }
            }
        }
    }

    fun getCurrentTemperatureReading() : TemperatureReading {
        return currentTemperatureReading
    }

    fun getStoredTemperatureReadings() : List<TemperatureReading> {
        return readStoredReadings()
    }

    @Throws(IOException::class, JSONException::class)
    fun storeData(jsonObject: JSONObject) {
        log.write("Storing received temperature...")

        val timestamp = Instant.now().toEpochMilli()
        val temperature = jsonObject.getInt(Constants.TEMPERATURE)

        currentTemperatureReading.timestamp = timestamp
        currentTemperatureReading.temperature = temperature

        // Create JSON object to store on disk
        val storeJson = JSONObject()
        storeJson.put(Constants.TIMESTAMP, timestamp)
        storeJson.put(Constants.TEMPERATURE, temperature)

        // Resolve files
        val lastReceivedFile = applicationDataDirPath.toFile().resolve(LAST_READING_FILE)
        val storedReadingsFile = ltsDataDirPath.toFile().resolve(STORED_READINGS_FILE)

        // Verify that files and parent directories exist
        if (!lastReceivedFile.exists()) {
            lastReceivedFile.parentFile.mkdirs()
            lastReceivedFile.createNewFile()
        }
        if (!storedReadingsFile.exists()) {
            storedReadingsFile.parentFile.mkdirs()
            storedReadingsFile.createNewFile()
        }

        // Store last received temperature
        lastReceivedFile.writeText(storeJson.toString(), StandardCharsets.UTF_8)

        // Add to LTS by first appending header if file is empty
        if (storedReadingsFile.length() == 0L) {
            storedReadingsFile.writeBytes(StsFormatUtil.encodeHeader())
        }
        storedReadingsFile.appendBytes(StsFormatUtil.encode(log, currentTemperatureReading))
        log.write("Successfully stored temperature data: $storeJson ")
        log.write("Total stored readings: ${++storedReadings}")
    }

    @Throws(JSONException::class)
    private fun readLastReceived(): JSONObject? {
        log.write("Reading stored temperature...")

        val filePath = applicationDataDirPath.resolve(LAST_READING_FILE)

        // Return null if no temperature reading exists
        if (!Files.exists(filePath)) {
            log.write("No stored temperature found.")
            return null
        }

        // Read file contents
        val jsonObject = JSONObject(filePath.toFile().readText())
        log.write("Successfully read stored temperature!")

        return jsonObject
    }

    private fun readStoredReadings(): List<TemperatureReading> {
        val filePath = ltsDataDirPath.resolve(STORED_READINGS_FILE)
        if (Files.exists(filePath)) {
            return StsFormatUtil.decode(log, filePath.readBytes())
        }
        return emptyList()
    }

    private fun handleCorruptReadingsFile() {
        try {
            val corruptFilePath = ltsDataDirPath
                .resolve(CORRUPT_READINGS)
                .resolve("${Instant.now().toEpochMilli()}-$STORED_READINGS_FILE")
            corruptFilePath.toFile().parentFile.mkdirs()
            Files.move(ltsDataDirPath.resolve(STORED_READINGS_FILE), corruptFilePath, StandardCopyOption.ATOMIC_MOVE)
        }
        catch (exception: IOException) {
            log.write("Error while moving file: ${exception.message}")
        }
    }
}