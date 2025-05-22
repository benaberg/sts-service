package fi.benaberg.sts.service.handlers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import fi.benaberg.sts.service.def.Constants
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Handles setting up the server and incoming requests.
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
                println("Error while reading stored temperature reading: " + exception.message)
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
                        println("Received temperature GET")
                        // Compose response
                        val jsonObject = JSONObject()
                        jsonObject.put(Constants.TEMPERATURE, temperature.toString())
                        jsonObject.put(Constants.LAST_UPDATED, lastUpdated)

                        // Send response headers
                        val jsonString = jsonObject.toString()
                        exchange.sendResponseHeaders(200, jsonString.length.toLong())

                        // Write response
                        val os = exchange.responseBody
                        os.write(jsonString.toByteArray())
                        os.close()
                        println("Successfully served temperature!")
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                println("Could not compose temperature JSON. Reason: " + exception.message)
                            }
                            is IOException -> {
                                println("Could not write response. Reason: " + exception.message)
                            }
                        }
                        exchange.sendResponseHeaders(500, -1)
                    }
                }
                "PUT" -> {
                    // Update temperature
                    try {
                        println("Received temperature PUT")

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
                        exchange.sendResponseHeaders(200, -1)
                        println("Successfully updated temperature!")
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                println("Could not parse JSON in request body. Reason: " + exception.message)
                            }
                            is IOException -> {
                                println("IOException while handling request. Reason: " + exception.message)
                            }
                            is SecurityException -> {
                                println("SecurityException while handling request. Reason: " + exception.message)
                            }
                        }
                        exchange.sendResponseHeaders(500, -1)
                    }
                }
            }
        }
    }
}