package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.EventBusService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

class ServiceProcessor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator
) : SymbolProcessor {

  @OptIn(KspExperimental::class)
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver.getSymbolsWithAnnotation(EventBusService::class.java.name)
      .filterIsInstance<KSClassDeclaration>()
      .forEach { classDeclaration ->
        if (classDeclaration.classKind != ClassKind.INTERFACE) {
          logger.error("Only interfaces are supported by ebservice", classDeclaration)
        }

        val functions = classDeclaration.extractFunctions()
        val annotation = classDeclaration.getAnnotationsByType(EventBusService::class).first()


        val serviceClassName = classDeclaration.toClassName()
        val fileSpecBuilder = generateFile(serviceClassName.packageName, serviceClassName.simpleName)
        fileSpecBuilder.addType(
          generateServiceImpl(serviceClassName)
            .apply { generateFunctions(fileSpecBuilder, this, functions) }
            .build()
        )
        generateRequestProperties(serviceClassName, functions, annotation.propertyVisibility)
          .forEach(fileSpecBuilder::addProperty)

        val fileSpec = fileSpecBuilder.build()

        val dependencies = Dependencies(true, classDeclaration.containingFile!!)
        codeGenerator.createNewFile(
          dependencies,
          classDeclaration.packageName.asString(),
          "${classDeclaration.simpleName.asString()}Impl"
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
        val returnType = function.returnType!!.resolve().toTypeName()

        if (returnType != Unit::class.asTypeName() && Modifier.SUSPEND !in function.modifiers) {
          logger.error("Function ${function.simpleName} must be suspending")
        }

        if (returnType == Unit::class.asTypeName() && Modifier.SUSPEND in function.modifiers) {
          logger.info("Function ${function.simpleName} doesn't need to be suspending")
        }

        val parameters = function.parameters
          .asSequence()
          .onEach { parameter ->
            if (parameter.isVararg) {
              logger.error("Vararg parameter ${parameter.name} in function ${function.simpleName} are not supported by ebservice")
            }
          }
          .toFunctionParameters()
          .toSet()

        Function(
          function.simpleName.asString(),
          function.returnType!!.resolve().toTypeName(),
          parameters,
          Modifier.SUSPEND in function.modifiers
        )
      }
  }

  private fun Sequence<KSValueParameter>.toFunctionParameters(): Sequence<Parameter> {
    return map { valueParameter ->
      val type = valueParameter.type
      Parameter(valueParameter.name!!.asString(), type.resolve().toTypeName())
    }
  }
}
