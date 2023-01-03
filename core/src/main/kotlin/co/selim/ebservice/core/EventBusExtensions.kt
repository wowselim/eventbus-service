package co.selim.ebservice.core

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.MessageCodec

fun EventBus.initializeServiceCodec() {
  registerCodec(ServiceCodec)
}

fun EventBus.initializeServiceCodec(
  customCodec: MessageCodec<*, *>,
  customDeliveryOptions: DeliveryOptions = deliveryOptions,
) {
  registerCodec(customCodec)
  deliveryOptions = customDeliveryOptions.setCodecName(customCodec.name())
}

var deliveryOptions: DeliveryOptions = DeliveryOptions()
  .setLocalOnly(true)
  .setCodecName(ServiceCodec.name())

private object ServiceCodec : MessageCodec<Any, Any> {
  override fun decodeFromWire(pos: Int, buffer: Buffer?) = throw UnsupportedOperationException()
  override fun encodeToWire(buffer: Buffer?, s: Any?) = throw UnsupportedOperationException()
  override fun transform(s: Any?) = s
  override fun name() = "ebservice"
  override fun systemCodecID(): Byte = -1
}
