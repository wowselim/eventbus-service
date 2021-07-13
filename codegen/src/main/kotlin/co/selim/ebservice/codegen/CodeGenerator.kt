package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.Visibility
import co.selim.ebservice.core.EventBusServiceRequest
import co.selim.ebservice.core.EventBusServiceRequestImpl
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import io.vertx.core.Vertx
import kotlinx.coroutines.flow.Flow
import java.util.*

internal fun generateFile(
  serviceName: ClassName
): FileSpec.Builder {
  val topicProperty = PropertySpec.builder(
    "TOPIC", String::class, KModifier.PRIVATE, KModifier.CONST
  )
    .initializer("%S", "${serviceName.packageName}.${serviceName.simpleName.lowercase(Locale.getDefault())}")
    .build()

  return FileSpec.builder(serviceName.packageName, serviceName.simpleName + "Impl")
    .addProperty(topicProperty)
}

@KotlinPoetMetadataPreview
internal fun generateRequestProperties(
  serviceName: ClassName,
  functions: Set<Function>,
  visibility: Visibility,
): Iterable<PropertySpec> {
  return functions.map { function ->
    val requestType = when (function.parameters.size) {
      0 -> Unit::class.asTypeName()
      1 -> function.parameters.first().type
      else -> {
        val capitalizedName = function.name.replaceFirstChar {
          it.titlecase(Locale.getDefault())
        }
        ClassName(serviceName.packageName, capitalizedName + "Parameters")
      }
    }
    generateRequestsProperty(
      function.name,
      requestType,
      function.returnType,
      function.name + "Requests",
      visibility,
    )
  }
}

private fun generateRequestsProperty(
  functionName: String,
  requestType: TypeName,
  responseType: TypeName,
  propertyName: String,
  visibility: Visibility,
): PropertySpec {
  val getter = FunSpec.getterBuilder()
    .addCode(
      """
      return eventBus()
        .localConsumer<%T>(TOPIC + ".${functionName}")
        .%M(this)
        .%M()
        .%M { %T<%T, %T>(it) }
    """.trimIndent(),
      requestType,
      MemberName("io.vertx.kotlin.coroutines", "toReceiveChannel"),
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
    .apply {
      if (visibility == Visibility.INTERNAL) {
        addModifiers(KModifier.INTERNAL)
      }
    }
    .getter(getter)
    .build()
}

@KotlinPoetMetadataPreview
internal fun generateServiceImpl(
  service: Service
): TypeSpec.Builder {
  return TypeSpec.classBuilder(service.name.peerClass(service.name.simpleName + "Impl"))
    .addSuperinterface(service.name)
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameter("vertx", Vertx::class)
        .build()
    ).addProperty(
      PropertySpec.builder("vertx", Vertx::class, KModifier.PRIVATE)
        .initializer("vertx")
        .build()
    )
}

@KotlinPoetMetadataPreview
internal fun generateFunctions(
  fileSpec: FileSpec.Builder,
  serviceSpec: TypeSpec.Builder,
  functions: Set<Function>
) {
  functions.forEach { function ->
    val container = if (function.parameters.size > 1) {
      generateParameterContainer(function.name, function.parameters)
    } else null
    container?.let(fileSpec::addType)
    serviceSpec.addFunction(generateFunction(function, container?.name))
  }
}

@KotlinPoetMetadataPreview
private fun generateParameterContainer(
  functionName: String,
  parameters: Set<Parameter>
): TypeSpec {
  val capitalizedFunctionName = functionName.replaceFirstChar { it.titlecase(Locale.ROOT) }
  return TypeSpec.classBuilder(capitalizedFunctionName + "Parameters")
    .addModifiers(KModifier.DATA)
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameters(
          parameters.map { parameter ->
            ParameterSpec(parameter.name, parameter.type)
          }
        )
        .build()
    ).addProperties(
      parameters.map { parameter ->
        PropertySpec.builder(parameter.name, parameter.type)
          .initializer(parameter.name)
          .build()
      }
    )
    .build()
}


@KotlinPoetMetadataPreview
private fun generateFunction(function: Function, containerType: String?): FunSpec {
  val message: String = containerType?.let { type ->
    val params = function.parameters.joinToString { it.name }
    "$type($params)"
  } ?: function.parameters.map { it.name }.firstOrNull() ?: "Unit"
  return FunSpec.builder(function.name)
    .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
    .addParameters(
      function.parameters.map { parameter ->
        ParameterSpec(parameter.name, parameter.type)
      }
    )
    .returns(function.returnType)
    .addCode(
      """
      return vertx.eventBus()
        .request<%T>(TOPIC + ".${function.name}", $message, %M)
        .%M()
        .body()
    """.trimIndent(),
      function.returnType,
      MemberName("co.selim.ebservice.core", "deliveryOptions"),
      MemberName("io.vertx.kotlin.coroutines", "await")
    )
    .build()
}
