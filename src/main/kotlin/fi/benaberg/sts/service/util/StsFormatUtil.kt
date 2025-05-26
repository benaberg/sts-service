package fi.benaberg.sts.service.util

import fi.benaberg.sts.service.def.StsFormatException
import fi.benaberg.sts.service.model.TemperatureReading
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Util class for working with the STS data format.
 * The STS format consists of the following fields:
 *
 *  - Header:
 *      - Format identifier ("sts")
 *      - Format version ("1")
 *  - Payload:
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
        private const val STS_VERSION_LEN = 1
        private const val STS_TIMESTAMP_LEN = 8
        private const val STS_TEMPERATURE_LEN = 1

        /**
         * Encodes the STS format header and returns the encoded data.
         */
        fun encodeHeader(): ByteArray {
            val buffer = ByteBuffer.allocate(STS_MAGIC_LEN + STS_VERSION_LEN)
            buffer.put(STS_MAGIC.toByteArray(StandardCharsets.UTF_8))
            buffer.put(STS_VERSION_1.toByte())
            return buffer.array()
        }

        /**
         * Encodes a single temperature reading object according to the STS format and returns the encoded data.
         */
        @Throws(StsFormatException::class)
        fun encode(temperatureReading: TemperatureReading): ByteArray {
            LogUtil.write("Encoding temperature reading with timestamp: ${temperatureReading.temperature}, temperature: ${temperatureReading.temperature}")
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
                val buffer = ByteBuffer.allocate(STS_TIMESTAMP_LEN + STS_TEMPERATURE_LEN)
                buffer.putLong(temperatureReading.timestamp)
                buffer.put(temperatureReading.temperature.toByte())
                LogUtil.write("Successfully encoded temperature reading!")
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
        fun decode(payload: ByteArray): List<TemperatureReading> {
            LogUtil.write("Decoding temperature readings from payload with size: ${payload.size}")
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
                val version = buffer.get().toInt()
                if (version != STS_VERSION_1) {
                    throw StsFormatException("Unsupported format version: $version")
                }

                // Extract temperature readings
                while (buffer.hasRemaining()) {
                    val timestamp = buffer.getLong()
                    val temperature = buffer.get().toInt()
                    readings.add(TemperatureReading(temperature, timestamp))
                }

                LogUtil.write("Successfully decoded ${readings.size} temperature reading(s)!")
                return readings
            }
            catch (exception: Exception) {
                throw StsFormatException(exception)
            }
        }
    }
}