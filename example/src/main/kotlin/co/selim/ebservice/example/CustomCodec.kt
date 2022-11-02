package co.selim.ebservice.example

import co.selim.ebservice.annotation.EventBusService
import co.selim.ebservice.core.initializeServiceCodec
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@EventBusService
interface WeatherService {
  suspend fun getWeather(city: String): String

  companion object {
    fun create(vertx: Vertx): WeatherService = WeatherServiceImpl(vertx)
  }
}

class WeatherVerticle : CoroutineVerticle() {
  override suspend fun start() {
    vertx.getWeatherRequests
      .onEach { (request, reply) ->
        val weather = if (request == "Frankfurt am Main") {
          "Sunny"
        } else {
          "Partly cloudy"
        }

        reply(weather)
      }
      .launchIn(this)
  }
}

suspend fun main() {
  val vertx = Vertx.vertx()

  val reversingCodec = object : MessageCodec<Any, Any> {
    override fun decodeFromWire(pos: Int, buffer: Buffer) = throw UnsupportedOperationException()
    override fun encodeToWire(buffer: Buffer, s: Any?) = throw UnsupportedOperationException()
    override fun transform(s: Any?) = (s as? String)?.reversed() ?: s
    override fun name() = "reversing-codec"
    override fun systemCodecID(): Byte = -1
  }
  vertx.eventBus().initializeServiceCodec(reversingCodec)

  vertx.deployVerticle(WeatherVerticle()).await()

  val weatherService = WeatherService.create(vertx)
  val city = "Frankfurt am Main"
  println(weatherService.getWeather(city))
  println(weatherService.getWeather(city.reversed()))


  vertx.close().await()
}
