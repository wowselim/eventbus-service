package co.selim.ebservice.eventbus

import io.vertx.core.eventbus.Message

interface EventBusServiceRequest<RequestType, ResponseType> {
  val body: RequestType
  fun reply(response: ResponseType)
}

class EventBusServiceRequestImpl<RequestType, ResponseType>(
  private val message: Message<RequestType>
) : EventBusServiceRequest<RequestType, ResponseType> {
  override val body: RequestType = message.body()

  override fun reply(response: ResponseType) {
    message.reply(response, deliveryOptions)
  }
}
