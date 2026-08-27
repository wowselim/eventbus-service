package co.selim.ebservice.codegen

import co.selim.ebservice.core.Visibility
import java.util.*

internal fun generateServiceFile(
  serviceName: String,
  functions: Sequence<Function>,
  visibility: Visibility,
): String {
  val packageName = serviceName.substringBeforeLast('.', "")
  val simpleName = serviceName.substringAfterLast('.')
  val functionList = functions.toList()
  val topic = "$packageName.${simpleName.lowercase(Locale.getDefault())}"

  return buildString {
    appendLine(
      """
      @file:Suppress("RedundantVisibilityModifier")

      package $packageName

      import co.selim.ebservice.core.EventBusServiceRequest
      import co.selim.ebservice.core.EventBusServiceRequestImpl
      import co.selim.ebservice.core.deliveryOptions
      import io.vertx.core.Vertx
      import io.vertx.kotlin.coroutines.coAwait
      import io.vertx.kotlin.coroutines.toReceiveChannel
      import kotlinx.coroutines.flow.Flow
      import kotlinx.coroutines.flow.map
      import kotlinx.coroutines.flow.receiveAsFlow

      private const val TOPIC: String = "$topic"
      """.trimIndent()
    )
    appendLine()
    functionList.forEach { function ->
      generateParameterContainer(function)?.let { container ->
        append(container)
        appendLine()
      }
    }
    append(generateServiceImpl(simpleName, serviceName, functionList))
    appendLine()
    appendLine()
    append(generateServiceRequestsClass(simpleName, functionList, visibility))
  }
}

private fun generateServiceImpl(
  simpleName: String,
  serviceName: String,
  functions: List<Function>,
): String {
  return buildString {
    append("class ${simpleName}Impl(private val vertx: Vertx) : $serviceName {")
    functions.forEach { function ->
      append("\n\n")
      append(generateFunction(function).prependIndent("  "))
    }
    appendLine("}")
  }
}

private fun generateFunction(function: Function): String {
  (val name = name, val returnType = returnType) = function
  val parameters = function.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
  return buildString {
    append("override ")
    if (function.isSuspend) append("suspend ")
    appendLine("fun $name($parameters): ${functionReturnType(function)} {")
    if (isOneWay(function)) {
      appendLine(
        $$"""
        vertx.eventBus()
          .send("$TOPIC.$$name", $${requestMessage(function)}, deliveryOptions)
        """.trimIndent().prependIndent("  ")
      )
    } else {
      appendLine(
        $$"""
        return vertx.eventBus()
          .request<$$returnType>("$TOPIC.$$name", $${requestMessage(function)}, deliveryOptions)
          .coAwait()
          .body()
        """.trimIndent().prependIndent("  ")
      )
    }
    append("}")
  }
}

private fun generateServiceRequestsClass(
  simpleName: String,
  functions: List<Function>,
  visibility: Visibility,
): String {
  val modifier = visibilityModifier(visibility)
  return buildString {
    append("$modifier object ${simpleName}Requests {")
    functions.forEach { function ->
      append("\n\n")
      append(generateRequestFunction(function, modifier).prependIndent("  "))
    }
    appendLine("}")
  }
}

private fun generateRequestFunction(function: Function, modifier: String): String {
  (val name = name, val returnType = returnType) = function
  val parameters = function.parameters.toList()
  val requestType = when (parameters.size) {
    0 -> "Unit"
    1 -> parameters.first().type
    else -> containerName(name)
  }
  return buildString {
    appendLine(
      "$modifier fun $name(vertx: Vertx): Flow<${
        if (isOneWay(function)) requestType
        else "EventBusServiceRequest<$requestType, $returnType>"
      }> {"
    )
    appendLine(
      $$"""
      return vertx.eventBus()
        .consumer<$$requestType>("$TOPIC.$$name")
        .toReceiveChannel(vertx)
        .receiveAsFlow()
        $${
        if (isOneWay(function)) ".map { it.body() }"
        else ".map { EventBusServiceRequestImpl<$requestType, $returnType>(it) }"
      }
      """.trimIndent().prependIndent("  ")
    )
    append("}")
  }
}

private fun generateParameterContainer(function: Function): String? {
  val parameters = function.parameters.toList()
  if (parameters.size <= 1) return null
  return buildString {
    appendLine("data class ${containerName(function.name)}(")
    parameters.forEach { parameter ->
      appendLine("  val ${parameter.name}: ${parameter.type},")
    }
    appendLine(")")
  }
}

private fun requestMessage(function: Function): String {
  val parameters = function.parameters.toList()
  return when (parameters.size) {
    0 -> "Unit"
    1 -> parameters.first().name
    else -> "${containerName(function.name)}(${parameters.joinToString(", ") { it.name }})"
  }
}

private fun functionReturnType(function: Function): String =
  if (isOneWay(function)) "Unit" else function.returnType

private fun isOneWay(function: Function): Boolean =
  function.returnType.removePrefix("kotlin.") == "Unit"

private fun visibilityModifier(visibility: Visibility): String =
  if (visibility == Visibility.INTERNAL) "internal" else ""

private fun containerName(functionName: String): String =
  functionName.replaceFirstChar { it.titlecase(Locale.ROOT) } + "Parameters"
