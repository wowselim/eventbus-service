package co.selim.ebservice.codegen

import co.selim.ebservice.core.EventBusService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

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
        val serviceName = classDeclaration.qualifiedName?.asString() ?: return@forEach

        val fileContent = generateServiceFile(serviceName, functions, annotation.propertyVisibility)

        val dependencies = Dependencies(true, classDeclaration.containingFile!!)
        codeGenerator.createNewFile(
          dependencies,
          classDeclaration.packageName.asString(),
          "${classDeclaration.simpleName.asString()}Impl"
        )
          .bufferedWriter()
          .use { writer ->
            writer.write(fileContent)
          }
      }

    return emptyList()
  }

  private fun KSClassDeclaration.extractFunctions(): Sequence<Function> {
    return getDeclaredFunctions()
      .map { function ->
        val returnType = function.returnType!!.resolve().asTypeString()

        if (returnType != "kotlin.Unit" && Modifier.SUSPEND !in function.modifiers) {
          logger.error("Function ${function.simpleName} must be suspending")
        }

        if (returnType == "kotlin.Unit" && Modifier.SUSPEND in function.modifiers) {
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
          returnType,
          parameters,
          Modifier.SUSPEND in function.modifiers
        )
      }
  }

  private fun Sequence<KSValueParameter>.toFunctionParameters(): Sequence<Parameter> {
    return map { valueParameter ->
      val type = valueParameter.type.resolve().asTypeString()
      Parameter(valueParameter.name!!.asString(), type)
    }
  }

  private fun KSType.asTypeString(): String = buildString {
    append(declaration.qualifiedName?.asString() ?: declaration.simpleName.asString())
    if (arguments.isNotEmpty()) {
      append('<')
      append(arguments.joinToString(", ") { argument ->
        argument.type?.resolve()?.asTypeString() ?: "*"
      })
      append('>')
    }
    if (isMarkedNullable) append('?')
  }
}
