package co.selim.ebservice.codegen

import co.selim.ebservice.core.Visibility
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeGeneratorTest {

  private val mathService = "com.example.MathService"
  private val division = "Division"
  private val weatherReport = "WeatherReport"

  private val functions = listOf(
    Function(
      name = "divide",
      returnType = division,
      parameters = setOf(
        Parameter("dividend", "Double"),
        Parameter("divisor", "Double"),
      ),
      isSuspend = true,
    ),
    Function(
      name = "getWeather",
      returnType = weatherReport,
      parameters = setOf(Parameter("city", "String")),
      isSuspend = true,
    ),
    Function(
      name = "ping",
      returnType = "String",
      parameters = emptySet(),
      isSuspend = true,
    ),
    Function(
      name = "call",
      returnType = "Unit",
      parameters = setOf(Parameter("request", "Int")),
      isSuspend = false,
    ),
  )

  private fun generateSource(
    functions: List<Function> = this.functions,
    visibility: Visibility = Visibility.INTERNAL,
  ): String {
    return generateServiceFile(mathService, functions.asSequence(), visibility)
  }

  @Test
  fun `generated file is annotated with suppress and contains the topic constant`() {
    val source = generateSource()

    assertTrue(source.contains("@file:Suppress(\"RedundantVisibilityModifier\")"))
    assertTrue(source.contains("private const val TOPIC: String = \"com.example.mathservice\""))
  }

  @Test
  fun `requests object is internal by default`() {
    val source = generateSource()

    assertTrue(source.contains("internal object MathServiceRequests"))
    assertTrue(source.contains("internal fun divide("))
    assertTrue(source.contains("internal fun call("))
  }

  @Test
  fun `requests object is public when requested`() {
    val source = generateSource(visibility = Visibility.PUBLIC)

    assertTrue(source.contains("object MathServiceRequests"))
    assertFalse(source.contains("internal"))
  }

  @Test
  fun `multi parameter request wraps parameters in a generated container`() {
    val source = generateSource()

    assertTrue(source.contains("data class DivideParameters("))
    assertTrue(source.contains("Flow<EventBusServiceRequest<DivideParameters, Division>>"))
    assertTrue(source.contains(".consumer<DivideParameters>(\"\$TOPIC.divide\")"))
  }

  @Test
  fun `single parameter request uses the parameter type directly`() {
    val source = generateSource()

    assertTrue(source.contains("Flow<EventBusServiceRequest<String, WeatherReport>>"))
    assertTrue(source.contains(".consumer<String>(\"\$TOPIC.getWeather\")"))
  }

  @Test
  fun `parameterless request uses unit as the request type`() {
    val source = generateSource()

    assertTrue(source.contains("Flow<EventBusServiceRequest<Unit, String>>"))
    assertTrue(source.contains(".consumer<Unit>(\"\$TOPIC.ping\")"))
  }

  @Test
  fun `two way requests are wrapped in an event bus service request impl`() {
    val source = generateSource()

    assertTrue(source.contains(".map { EventBusServiceRequestImpl<DivideParameters, Division>(it) }"))
  }

  @Test
  fun `one way requests expose only the request type`() {
    val source = generateSource()

    assertTrue(source.contains("fun call("))
    assertTrue(source.contains("Flow<Int>"))
    assertTrue(source.contains(".map { it.body() }"))
  }

  @Test
  fun `service impl sends event bus requests for two way functions`() {
    val source = generateSource()

    assertTrue(source.contains("class MathServiceImpl("))
    assertTrue(source.contains("override suspend fun divide("))
    assertTrue(source.contains(".request<Division>(\"\$TOPIC.divide\", DivideParameters(dividend, divisor), deliveryOptions)"))
    assertTrue(source.contains(".coAwait()"))
  }

  @Test
  fun `service impl sends one way functions without waiting for a reply`() {
    val source = generateSource()

    assertTrue(source.contains("override fun call("))
    assertTrue(source.contains(".send(\"\$TOPIC.call\", request, deliveryOptions)"))
  }
}
