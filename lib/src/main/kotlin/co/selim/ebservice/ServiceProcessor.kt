package co.selim.ebservice

import co.selim.ebservice.annotation.EventBusService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
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
        val sourceFilePath = getSourceFilePath(service)
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

  private fun getSourceFilePath(service: EventBusService): Path {
    return Paths.get(processingEnv.options["kapt.kotlin.generated"]!!)
      .resolve(service.topic.replace(".", File.separator))
      .resolve("ServiceExtensions.kt")
  }

  private fun EventBusService.extractConsumedType(): ClassName {
    return extractClass { ClassName(consumes.java.simpleName, consumes.java.name) }
  }

  private fun EventBusService.extractProducedType(): ClassName {
    return extractClass { ClassName(produces.java.simpleName, produces.java.name) }
  }

  private fun extractClass(block: () -> ClassName): ClassName {
    return try {
      block()
    } catch (e: MirroredTypeException) {
      val typeElement = processingEnv.typeUtils.asElement(e.typeMirror) as TypeElement
      ClassName(typeElement.simpleName.toString(), typeElement.qualifiedName.toString())
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
  ): String {
    return buildString {
      appendLine(generateHeader(topic, consumedType.qualifiedName, producedType.qualifiedName))
      appendLine()
      appendLine(generateRequestsProperty(consumedType.simpleName, producedType.simpleName, propertyName))
      appendLine()
      appendLine(generateRequestFunction(consumedType.simpleName, producedType.simpleName, functionName))
    }
  }
}

private data class ClassName(
  val simpleName: String,
  val qualifiedName: String
)
