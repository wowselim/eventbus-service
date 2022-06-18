package co.selim.ebservice.test

import co.selim.ebservice.annotation.EventBusService
import co.selim.ebservice.core.initializeServiceCodec
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

@EventBusService
interface TestService {
  suspend fun getString(): String
  suspend fun getResultWithRequest(request: String): Result
  suspend fun callSuspending(request: List<Int>)
  fun call(request: Int)

  sealed class Result
  object Success : Result()
  object Failure : Result()

  companion object {
    fun create(vertx: Vertx): TestService = TestServiceImpl(vertx)
  }
}

@ExtendWith(VertxExtension::class)
@Timeout(1, unit = TimeUnit.SECONDS)
class ServiceTest(private val vertx: Vertx) {

  private val scope: CoroutineScope
    get() = CoroutineScope(vertx.dispatcher())

  @Test
  fun `reply is sent`() = runTest {
    val testService = TestService.create(vertx)
    vertx.getStringRequests
      .onEach { (_, reply) -> reply("Hello World") }
      .launchIn(scope)

    val message = testService.getString()

    assertEquals("Hello World", message)
  }

  @Test
  fun `request is sent`() = runTest {
    val testService = TestService.create(vertx)
    vertx.getResultWithRequestRequests
      .onEach { (request, reply) ->
        if (request != "Hello World") {
          reply(TestService.Failure)
        } else {
          reply(TestService.Success)
        }
      }
      .launchIn(scope)

    val result = testService.getResultWithRequest("Hello World")
    assertEquals(TestService.Success, result)
  }

  @Test
  fun `suspending one way request is sent`() = runTest {
    val mutex = Mutex(true)
    val testService = TestService.create(vertx)
    lateinit var receivedNumbers: List<Int>
    vertx.callSuspendingRequests
      .onEach { numbers ->
        receivedNumbers = numbers
        mutex.unlock()
      }
      .launchIn(scope)

    val sentNumbers = listOf(1, 2, 3)
    testService.callSuspending(sentNumbers)
    mutex.lock()
    assertEquals(sentNumbers, receivedNumbers)
  }

  @Test
  fun `non suspending one way request is sent`() = runTest {
    val mutex = Mutex(true)
    val testService = TestService.create(vertx)
    var receivedNumber = 0
    vertx.callRequests
      .onEach { number ->
        receivedNumber = number
        mutex.unlock()
      }
      .launchIn(scope)

    val sentNumber = 1337
    testService.call(sentNumber)
    mutex.lock()
    assertEquals(sentNumber, receivedNumber)
  }

  private fun runTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking(vertx.dispatcher(), block)
  }

  companion object {
    @JvmStatic
    @BeforeAll
    fun setup(vertx: Vertx) {
      vertx.eventBus().initializeServiceCodec()
    }
  }
}
