package co.selim.ebservice.test

import co.selim.ebservice.annotation.EventBusService
import co.selim.ebservice.core.initializeServiceCodec
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.Serializable

@EventBusService
interface WeatherService {
  suspend fun getWeather(city: String): WeatherReport
  suspend fun getWeatherBalloon(message: String): WeatherBalloon

  sealed interface WeatherReport {
    data class Success(val description: String) : WeatherReport, Serializable
    data class Failure(val message: String) : WeatherReport, Serializable
  }

  object WeatherBalloon : Serializable {
    private fun readResolve(): Any = WeatherBalloon
  }

  companion object {
    fun create(vertx: Vertx): WeatherService = WeatherServiceImpl(vertx)
  }
}

class ClusteredCustomCodecTest {

  @Test
  fun `test clustered custom codec`(): Unit = runBlocking {
    var serviceVertx: Vertx? = null
    var consumerVertx: Vertx? = null
    val weatherChannel = Channel<WeatherService.WeatherReport>(2)
    val objectChannel = Channel<WeatherService.WeatherBalloon>(1)

    val weatherVerticle = object : CoroutineVerticle() {
      override suspend fun start() {
        WeatherServiceRequests.getWeather(vertx)
          .onEach { (request, reply) ->
            val weather = if (request == "Frankfurt am Main") {
              WeatherService.WeatherReport.Success("Sunny")
            } else {
              WeatherService.WeatherReport.Failure("Unknown city '$request'")
            }

            reply(weather)
          }
          .launchIn(this)

        WeatherServiceRequests.getWeatherBalloon(vertx)
          .onEach { (_, reply) ->
            reply(WeatherService.WeatherBalloon)
          }
          .launchIn(this)
      }
    }

    val weatherConsumerVerticle = object : CoroutineVerticle() {
      override suspend fun start() {
        val weatherService = WeatherService.create(vertx)

        with(weatherChannel) {
          send(weatherService.getWeather("Frankfurt am Main"))
          send(weatherService.getWeather("Berlin"))
        }

        with(objectChannel) {
          send(weatherService.getWeatherBalloon("Kaboom"))
        }
      }
    }

    try {
      serviceVertx = Vertx.clusteredVertx(VertxOptions().setClusterManager(FakeClusterManager())).coAwait()
      serviceVertx.eventBus().initializeServiceCodec(ObjectCodec, DeliveryOptions().setLocalOnly(false))
      serviceVertx.deployVerticle(weatherVerticle, DeploymentOptions()).coAwait()

      consumerVertx = Vertx.clusteredVertx(VertxOptions().setClusterManager(FakeClusterManager())).coAwait()
      consumerVertx.eventBus().initializeServiceCodec(ObjectCodec, DeliveryOptions().setLocalOnly(false))
      consumerVertx.deployVerticle(weatherConsumerVerticle, DeploymentOptions()).coAwait()

      assertEquals(WeatherService.WeatherReport.Success("Sunny"), weatherChannel.receive())
      assertEquals(WeatherService.WeatherReport.Failure("Unknown city 'Berlin'"), weatherChannel.receive())
      assertEquals(WeatherService.WeatherBalloon, objectChannel.receive())
    } finally {
      serviceVertx?.run { close().coAwait() }
      consumerVertx?.run { close().coAwait() }
    }
  }
}
