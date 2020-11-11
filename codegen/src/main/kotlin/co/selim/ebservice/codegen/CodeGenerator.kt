package co.selim.ebservice.codegen

import co.selim.ebservice.core.EventBusServiceRequest
import co.selim.ebservice.core.EventBusServiceRequestImpl
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.vertx.core.Vertx
import kotlinx.coroutines.flow.Flow

internal fun generateFile(
  topic: String
): FileSpec.Builder {
  val topicProperty = PropertySpec.builder(
    "TOPIC", String::class, KModifier.PRIVATE, KModifier.CONST
  )
    .initializer("%S", topic)
    .build()

  return FileSpec.builder(topic, "ServiceExtensions.kt")
    .addProperty(topicProperty)
}

internal fun generateRequestsProperty(
  requestType: ClassName,
  responseType: ClassName,
  propertyName: String
): PropertySpec {
  val getter = FunSpec.getterBuilder()
    .addCode(
      """
      return eventBus()
        .consumer<%T>(TOPIC)
        .%M(this)
        .%M()
        .%M { %T<%T, %T>(it) }
    """.trimIndent(),
      requestType,
      MemberName("io.vertx.kotlin.coroutines", "toChannel"),
      MemberName("kotlinx.coroutines.flow", "receiveAsFlow"),
      MemberName("kotlinx.coroutines.flow", "map"),
      EventBusServiceRequestImpl::class.asTypeName(),
      requestType,
      responseType
    )
    .build()

  val flowType = EventBusServiceRequest::class.asTypeName().parameterizedBy(requestType, responseType)
  return PropertySpec.builder(propertyName, Flow::class.asTypeName().parameterizedBy(flowType))
    .receiver(Vertx::class)
    .getter(getter)
    .build()
}

internal fun generateRequestFunction(
  requestType: ClassName,
  responseType: ClassName,
  functionName: String
): FunSpec {
  return FunSpec.builder(functionName)
    .addModifiers(KModifier.SUSPEND)
    .addParameter("request", requestType)
    .receiver(Vertx::class)
    .returns(responseType)
    .addCode(
      """
      return eventBus()
        .request<%T>(TOPIC, request, %M)
        .%M()
        .body()
    """.trimIndent(),
      responseType,
      MemberName("co.selim.ebservice.core", "deliveryOptions"),
      MemberName("io.vertx.kotlin.coroutines", "await")
    )
    .build()
}
