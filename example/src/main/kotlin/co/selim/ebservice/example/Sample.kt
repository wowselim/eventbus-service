package co.selim.ebservice.example

import co.selim.ebservice.annotation.EventBusService
import co.selim.ebservice.core.initializeServiceCodec
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@EventBusService
interface MathService {
  suspend fun add(addendA: Double, addendB: Double): Double
  suspend fun divide(dividend: Double, divisor: Double): Division

  sealed class Division {
    data class Success(val quotient: Double) : Division()
    data class Error(val message: String) : Division()
  }

  companion object {
    fun create(vertx: Vertx): MathService = MathServiceImpl(vertx)
  }
}

class SampleVerticle : CoroutineVerticle() {
  override suspend fun start() {
    val mathService = MathService.create(vertx)

    val sum = mathService.add(3.3, 4.5)
    println(sum)

    when (val divisionResult = mathService.divide(5.0, 0.0)) {
      is MathService.Division.Success -> println(divisionResult)
      is MathService.Division.Error -> System.err.println(divisionResult)
    }

    when (val divisionResult = mathService.divide(5.0, 2.0)) {
      is MathService.Division.Success -> println(divisionResult)
      is MathService.Division.Error -> System.err.println(divisionResult)
    }

  }
}

class DivisionVerticle : CoroutineVerticle() {
  override suspend fun start() {
    vertx.divideRequests
      .onEach { (request, reply) ->
        val (dividend, divisor) = request
        reply(
          if (divisor == 0.0) {
            MathService.Division.Error("Can't divide by zero")
          } else {
            MathService.Division.Success(dividend / divisor)
          }
        )
      }.launchIn(this)

    vertx.addRequests
      .onEach { (request, reply) ->
        val (addendA, addendB) = request
        reply(addendA + addendB)
      }.launchIn(this)
  }
}

suspend fun main() {
  val vertx = Vertx.vertx()
  vertx.eventBus().initializeServiceCodec()
  vertx.deployVerticle(DivisionVerticle::class.java.name).await()
  vertx.deployVerticle(SampleVerticle::class.java.name).await()
  vertx.close().await()
}
