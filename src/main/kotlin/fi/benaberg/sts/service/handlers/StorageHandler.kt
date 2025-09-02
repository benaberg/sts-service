package fi.benaberg.sts.service.handlers

import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.def.StsFormatException
import fi.benaberg.sts.service.model.TemperatureReading
import fi.benaberg.sts.service.LogRef
import fi.benaberg.sts.service.def.TemperatureListener
import fi.benaberg.sts.service.util.StsFormatUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.io.path.pathString
import kotlin.io.path.readBytes

/**
 * Handles storing temperature readings to disk.
 */
class StorageHandler(private val log: LogRef, private val applicationDataDirPath: Path, private val ltsDataDirPath: Path) {

    private val currentTemperatureReadings = mutableMapOf<Int, TemperatureReading>()
    private val temperatureListeners = mutableListOf<TemperatureListener>()
    private val sensors = mutableMapOf<Int, String>()
    private var storedReadings = 0

    companion object {

        // The last received reading is stored per sensor and will be written to a file named <sensorID>_last_reading.json
        // or last_reading.json if the reading has no sensor ID and read on start-up.
        const val LAST_READING_FILE = "last_reading.json"

        // Received readings are stored in this file using the custom, binary STS format, named <sensorID>_stored_readings.sts
        // or stored_readings.sts for readings that has no sensor ID.
        const val STORED_READINGS_FILE = "stored_readings.sts"

        // Folder to store corrupt temperature readings files in
        const val CORRUPT_READINGS = "corrupt"
    }

    init {
        try {
            // Read currently known sensor IDs
            readSensorIds()

            // Read last stored values
            sensors.forEach { (id, name) ->
                // Read last received for sensor ID
                val storedReading = readLastReceived(id, name)
                if (storedReading != null) {
                    val temperatureReading = TemperatureReading(-1, "", -1, 0)

                    if (id != -1) {
                        temperatureReading.sensorId = storedReading.getInt(Constants.SENSOR_ID)
                        temperatureReading.sensorName = name
                    }
                    temperatureReading.temperature = storedReading.getInt(Constants.TEMPERATURE)
                    temperatureReading.timestamp = storedReading.getLong(Constants.TIMESTAMP)

                    currentTemperatureReadings[id] = temperatureReading
                }
            }
            storedReadings += readStoredReadings(sensors).size
            log.write("Read $storedReadings currently stored readings from disk.")
        }
        catch (exception: JSONException) {
            log.write("Error while reading stored temperature reading: ${exception.message}")
        }
    }

    fun getSensors() : Map<Int, String> {
        return sensors;
    }

    fun getSensorIds() : Collection<Int> {
        return sensors.keys
    }

    fun getCurrentTemperatureReading(sensorId: Int) : TemperatureReading? {
        return currentTemperatureReadings[sensorId]
    }

    fun getStoredTemperatureReadings(sensorId: Int) : Collection<TemperatureReading> {
        return readStoredReadings(sensors).filter { it.sensorId == sensorId }
    }

    fun addListener(listener: TemperatureListener) {
        temperatureListeners.add(listener)
    }

    fun removeListener(listener: TemperatureListener) {
        temperatureListeners.remove(listener)
    }

