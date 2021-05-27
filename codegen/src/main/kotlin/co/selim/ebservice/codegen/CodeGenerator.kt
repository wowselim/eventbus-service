package co.selim.ebservice.codegen

import co.selim.ebservice.core.EventBusServiceRequest
import co.selim.ebservice.core.EventBusServiceRequestImpl
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.vertx.core.Vertx
import kotlinx.coroutines.flow.Flow

internal fun generateFile(
  packageName: String,
  serviceName: String,
): FileSpec.Builder {
  val topicProperty = PropertySpec.builder("TOPIC", String::class, KModifier.PRIVATE, KModifier.CONST)
    .initializer("%S", "${packageName}.${serviceName.toLowerCase()}")
    .build()

  return FileSpec.builder(packageName, serviceName + "Impl")
    .addProperty(topicProperty)
}

internal fun generateRequestProperties(
  packageName: String,
  functions: List<Function>
): Iterable<PropertySpec> {
  return functions.map { function ->
    val requestType: TypeName = when (function.parameters.size) {
      0 -> Unit::class.asTypeName()
      1 -> function.parameters.first().type
      else -> ClassName(packageName, function.name.capitalize() + "Parameters")
    }
    generateRequestsProperty(
      functionName = function.name,
      requestType = requestType,
      responseType = function.returnType,
      propertyName = function.name + "Requests"
    )
  }
}

private fun generateRequestsProperty(
  functionName: String,
  requestType: TypeName,
  responseType: TypeName,
  propertyName: String
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

internal fun generateServiceImpl(
  service: Service
): TypeSpec.Builder {
  return TypeSpec.classBuilder(service.name + "Impl")
    .addSuperinterface(ClassName(service.packageName, service.name))
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

internal fun generateFunctions(
  fileSpec: FileSpec.Builder,
  serviceSpec: TypeSpec.Builder,
  functions: List<Function>
) {
  functions.forEach { function ->
    val container = if (function.parameters.size > 1) {
      generateParameterContainer(function.name, function.parameters)
    } else null
    container?.let(fileSpec::addType)
    serviceSpec.addFunction(generateFunction(function, container?.name))
  }
}

private fun generateParameterContainer(
  functionName: String,
  parameters: List<Parameter>
): TypeSpec {
  return TypeSpec.classBuilder(functionName.capitalize() + "Parameters")
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

private fun generateFunction(function: Function, containerType: String?): FunSpec {
  val message: String = containerType?.let { type ->
    val params = function.parameters.joinToString { it.name }
    "$type($params)"
  } ?: function.parameters.map { it.name }.firstOrNull() ?: "Unit"
  val returnType = function.returnType
  val isReturnTypeUnit = returnType == Unit::class.asTypeName()

  return FunSpec.builder(function.name)
    .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
    .addParameters(
      function.parameters.map { parameter ->
        ParameterSpec(parameter.name, parameter.type)
      }
    )
    .returns(returnType)
    .apply {
      val deliveryOptions = MemberName("co.selim.ebservice.core", "deliveryOptions")
      if (isReturnTypeUnit) {
        addCode(
          """
          |vertx.eventBus()
          |  .send(TOPIC + ".${function.name}", $message, %M)
          |""".trimMargin(),
          deliveryOptions
        )
      } else {
        addCode(
          """
          |return vertx.eventBus()
          |  .request<%T>(TOPIC + ".${function.name}", $message, %M)
          |  .%M()
          |  .body()
          |""".trimMargin(),
          returnType,
          deliveryOptions,
          MemberName("io.vertx.kotlin.coroutines", "await")
        )
      }
    }
    .build()
}
