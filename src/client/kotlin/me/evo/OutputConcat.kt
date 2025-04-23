package me.evo

import com.google.common.collect.Lists
import it.unimi.dsi.fastutil.shorts.ShortConsumer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.util.function.Consumer

@Environment(EnvType.CLIENT)
class OutputConcat(size: Int) : ShortConsumer {
    private val buffers: MutableList<ByteBuffer?> = Lists.newArrayList()
    private val size: Int = size + 1 and -2
    var currentBufferSize: Int = 0
        private set
    private var buffer: ByteBuffer

    init {
        this.buffer = BufferUtils.createByteBuffer(size)
    }

    override fun accept(value: Short) {
        if (this.buffer.remaining() == 0) {
            this.buffer.flip()
            this.buffers.add(this.buffer)
            this.buffer = BufferUtils.createByteBuffer(this.size)
        }

        this.buffer.putShort(value)
        this.currentBufferSize += 2
    }

    fun accept(values: ShortArray) {
        for (value in values) {
            accept(value)
        }
    }

    fun getBuffer(): ByteBuffer {
        this.buffer.flip()
        if (this.buffers.isEmpty()) {
            return this.buffer
        } else {
            val byteBuffer = BufferUtils.createByteBuffer(this.currentBufferSize)
            this.buffers.forEach(Consumer { src: ByteBuffer? -> byteBuffer.put(src) })
            byteBuffer.put(this.buffer)
            byteBuffer.flip()
            return byteBuffer
        }
    }
}