package co.selim.ebservice.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class EventBusService(
  val propertyVisibility: Visibility = Visibility.INTERNAL
)

enum class Visibility {
  PUBLIC, INTERNAL
}
