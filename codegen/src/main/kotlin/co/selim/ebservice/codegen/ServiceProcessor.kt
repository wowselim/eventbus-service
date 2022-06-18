package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.EventBusService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.metadata.*
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic

@KotlinPoetMetadataPreview
@SupportedOptions("kapt.kotlin.generated")
@SupportedAnnotationTypes("co.selim.ebservice.annotation.EventBusService")
class ServiceProcessor : AbstractProcessor() {
  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val elements = roundEnv.getElementsAnnotatedWith(EventBusService::class.java)
    if (elements.isEmpty()) return false

    val typeElements = ElementFilter.typesIn(elements)

    val services = typeElements.map { typeElement ->
      val kmClass = typeElement.toKmClass()
      if (!kmClass.isInterface) {
        logError("Only interfaces are supported by ebservice", typeElement)
      }

      val functions = kmClass.functions
        .asSequence()
        .filter { kmFunction ->
          val classifier = kmFunction.returnType.classifier
          classifier is KmClassifier.Class || classifier is KmClassifier.TypeAlias
        }
        .extractFunctions()
        .toSet()

      val annotation = typeElement.getAnnotation(EventBusService::class.java)
      Service(kmClass.name.toClassName(), functions, annotation.propertyVisibility)
    }

    services.forEach { service ->
      val fileSpecBuilder = generateFile(service.name)
      fileSpecBuilder.addType(
        generateServiceImpl(service)
          .apply { generateFunctions(fileSpecBuilder, this, service.functions) }
          .build()
      )
      generateRequestProperties(service.name, service.functions, service.propertyVisibility)
        .forEach(fileSpecBuilder::addProperty)
      fileSpecBuilder.build()
        .writeTo(processingEnv.filer)
    }

    return true
  }

  private fun Sequence<KmFunction>.extractFunctions(): Sequence<Function> {
    return map { kmFunction ->
      if (kmFunction.returnType.toTypeName() != Unit::class.asTypeName() && !kmFunction.isSuspend) {
        logError("Function ${kmFunction.name} must be suspending")
      }

      val parameters = kmFunction.valueParameters
        .asSequence()
        .onEach { parameter ->
          if (parameter.varargElementType != null) {
            logError("Vararg parameters are not supported by ebservice")
          }
        }
        .filter { parameter ->
          val classifier = parameter.type.classifier
          classifier is KmClassifier.Class || classifier is KmClassifier.TypeAlias
        }
        .toFunctionParameters()
        .toSet()

      Function(kmFunction.name, kmFunction.returnType.toTypeName(), parameters, kmFunction.isSuspend)
    }
  }

  private fun Sequence<KmValueParameter>.toFunctionParameters(): Sequence<Parameter> {
    return map { kmValueParameter ->
      val type = kmValueParameter.type
      Parameter(kmValueParameter.name, type.toTypeName())
    }
  }

  private fun logError(msg: String, element: Element? = null) {
    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg, element)
  }

  private fun KmType.toTypeName(): TypeName {
    val params: List<TypeName> = arguments.mapNotNull { typeProjection ->
      typeProjection.type?.toTypeName()?.copy(nullable = typeProjection.type!!.isNullable)
    }

    return when (classifier) {
      is KmClassifier.Class -> {
        classifier.toClassName().safelyParameterizedBy(params).copy(nullable = isNullable)
      }
      is KmClassifier.TypeAlias -> {
        classifier.toClassName().safelyParameterizedBy(params).copy(nullable = isNullable)
      }
      is KmClassifier.TypeParameter -> error("Type parameters are not supported")
    }
  }

  private fun ClassName.safelyParameterizedBy(typeNames: List<TypeName>?): TypeName {
    return if (typeNames.isNullOrEmpty()) {
      this
    } else {
      this.parameterizedBy(typeNames)
    }
  }

  private fun KmClassifier.toClassName(): ClassName {
    return when (this) {
      is KmClassifier.Class -> name.toClassName()
      is KmClassifier.TypeAlias -> name.toClassName()
      is KmClassifier.TypeParameter -> error("Type parameters are not supported")
    }
  }

  private fun String.toClassName(): ClassName {
    return ClassName(substringBeforeLast('/').replace('/', '.'), substringAfterLast('/'))
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(EventBusService::class.java.name)
  }

  override fun getSupportedSourceVersion(): SourceVersion {
    return SourceVersion.latestSupported()
  }
}