    @Throws(IOException::class, JSONException::class)
    fun storeData(sensorId: Int, jsonObject: JSONObject) {
        log.write("Storing received temperature...")

        val sensorName = jsonObject.getString(Constants.SENSOR_NAME)
        val temperature = jsonObject.getInt(Constants.TEMPERATURE)
        val timestamp = Instant.now().toEpochMilli()

        val receivedTemperatureReading = TemperatureReading(sensorId, sensorName, temperature, timestamp)
        currentTemperatureReadings[sensorId] = receivedTemperatureReading
        sensors[sensorId] = sensorName

        // Create JSON object to store on disk
        val storeJson = JSONObject()
        storeJson.put(Constants.SENSOR_ID, sensorId)
        storeJson.put(Constants.TEMPERATURE, temperature)
        storeJson.put(Constants.TIMESTAMP, timestamp)

        // Resolve files
        val lastReceivedFile: File =
            if (sensorId == -1) {
                applicationDataDirPath.toFile().resolve(LAST_READING_FILE)
            }
            else {
                applicationDataDirPath.toFile().resolve("${sensorId}_${sensorName}_$LAST_READING_FILE")
            }

        val storedReadingsFile = ltsDataDirPath.toFile().resolve("${StsFormatUtil.CURRENT_VERSION}_$STORED_READINGS_FILE")

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
        storedReadingsFile.appendBytes(StsFormatUtil.encode(log, receivedTemperatureReading))

        // Notify listeners
        temperatureListeners.forEach{ it.onReadingAdded(receivedTemperatureReading) }

        log.write("Successfully stored temperature data: $storeJson ")
        log.write("Total stored readings: ${++storedReadings}")
    }

    @Throws(JSONException::class)
    private fun readLastReceived(sensorId: Int, sensorName: String): JSONObject? {
        log.write("Reading stored temperature for sensor with ID: $sensorId")
        val filePath: Path? =
            if (sensorId == -1) {
                applicationDataDirPath.resolve(LAST_READING_FILE)
            }
            else {
                applicationDataDirPath.resolve("${sensorId}_${sensorName}_$LAST_READING_FILE")
            }

        // Return null if no temperature reading exists
        if (!Files.exists(filePath!!)) {
            log.write("No stored temperature found.")
            return null
        }

        // Read file contents
        val jsonObject = JSONObject(filePath.toFile().readText())
        log.write("Successfully read stored temperature!")

        return jsonObject
    }

    private fun readSensorIds() {
        log.write("Reading sensor IDs from disk...")
        sensors.clear()

        val filenames = applicationDataDirPath.toFile().listFiles()
            ?.filter { it.path.contains(LAST_READING_FILE) }
            ?.map { it.name }
            ?: emptyList()
        filenames.forEach { filename ->
            try {
                val parts = filename.split("_")
                if (parts.size == 2) {
                    // No sensor ID prefix
                    sensors[-1] = ""
                }
                else if (parts.size == 4) {
                    // Attempt to parse sensor ID
                    sensors[parts[0].toInt()] = parts[1]
                }
            }
            catch (exception: NumberFormatException) {
                log.write("Failed to parse sensor ID from path: $filename")
            }
        }
        log.write("Successfully read ${sensors.size} sensor IDs from disk!")
    }

    private fun readStoredReadings(sensorIds: Map<Int, String>): List<TemperatureReading> {
        log.write("Reading stored readings from disk...")
        val paths = ltsDataDirPath.toFile().listFiles()
            ?.filter { it.path.contains(STORED_READINGS_FILE) }
            ?.map { it.toPath() }
            ?: emptyList()
        val readings = mutableListOf<TemperatureReading>()
        paths.forEach { path ->
            try {
                readings.addAll(StsFormatUtil.decode(log, sensorIds, path.readBytes()))
            }
            catch (exception: NumberFormatException) {
                log.write("Failed to parse STS format version from path: ${path.pathString}, reason: ${exception.message}")
            }
            catch (exception: StsFormatException) {
                log.write("Error while reading stored temperature readings from path: ${path.pathString}, reason: ${exception.message}. Moving file to corrupt files.")
                exception.printStackTrace()
                handleCorruptReadingsFile()
            }
        }
        return readings
    }

    private fun handleCorruptReadingsFile() {
        try {
            val corruptFilePath = ltsDataDirPath
                .resolve(CORRUPT_READINGS)
                .resolve("${Instant.now().toEpochMilli()}_$STORED_READINGS_FILE")
            corruptFilePath.toFile().parentFile.mkdirs()
            Files.move(ltsDataDirPath.resolve("${StsFormatUtil.CURRENT_VERSION}_$STORED_READINGS_FILE"), corruptFilePath, StandardCopyOption.ATOMIC_MOVE)
        }
        catch (exception: IOException) {
            log.write("Error while moving file: ${exception.message}")
        }
    }
}