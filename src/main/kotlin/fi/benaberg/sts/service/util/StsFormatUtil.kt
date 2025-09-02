package fi.benaberg.sts.service.util

import fi.benaberg.sts.service.LogRef
import fi.benaberg.sts.service.def.StsFormatException
import fi.benaberg.sts.service.model.TemperatureReading
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Util class for working with the STS data format.
 * The STS format v2 consists of the following fields:
 *
 *  - Header:
 *      - Format identifier ("sts")
 *      - Format version ("1")
 *  - Payload:
 *      - Sensor ID (2 bytes)
 *      - Timestamp (8 bytes)
 *      - Temperature (1 byte)
 *
 * The header is appended to the beginning of the file.
 * The payload fields are repeating.
 *
 */
class StsFormatUtil {

    companion object {
        private const val STS_MAGIC = "sts"
        private const val STS_MAGIC_LEN = 3
        private const val STS_VERSION_1 = 1
        private const val STS_VERSION_2 = 2
        private const val STS_VERSION_LEN = 1
        private const val STS_SENSOR_ID_LEN = 2
        private const val STS_TIMESTAMP_LEN = 8
        private const val STS_TEMPERATURE_LEN = 1

        const val CURRENT_VERSION = STS_VERSION_2

        /**
         * Encodes the STS format header and returns the encoded data.
         */
        fun encodeHeader(): ByteArray {
            val buffer = ByteBuffer.allocate(STS_MAGIC_LEN + STS_VERSION_LEN)
            buffer.put(STS_MAGIC.toByteArray(StandardCharsets.UTF_8))
            buffer.put(CURRENT_VERSION.toByte())
            return buffer.array()
        }

        /**
         * Encodes a single temperature reading object according to the STS format and returns the encoded data.
         */
        @Throws(StsFormatException::class)
        fun encode(log: LogRef, temperatureReading: TemperatureReading): ByteArray {
            log.write("Encoding temperature reading with timestamp: ${temperatureReading.timestamp}, temperature: ${temperatureReading.temperature}")
            try {
                // Check that temperature reading is valid
                if (temperatureReading.temperature < -127 || temperatureReading.temperature > 128) {
                    throw StsFormatException("Invalid temperature reading: ${temperatureReading.temperature}. Reading is not within valid range: [-127,128]")
                }

                // Check that timestamp is valid
                if (temperatureReading.timestamp < 0) {
                    throw StsFormatException("Invalid timestamp: ${temperatureReading.timestamp}. Timestamp can not be negative.")
                }

                // Encode data
                val buffer = ByteBuffer.allocate(STS_SENSOR_ID_LEN + STS_TIMESTAMP_LEN + STS_TEMPERATURE_LEN)

                buffer.putShort(temperatureReading.sensorId.toShort())
                buffer.putLong(temperatureReading.timestamp)
                buffer.put(temperatureReading.temperature.toByte())
                log.write("Successfully encoded temperature reading!")
                return buffer.array()
            }
            catch (exception: Exception) {
                throw StsFormatException(exception)
            }
        }

        /**
         *  Decodes an STS payload and returns a list containing the present temperature readings.
         */
        @Throws(StsFormatException::class)
        fun decode(log: LogRef, sensorIds: Map<Int, String>, payload: ByteArray): List<TemperatureReading> {
            log.write("Decoding temperature readings from payload with size: ${payload.size}")
            try {
                val readings = ArrayList<TemperatureReading>()
                val buffer = ByteBuffer.wrap(payload)

                // Check that the payload starts with the STS magic bytes
                if (payload.size < STS_MAGIC_LEN + STS_VERSION_LEN || !payload.sliceArray(STS_MAGIC.indices).contentEquals(STS_MAGIC.toByteArray(StandardCharsets.UTF_8))) {
                    throw StsFormatException("Invalid payload: payload does not contain the magic bytes.")
                }

                // Set buffer position
                buffer.position(STS_MAGIC_LEN)

                // Decode version
                when (val version = buffer.get().toInt()) {
                    STS_VERSION_1 -> {
                        // Extract temperature readings
                        while (buffer.hasRemaining()) {
                            val timestamp = buffer.getLong()
                            val temperature = buffer.get().toInt()
                            readings.add(TemperatureReading(-1, "", temperature, timestamp))
                        }

                        log.write("Successfully decoded ${readings.size} temperature reading(s) from file (STS format v1)!")
                        return readings
                    }
                    STS_VERSION_2 -> {
                        // Extract temperature readings
                        while (buffer.hasRemaining()) {
                            val sensorId = buffer.getShort().toInt()
                            val timestamp = buffer.getLong()
                            val temperature = buffer.get().toInt()
                            readings.add(TemperatureReading(sensorId, sensorIds[sensorId].orEmpty(), temperature, timestamp))
                        }

                        log.write("Successfully decoded ${readings.size} temperature reading(s) from file (STS format v2)!")
                        return readings
                    }
                    else -> throw StsFormatException("Unsupported format version: $version")
                }
            }
            catch (exception: Exception) {
                throw StsFormatException(exception)
            }
        }
    }
}