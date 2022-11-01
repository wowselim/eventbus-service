package co.selim.ebservice.core

import io.vertx.core.eventbus.Message

interface EventBusServiceRequest<RequestType, ResponseType> {
  val body: RequestType
  fun reply(response: ResponseType)
  operator fun component1(): RequestType = body
  operator fun component2(): (ResponseType) -> Unit = ::reply
}

@JvmInline
value class EventBusServiceRequestImpl<RequestType, ResponseType>(
  private val message: Message<RequestType>
) : EventBusServiceRequest<RequestType, ResponseType> {
  override val body: RequestType
    get() = message.body()

  override fun reply(response: ResponseType) {
    message.reply(response, deliveryOptions)
  }
}
