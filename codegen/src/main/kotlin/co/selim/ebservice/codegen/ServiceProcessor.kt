package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.EventBusService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.metadata.*
import kotlinx.metadata.KmClassifier
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic

@KotlinPoetMetadataPreview
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("kapt.kotlin.generated")
@SupportedAnnotationTypes("co.selim.ebservice.annotation.EventBusService")
class ServiceProcessor : AbstractProcessor() {
  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val elements = roundEnv.getElementsAnnotatedWith(EventBusService::class.java)
    if (elements.isEmpty()) return false

    val typeElements = ElementFilter.typesIn(elements)

    val services = typeElements.map { typeElement ->
      val kmClass = typeElement.toImmutableKmClass()
      if (!kmClass.isInterface) {
        logError("Only interfaces are supported by ebservice", typeElement)
        error("Only interfaces are supported by ebservice")
      }

      val functions = kmClass.functions
        .asSequence()
        .filter { kmFunction ->
          val classifier = kmFunction.returnType.classifier
          classifier is KmClassifier.Class || classifier is KmClassifier.TypeAlias
        }
        .extractFunctions()
        .toSet()

      Service(kmClass.name.toClassName(), functions)
    }

    services.forEach { service ->
      val fileSpecBuilder = generateFile(service.name)
      fileSpecBuilder.addType(
        generateServiceImpl(service)
          .apply { generateFunctions(fileSpecBuilder, this, service.functions) }
          .build()
      )
      generateRequestProperties(service.name, service.functions).forEach(fileSpecBuilder::addProperty)
      fileSpecBuilder.build()
        .writeTo(processingEnv.filer)
    }

    return true
  }

  private fun Sequence<ImmutableKmFunction>.extractFunctions(): Sequence<Function> {
    return map { kmFunction ->
      if (!kmFunction.isSuspend) {
        logError("Function ${kmFunction.name} must be suspending")
        error("Function ${kmFunction.name} must be suspending")
      }

      val typeParameters = kmFunction.returnType.getTypeParameters()
      val returnType = kmFunction.returnType.classifier
        .toClassName(kmFunction.returnType.isNullable)
        .safelyParameterizedBy(typeParameters)

      val parameters = kmFunction.valueParameters
        .asSequence()
        .onEach { parameter ->
          if (parameter.varargElementType != null) {
            logError("Vararg parameters are not supported by ebservice")
            error("Vararg parameters are not supported by ebservice")
          }
        }
        .filter { parameter ->
          val classifier = parameter.type!!.classifier
          classifier is KmClassifier.Class || classifier is KmClassifier.TypeAlias
        }
        .toFunctionParameters()
        .toSet()

      Function(kmFunction.name, returnType, parameters)
    }
  }

  private fun Sequence<ImmutableKmValueParameter>.toFunctionParameters(): Sequence<Parameter> {
    return map { kmValueParameter ->
      val type = kmValueParameter.type!!
      val classifier = type.classifier
      val typeParametersOfParameter = type.getTypeParameters()
      val typeOfParameter = classifier.toClassName(type.isNullable).safelyParameterizedBy(typeParametersOfParameter)
      Parameter(kmValueParameter.name, typeOfParameter)
    }
  }

  private fun logError(msg: String, element: Element? = null) {
    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg, element)
  }

  private fun ImmutableKmType.getTypeParameters(): List<TypeName> {
    return arguments.mapNotNull { typeProjection ->
      val params = typeProjection.type?.getTypeParameters()

      when (val classifier = typeProjection.type?.classifier) {
        is KmClassifier.Class -> classifier.toClassName(typeProjection.type!!.isNullable).safelyParameterizedBy(params)
        is KmClassifier.TypeParameter,
        is KmClassifier.TypeAlias,
        null -> null
      }
    }
  }

  private fun ClassName.safelyParameterizedBy(typeNames: List<TypeName>?): TypeName {
    return if (typeNames.isNullOrEmpty()) {
      this
    } else {
      this.parameterizedBy(typeNames)
    }
  }

  private fun KmClassifier.toClassName(nullable: Boolean): ClassName {
    return when (this) {
      is KmClassifier.Class -> name.toClassName().copy(nullable = nullable) as ClassName
      is KmClassifier.TypeAlias -> name.toClassName().copy(nullable = nullable) as ClassName
      is KmClassifier.TypeParameter -> error("Type parameters are not supported")
    }
  }

  private fun String.toClassName(): ClassName {
    return ClassName(substringBeforeLast('/').replace('/', '.'), substringAfterLast('/'))
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(EventBusService::class.java.name)
  }
}
