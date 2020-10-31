package co.selim.ebservice.eventbus

import io.vertx.core.eventbus.Message

interface EventBusServiceRequest<RequestType, ResponseType> {
  val request: RequestType
  fun reply(response: ResponseType)
}

class EventBusServiceRequestImpl<RequestType, ResponseType>(
  private val message: Message<RequestType>
) : EventBusServiceRequest<RequestType, ResponseType> {
  override val request: RequestType = message.body()

  override fun reply(response: ResponseType) {
    message.reply(response, deliveryOptions)
  }
}
