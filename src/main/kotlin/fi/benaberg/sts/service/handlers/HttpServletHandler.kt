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
class HttpServletHandler(private val httpPort: Int, wsPort: Int, temperatureContext: String, dashboardContext: String, storageHandler: StorageHandler) {

    private val server: HttpServer by lazy { HttpServer.create(InetSocketAddress(httpPort), 0) }

    init {
        server.createContext(temperatureContext, TemperatureRequestHandler(storageHandler))
        server.createContext(dashboardContext, DashboardRequestHandler(wsPort))
        server.executor = null
    }

    fun start() {
        LogUtil.write("Setting up HTTP servlet on port: $httpPort")
        server.start()
    }

    private class TemperatureRequestHandler(private val storageHandler: StorageHandler) : HttpHandler {

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
                        jsonObject.put(Constants.TEMPERATURE, storageHandler.getTemperatureReading().temperature)
                        jsonObject.put(Constants.TIMESTAMP, storageHandler.getTemperatureReading().timestamp)

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

                        storageHandler.setTemperature(jsonObject.getInt(Constants.TEMPERATURE))
                        storageHandler.setTimestamp(Instant.now().toEpochMilli())

                        // Store temperature
                        val storedJson = JSONObject()
                        storedJson.put(Constants.TEMPERATURE, storageHandler.getTemperatureReading().temperature)
                        storedJson.put(Constants.TIMESTAMP, storageHandler.getTemperatureReading().timestamp)
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

    private class DashboardRequestHandler(private val port: Int) : HttpHandler {

        override fun handle(exchange: HttpExchange) {
            serveHtml(exchange)
        }

        fun serveHtml(exchange: HttpExchange) {
            try {
                // Get HTML
                var html = object {}.javaClass.getResource("/fi/benaberg/sts/service/html/Dashboard.html")!!.readText()
                html = html.replace("{{WS_PORT}}", port.toString())

                // Serve HTML
                val bytes = html.toByteArray()
                exchange.responseHeaders?.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            catch (exception: Exception) {
                LogUtil.write("Failed to serve HTML: ${exception.message}")
            }
            finally {
                exchange.close()
            }
        }
    }
}