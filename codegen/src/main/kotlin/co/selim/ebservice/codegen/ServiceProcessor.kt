package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.EventBusService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName

class ServiceProcessor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
) : SymbolProcessor {

  @OptIn(KspExperimental::class)
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver.getSymbolsWithAnnotation(EventBusService::class.java.name)
      .filterIsInstance<KSClassDeclaration>()
      .forEach { ksClassDeclaration ->
        if (ksClassDeclaration.classKind != ClassKind.INTERFACE) {
          logError("Only interfaces are supported by ebservice", ksClassDeclaration)
        }
        val annotatedClass = ksClassDeclaration.qualifiedName!!.asString()

        val functions = ksClassDeclaration.extractFunctions()

        val annotation = ksClassDeclaration.getAnnotationsByType(EventBusService::class).first()
        val dependencies = Dependencies(true, ksClassDeclaration.containingFile!!)

        val serviceClassName = ClassName.bestGuess(annotatedClass)
        val fileSpecBuilder = generateFile(serviceClassName.packageName, serviceClassName.simpleName)
        fileSpecBuilder.addType(
          generateServiceImpl(serviceClassName)
            .apply { generateFunctions(fileSpecBuilder, this, functions) }
            .build()
        )
        generateRequestProperties(serviceClassName, functions, annotation.propertyVisibility)
          .forEach(fileSpecBuilder::addProperty)

        val fileSpec = fileSpecBuilder.build()

        codeGenerator.createNewFile(
          dependencies,
          ksClassDeclaration.packageName.asString(),
          "${ksClassDeclaration.simpleName.asString()}Impl"
        )
          .bufferedWriter()
          .use { writer ->
            fileSpec.writeTo(writer)
          }
      }

    return emptyList()
  }

  private fun KSClassDeclaration.extractFunctions(): Sequence<Function> {
    return getDeclaredFunctions()
      .map { function ->
        val returnType = function.returnType!!.resolve().getFullType()

        if (returnType != Unit::class.asTypeName() && Modifier.SUSPEND !in function.modifiers) {
          logError("Function ${function.simpleName} must be suspending")
        }

        if (returnType == Unit::class.asTypeName() && Modifier.SUSPEND in function.modifiers) {
          logInfo("Function ${function.simpleName} doesn't need to be suspending")
        }

        val parameters = function.parameters
          .asSequence()
          .onEach { parameter ->
            if (parameter.isVararg) {
              logError("Vararg parameter ${parameter.name} in function ${function.simpleName} are not supported by ebservice")
            }
          }
          .toFunctionParameters()
          .toSet()

        Function(
          function.simpleName.asString(),
          function.returnType!!.resolve().getFullType(),
          parameters,
          Modifier.SUSPEND in function.modifiers
        )
      }
  }

  private fun Sequence<KSValueParameter>.toFunctionParameters(): Sequence<Parameter> {
    return map { kmValueParameter ->
      val type = kmValueParameter.type
      Parameter(kmValueParameter.name!!.asString(), type.resolve().getFullType())
    }
  }

  private fun logError(msg: String, element: KSNode? = null) {
    logger.error(msg, element)
  }

  private fun logInfo(msg: String, element: KSNode? = null) {
    logger.info(msg, element)
  }

  private fun KSType.getFullType(): TypeName {
    val typeParams = arguments.mapNotNull { argument ->
      val resolvedType = argument.type?.resolve()

      resolvedType?.getFullType()?.copy(nullable = resolvedType.isMarkedNullable)
    }

    val fullName = declaration.qualifiedName!!.asString()
    val simpleName = fullName.substringAfter(declaration.packageName.asString())
    return ClassName(declaration.packageName.asString(), simpleName)
      .safelyParameterizedBy(typeParams)
      .copy(nullable = isMarkedNullable)
  }


  private fun ClassName.safelyParameterizedBy(typeNames: List<TypeName>?): TypeName {
    return if (typeNames.isNullOrEmpty()) {
      this
    } else {
      this.parameterizedBy(typeNames)
    }
  }
}
