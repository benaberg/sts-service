package fi.benaberg.sts.service.handlers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import fi.benaberg.sts.service.def.Constants
import fi.benaberg.sts.service.def.HttpResponse
import fi.benaberg.sts.service.util.LogUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Handles server initializing and request handling.
 */
class ServletHandler(port: Int, context: String, storageHandler: StorageHandler) {

    private val server: HttpServer by lazy { HttpServer.create(InetSocketAddress(port), 0) }

    init {
        server.createContext(context, RequestHandler(storageHandler))
        server.executor = null
    }

    fun start() {
        server.start()
    }

    private class RequestHandler(private val storageHandler: StorageHandler) : HttpHandler {

        @Volatile private var temperature = -1
        @Volatile private var lastUpdated = 0L

        init {
            try {
                val storedReading = storageHandler.readData()
                if (storedReading != null) {
                    temperature = storedReading.getInt(Constants.TEMPERATURE)
                    lastUpdated = storedReading.getLong(Constants.LAST_UPDATED)
                }
            }
            catch (exception: JSONException) {
                LogUtil.write("Error while reading stored temperature reading: " + exception.message)
            }
        }

        override fun handle(exchange: HttpExchange?) {
            if (exchange == null) {
                return
            }
            when (exchange.requestMethod) {
                "GET" -> {
                    // Fetch temperature
                    try {
                        LogUtil.write("Received temperature GET")
                        // Compose response
                        val jsonObject = JSONObject()
                        jsonObject.put(Constants.TEMPERATURE, temperature.toString())
                        jsonObject.put(Constants.LAST_UPDATED, lastUpdated)

                        // Send response headers
                        val jsonString = jsonObject.toString()
                        exchange.sendResponseHeaders(HttpResponse.OK, jsonString.length.toLong())

                        // Write response
                        val os = exchange.responseBody
                        os.write(jsonString.toByteArray())
                        os.close()
                        LogUtil.write("Successfully served temperature!")
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                LogUtil.write("Could not compose temperature JSON. Reason: " + exception.message)
                            }
                            is IOException -> {
                                LogUtil.write("Could not write response. Reason: " + exception.message)
                            }
                        }
                        exchange.sendResponseHeaders(HttpResponse.INTERNAL_SERVER_ERROR, -1)
                    }
                }
                "PUT" -> {
                    // Update temperature
                    try {
                        LogUtil.write("Received temperature PUT")

                        // Read request
                        val jsonString = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
                        val jsonObject = JSONObject(jsonString)

                        // Update temperature
                        temperature = jsonObject.getInt(Constants.TEMPERATURE)
                        lastUpdated = Instant.now().toEpochMilli()

                        // Store temperature
                        val storedJson = JSONObject()
                        storedJson.put(Constants.TEMPERATURE, temperature)
                        storedJson.put(Constants.LAST_UPDATED, lastUpdated)
                        storageHandler.storeData(storedJson)

                        // Send response headers
                        exchange.sendResponseHeaders(HttpResponse.OK, -1)
                        LogUtil.write("Successfully updated temperature!")
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                LogUtil.write("Could not parse JSON in request body. Reason: " + exception.message)
                            }
                            is IOException -> {
                                LogUtil.write("IOException while handling request. Reason: " + exception.message)
                            }
                            is SecurityException -> {
                                LogUtil.write("SecurityException while handling request. Reason: " + exception.message)
                            }
                        }
                        exchange.sendResponseHeaders(HttpResponse.INTERNAL_SERVER_ERROR, -1)
                    }
                }
            }
        }
    }
}