package co.selim.ebservice.annotation

import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class EventBusService(
  val topic: String,
  val consumes: KClass<*>,
  val produces: KClass<*>,
  val propertyName: String,
  val functionName: String
)
