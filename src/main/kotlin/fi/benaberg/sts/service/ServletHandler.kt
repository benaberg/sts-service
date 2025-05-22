package fi.benaberg.sts.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Handles setting up the server and incoming requests
 */
class ServletHandler(port: Int, context: String) {

    private val server: HttpServer by lazy { HttpServer.create(InetSocketAddress(port), 0) }

    init {
        server.createContext(context, RequestHandler())
        server.executor = null
    }

    fun start() {
        server.start()
    }

    private class RequestHandler : HttpHandler {

        @Volatile private var temperature = -1

        override fun handle(exchange: HttpExchange?) {
            if (exchange == null) {
                return
            }
            when (exchange.requestMethod) {
                "GET" -> {
                    // Fetch temperature
                    try {
                        // Compose response
                        val jsonObject = JSONObject()
                        jsonObject.put(Constants.TEMPERATURE, temperature.toString())

                        // Send response headers
                        val jsonString = jsonObject.toString()
                        exchange.sendResponseHeaders(200, jsonString.length.toLong())

                        // Write response
                        val os = exchange.responseBody
                        os.write(jsonString.toByteArray())
                        os.close()
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
                        // Read request
                        val jsonString = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
                        val jsonObject = JSONObject(jsonString)

                        // Update temperature
                        temperature = jsonObject.getInt(Constants.TEMPERATURE)

                        // Send response headers
                        exchange.sendResponseHeaders(200, -1)
                    }
                    catch (exception: Exception) {
                        when (exception) {
                            is JSONException -> {
                                println("Could not parse JSON in request body. Reason: " + exception.message)
                            }
                            is IOException -> {
                                println("Could not read request. Reason: " + exception.message)
                            }
                        }
                        exchange.sendResponseHeaders(500, -1)
                    }
                }
            }
        }
    }
}