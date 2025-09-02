package fi.benaberg.sts.service.util

import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.model.TemperatureReading
import org.json.JSONArray
import org.json.JSONObject

class JSONUtil {

    companion object {

        fun temperatureReadingToJSON(reading: TemperatureReading): JSONObject {
            val jsonObject = JSONObject()
            jsonObject.put(Constants.SENSOR_ID, reading.sensorId)
            jsonObject.put(Constants.SENSOR_NAME, reading.sensorName)
            jsonObject.put(Constants.TEMPERATURE, reading.temperature)
            jsonObject.put(Constants.TIMESTAMP, reading.timestamp)
            return jsonObject
        }

        fun sensorsToJSON(sensors: Map<Int, String>): JSONArray {
            val jsonArray = JSONArray()
            sensors.forEach { (id, name) ->
                val jsonObject = JSONObject()
                jsonObject.put(Constants.SENSOR_ID, id)
                jsonObject.put(Constants.SENSOR_NAME, name)
                jsonArray.put(jsonObject)
            }
            return jsonArray
        }
    }
}