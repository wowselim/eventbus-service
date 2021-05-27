package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.EventBusService
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

class EventBusServiceProcessor(
  private val logger: KSPLogger,
  private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver.getSymbolsWithAnnotation(EventBusService::class.java.name)
      .filterIsInstance<KSClassDeclaration>()
      .filter { classDeclaration ->
        val isInterface = classDeclaration.classKind == ClassKind.INTERFACE
        if (!isInterface) {
          logger.error("Only interfaces are supported", classDeclaration)
        }
        isInterface
      }
      .forEach { classDeclaration ->
        val functions = classDeclaration.getDeclaredFunctions()
          .map { functionDeclaration ->
            Function(
              functionDeclaration.simpleName.asString(),
              functionDeclaration.returnType!!.resolve().getFullType(),
              functionDeclaration.parameters
                .map { parameter ->
                  Parameter(parameter.name!!.asString(), parameter.type.resolve().getFullType())
                }
            )
          }.toList()
        val dependencies = Dependencies(true, classDeclaration.containingFile!!)
        val packageName = classDeclaration.packageName.asString()
        val serviceName = classDeclaration.simpleName.asString()
        codeGenerator.createNewFile(dependencies, packageName, serviceName + "Impl")
          .writer()
          .use { writer ->
            val fileSpecBuilder = generateFile(packageName, classDeclaration.simpleName.asString())
            fileSpecBuilder
              .addType(
                generateServiceImpl(Service(packageName, serviceName, emptySet()))
                  .apply { generateFunctions(fileSpecBuilder, this, functions) }
                  .build()
              )
            generateRequestProperties(packageName, functions).forEach(fileSpecBuilder::addProperty)
            fileSpecBuilder.build()
              .writeTo(writer)
          }
      }

    return emptyList()
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
