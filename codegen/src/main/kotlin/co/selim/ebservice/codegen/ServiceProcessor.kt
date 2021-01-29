package co.selim.ebservice.codegen

import co.selim.ebservice.annotation.EventBusService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.type.MirroredTypeException
import javax.lang.model.util.ElementFilter

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("kapt.kotlin.generated")
@SupportedAnnotationTypes("co.selim.ebservice.annotation.EventBusService")
class ServiceProcessor : AbstractProcessor() {
  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val elements = roundEnv.getElementsAnnotatedWith(EventBusService::class.java)
    if (elements.isEmpty()) return false

    val typeElements = ElementFilter.typesIn(elements)
    val methodElements = ElementFilter.methodsIn(elements)

    (typeElements + methodElements).forEach { element ->
      element.getAnnotation(EventBusService::class.java)?.let { service ->
        val consumedType = service.extractConsumedType()
        val producedType = service.extractProducedType()

        val sourceFile = generateServiceExtensions(
          service.topic,
          consumedType,
          producedType,
          service.propertyName,
          service.functionName
        )
        sourceFile.writeTo(processingEnv.filer)
      }
    }

    return true
  }

  private fun EventBusService.extractConsumedType(): ClassName {
    return extractClass { ClassName.bestGuess(consumes.java.name) }
  }

  private fun EventBusService.extractProducedType(): ClassName {
    return extractClass { ClassName.bestGuess(produces.java.name) }
  }

  private fun extractClass(block: () -> ClassName): ClassName {
    return try {
      block()
    } catch (e: MirroredTypeException) {
      val typeElement = processingEnv.typeUtils.asElement(e.typeMirror) as TypeElement
      val qualifiedName = typeElement.qualifiedName.toString()
      ClassName.bestGuess(qualifiedName)
    }
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(EventBusService::class.java.name)
  }

  private fun generateServiceExtensions(
    topic: String,
    consumedType: ClassName,
    producedType: ClassName,
    propertyName: String,
    functionName: String
  ): FileSpec {
    return generateFile(topic)
      .addProperty(generateRequestsProperty(consumedType, producedType, propertyName))
      .addFunction(generateRequestFunction(consumedType, producedType, functionName))
      .build()
  }
}
