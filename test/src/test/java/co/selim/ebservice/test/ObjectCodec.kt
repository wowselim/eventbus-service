package co.selim.ebservice.test

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object ObjectCodec : MessageCodec<Any, Any> {
  override fun decodeFromWire(pos: Int, buffer: Buffer): Any {
    val objectSize = buffer.getInt(pos)
    val nextPos = pos + Int.SIZE_BYTES
    val bytes = buffer.getBytes(nextPos, nextPos + objectSize)
    return ObjectInputStream(ByteArrayInputStream(bytes)).use {
      it.readObject()
    }
  }

  override fun encodeToWire(buffer: Buffer, s: Any) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ObjectOutputStream(byteArrayOutputStream).use { it.writeObject(s) }
    buffer.appendInt(byteArrayOutputStream.size())
    buffer.appendBytes(byteArrayOutputStream.toByteArray())
  }

  override fun transform(s: Any?) = throw UnsupportedOperationException("This should not be called by the tests")
  override fun name() = "custom-codec"
  override fun systemCodecID(): Byte = -1
}
