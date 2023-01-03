package co.selim.ebservice.test

import co.selim.ebservice.annotation.EventBusService
import co.selim.ebservice.core.initializeServiceCodec
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
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

  sealed interface WeatherReport {
    data class Success(val description: String) : WeatherReport, Serializable
    data class Failure(val message: String) : WeatherReport, Serializable
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

    val weatherVerticle = object : CoroutineVerticle() {
      override suspend fun start() {
        vertx.getWeatherRequests
          .onEach { (request, reply) ->
            val weather = if (request == "Frankfurt am Main") {
              WeatherService.WeatherReport.Success("Sunny")
            } else {
              WeatherService.WeatherReport.Failure("Unknown city '$request'")
            }

            reply(weather)
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
      }
    }

    try {
      serviceVertx = Vertx.clusteredVertx(VertxOptions().setClusterManager(FakeClusterManager())).await()
      serviceVertx.eventBus().initializeServiceCodec(ObjectCodec, DeliveryOptions().setLocalOnly(false))
      serviceVertx.deployVerticle(weatherVerticle, DeploymentOptions()).await()

      consumerVertx = Vertx.clusteredVertx(VertxOptions().setClusterManager(FakeClusterManager())).await()
      consumerVertx.eventBus().initializeServiceCodec(ObjectCodec, DeliveryOptions().setLocalOnly(false))
      consumerVertx.deployVerticle(weatherConsumerVerticle, DeploymentOptions()).await()

      assertEquals(WeatherService.WeatherReport.Success("Sunny"), weatherChannel.receive())
      assertEquals(WeatherService.WeatherReport.Failure("Unknown city 'Berlin'"), weatherChannel.receive())
    } finally {
      serviceVertx?.run { close().await() }
      consumerVertx?.run { close().await() }
    }
  }
}
