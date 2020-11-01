package co.selim.ebservice

import co.selim.ebservice.annotation.EventBusService
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
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
    val elements = roundEnv.getElementsAnnotatedWithAny(
      setOf(EventBusService::class.java)
    )
    if (elements.isEmpty()) return false

    val typeElements = ElementFilter.typesIn(elements)

    typeElements.forEach { typeElement ->
      typeElement.getAnnotation(EventBusService::class.java)?.let { service ->
        val sourceFilePath = Paths.get(processingEnv.options["kapt.kotlin.generated"]!!)
          .resolve(service.topic.replace(".", File.separator))
          .resolve("Service.kt")
        Files.deleteIfExists(sourceFilePath)
        Files.createDirectories(sourceFilePath.parent)

        val consumedType = service.extractConsumedType()
        val producedType = service.extractProducedType()
        Files.writeString(
          sourceFilePath,
          generateServiceExtensions(
            service.topic,
            consumedType,
            producedType,
            service.propertyName,
            service.functionName
          )
        )
      }
    }

    return true
  }

  private fun EventBusService.extractConsumedType(): Pair<String, String> {
    return extractClass { consumes.java.simpleName to consumes.java.name }
  }

  private fun EventBusService.extractProducedType(): Pair<String, String> {
    return extractClass { produces.java.simpleName to produces.java.name }
  }

  private fun extractClass(block: () -> Pair<String, String>): Pair<String, String> {
    return try {
      block()
    } catch (e: MirroredTypeException) {
      val typeElement = processingEnv.typeUtils.asElement(e.typeMirror) as TypeElement
      typeElement.simpleName.toString() to typeElement.qualifiedName.toString()
    }
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(EventBusService::class.java.name)
  }

  private fun generateServiceExtensions(
    topic: String,
    consumedType: Pair<String, String>,
    producedType: Pair<String, String>,
    propertyName: String,
    functionName: String
  ): String {
    return buildString {
      appendLine(generateHeader(topic, consumedType.second, producedType.second))
      appendLine()
      appendLine(generateRequestsProperty(consumedType.first, producedType.first, propertyName))
      appendLine()
      appendLine(generateRequestFunction(consumedType.first, producedType.first, functionName))
    }
  }
}
