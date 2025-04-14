package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.Visibility
import co.selim.ebservice.core.EventBusServiceRequest
import co.selim.ebservice.core.EventBusServiceRequestImpl
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.vertx.core.Vertx
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.*
import javax.annotation.processing.Generated

internal fun generateFile(
  packageName: String,
  simpleName: String,
): FileSpec.Builder {
  val topicProperty = PropertySpec.builder(
    "TOPIC", String::class, KModifier.PRIVATE, KModifier.CONST
  )
    .initializer("%S", "$packageName.${simpleName.lowercase(Locale.getDefault())}")
    .build()

  return FileSpec.builder(packageName, simpleName + "Impl")
    .addAnnotation(
      AnnotationSpec.builder(Suppress::class)
        .addMember("%S", "RedundantVisibilityModifier")
        .build()
    )
    .addProperty(topicProperty)
}

internal fun generateRequestFunctions(
  serviceName: ClassName,
  functions: Sequence<Function>,
  visibility: Visibility,
): Sequence<FunSpec> {
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
    generateRequestsFunction(
      function.name,
      requestType,
      function.returnType,
      visibility,
    )
  }
}

private fun generateRequestsFunction(
  functionName: String,
  requestType: TypeName,
  responseType: TypeName,
  visibility: Visibility,
): FunSpec {
  val isOneWayRequest = responseType == Unit::class.asTypeName()

  val flowType = if (isOneWayRequest) {
    requestType
  } else {
    EventBusServiceRequest::class.asTypeName().parameterizedBy(requestType, responseType)
  }
  val function = FunSpec.builder(functionName)
    .returns(Flow::class.asTypeName().parameterizedBy(flowType))
    .addParameter("vertx", Vertx::class)
    .apply {
      if (visibility == Visibility.INTERNAL) {
        addModifiers(KModifier.INTERNAL)
      }
    }

  val body = CodeBlock.builder()
    .apply {
      if (isOneWayRequest) {
        add(
          """
            return vertx.eventBus()
              .consumer<%T>("${'\$'}TOPIC.${functionName}")
              .%M(vertx)
              .%M()
              .%M { it.body() }
          """.trimIndent(),
          requestType,
          MemberName("io.vertx.kotlin.coroutines", "toReceiveChannel"),
          MemberName("kotlinx.coroutines.flow", "receiveAsFlow"),
          MemberName("kotlinx.coroutines.flow", "map"),
        )
      } else {
        add(
          """
            return vertx.eventBus()
              .consumer<%T>("${'\$'}TOPIC.${functionName}")
              .%M(vertx)
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
      }
    }
    .build()

  return function.addCode(body).build()
}

internal fun generateServiceImpl(serviceClassName: ClassName): TypeSpec.Builder {
  val generatedAnnotation = AnnotationSpec.builder(Generated::class)
    .addMember("%S", ServiceProcessor::class.java.name)
    .addMember("date = %S", Instant.now().toString())
    .build()

  return TypeSpec.classBuilder(serviceClassName.peerClass(serviceClassName.simpleName + "Impl"))
    .addAnnotation(generatedAnnotation)
    .addSuperinterface(serviceClassName)
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

internal fun generateServiceRequestsClass(
  serviceClassName: ClassName,
  visibility: Visibility
): TypeSpec.Builder {

  return TypeSpec.objectBuilder(serviceClassName.peerClass(serviceClassName.simpleName + "Requests"))
    .apply {
      if (visibility == Visibility.INTERNAL) {
        addModifiers(KModifier.INTERNAL)
      }
    }
}

internal fun generateFunctions(
  fileSpec: FileSpec.Builder,
  serviceSpec: TypeSpec.Builder,
  functions: Sequence<Function>
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


private fun generateFunction(function: Function, containerType: String?): FunSpec {
  val message: String = containerType?.let { type ->
    val params = function.parameters.joinToString { it.name }
    "$type($params)"
  } ?: function.parameters.map { it.name }.firstOrNull() ?: "Unit"
  return FunSpec.builder(function.name)
    .addModifiers(KModifier.OVERRIDE)
    .apply { if (function.isSuspend) addModifiers(KModifier.SUSPEND) }
    .addParameters(
      function.parameters.map { parameter ->
        ParameterSpec(parameter.name, parameter.type)
      }
    )
    .returns(function.returnType)
    .apply {
      if (function.returnType != Unit::class.asTypeName()) {
        addCode(
          """
          return vertx.eventBus()
            .request<%T>("${'\$'}TOPIC.${function.name}", $message, %M)
            .%M()
            .body()
          """.trimIndent(),
          function.returnType,
          MemberName("co.selim.ebservice.core", "deliveryOptions"),
          MemberName("io.vertx.kotlin.coroutines", "coAwait"),
        )
      } else {
        addCode(
          """
          vertx.eventBus()
            .send("${'\$'}TOPIC.${function.name}", $message, %M)
          """.trimIndent(),
          MemberName("co.selim.ebservice.core", "deliveryOptions"),
        )
      }
    }
    .build()
}
