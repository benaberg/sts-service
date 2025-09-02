package fi.benaberg.sts.service.util

import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.model.TemperatureReading
import org.json.JSONObject

class JSONUtil {

    companion object {

        fun temperatureReadingToJSON(reading: TemperatureReading, includeSensorId: Boolean): JSONObject {
            val jsonObject = JSONObject()
            if (includeSensorId) {
                jsonObject.put(Constants.SENSOR_ID, reading.sensorId)
            }
            jsonObject.put(Constants.TEMPERATURE, reading.temperature)
            jsonObject.put(Constants.TIMESTAMP, reading.timestamp)
            return jsonObject
        }
    }
}