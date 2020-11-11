package co.selim.ebservice.example

import co.selim.ebservice.annotation.EventBusService
import co.selim.ebservice.core.initializeServiceCodec
import co.selim.sample.division.divide
import co.selim.sample.division.divisionRequests
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


class SampleVerticle : CoroutineVerticle() {
  override suspend fun start() {
    when (val divisionResult = vertx.divide(DivisionRequest(5.0, 0.0))) {
      is Division.Success -> println("Yay! $divisionResult")
      is Division.Error -> System.err.println(divisionResult)
    }

    when (val divisionResult = vertx.divide(DivisionRequest(5.0, 2.0))) {
      is Division.Success -> println("Yay! $divisionResult")
      is Division.Error -> System.err.println(divisionResult)
    }
  }
}

@EventBusService(
  topic = "co.selim.sample.division",
  consumes = DivisionRequest::class,
  produces = Division::class,
  propertyName = "divisionRequests",
  functionName = "divide"
)
class DivisionVerticle : CoroutineVerticle() {
  override suspend fun start() {
    vertx.divisionRequests
      .onEach { request ->
        val (dividend, divisor) = request.body
        request.reply(
          if (divisor == 0.0) {
            Division.Error("Can't divide by zero")
          } else {
            Division.Success(dividend / divisor)
          }
        )
      }.launchIn(this)
  }
}


data class DivisionRequest(val dividend: Double, val divisor: Double)

sealed class Division {
  data class Success(val quotient: Double) : Division()
  data class Error(val message: String) : Division()
}

suspend fun main() {
  val vertx = Vertx.vertx()
  vertx.eventBus().initializeServiceCodec()
  vertx.deployVerticle(DivisionVerticle::class.java.name).await()
  vertx.deployVerticle(SampleVerticle::class.java.name).await()
  vertx.close().await()
}
