package me.evo

import net.minecraft.client.sound.NonRepeatingAudioStream
import org.chenliang.oggus.opus.AudioDataPacket
import org.chenliang.oggus.opus.IdHeader
import org.chenliang.oggus.opus.OggOpusStream
import org.chenliang.oggus.opus.OpusPacket
import org.concentus.CodecHelpers
import org.concentus.OpusDecoder
import org.concentus.OpusException
import org.concentus.OpusPacketInfo
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.util.*
import javax.sound.sampled.AudioFormat

class OpusAudioStream(val inputStream: InputStream) : NonRepeatingAudioStream {
    val opusStream: OggOpusStream = OggOpusStream.from(inputStream)
    val idHeader: IdHeader = opusStream.idHeader
    var sampleRate = idHeader.inputSampleRate.toInt()
    var channels = idHeader.channelCount

    private val format: AudioFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat(),
        16,
        channels,
        channels * 2,
        sampleRate.toFloat(),
        false
    )

    val decoder = OpusDecoder(sampleRate, channels)

    private val packetQueue: Queue<OpusPacket?> = LinkedList<OpusPacket?>()

    var endOfStream = false

    init {
        OpusReworkClient.LOGGER.info("Initializing opus @ $sampleRate hz ($channels channel(s))")
    }

    @Throws(IOException::class)
    private fun readPacket(): OpusPacket? {
        if (!packetQueue.isEmpty()) {
            return packetQueue.poll()
        }

        if (endOfStream) {
            return null
        }

        val audioPacket: AudioDataPacket? = opusStream.readAudioPacket()
        if (audioPacket == null) {
            endOfStream = true
            return null
        }

        val packets = audioPacket.opusPackets
        packetQueue.addAll(packets)

        return if (packetQueue.isEmpty()) null else packetQueue.poll()
    }

    @Throws(IOException::class)
    private fun readPackets(maxPackets: Int): MutableList<OpusPacket> {
        val result: MutableList<OpusPacket> = ArrayList<OpusPacket>(maxPackets)

        for (i in 0..<maxPackets) {
            val packet: OpusPacket? = readPacket()
            if (packet == null) {
                break
            }
            result.add(packet)
        }
        return result
    }

    @Throws(IOException::class, OpusException::class)
    private fun decodeNextBatch(maxPackets: Int): ShortArray? {
        val packets: MutableList<OpusPacket> = readPackets(maxPackets)

        if (packets.isEmpty()) {
            return null
        }

        val firstPacket = packets[0].dumpToStandardFormat()
        val samplesPerFrame = OpusPacketInfo.getNumSamplesPerFrame(firstPacket, 0, sampleRate)
        val totalSamples: Int = samplesPerFrame * packets.size * channels

        val decoded = ShortArray(totalSamples)
        var sampleOffset = 0

        packets.forEach { packet ->
            val encodedData = packet.dumpToStandardFormat()
            val code: Int = decoder.decode(
                encodedData, 0, encodedData.size,
                decoded, sampleOffset, samplesPerFrame, false
            )

            if (code < 0) {
                OpusReworkClient.LOGGER.info("Opus decoding error: " + CodecHelpers.opus_strerror(code))
                return@forEach
            }

            sampleOffset += code * channels
        }

        if (sampleOffset < totalSamples) {
            val trimmed = ShortArray(sampleOffset)
            System.arraycopy(decoded, 0, trimmed, 0, sampleOffset)
            return trimmed
        }

        return decoded
    }

    override fun read(size: Int): ByteBuffer? {
        val output = OutputConcat(16384)
        val decoded: ShortArray? = decodeNextBatch(1)

        if (decoded == null || decoded.isEmpty()) {
            return null
        }
        output.accept(decoded)
        return output.getBuffer()
    }

    override fun close() {
        inputStream.close()
    }

    override fun getFormat(): AudioFormat? = format

    override fun readAll(): ByteBuffer? {
        val output = OutputConcat(16384)
        val BATCH_SIZE = 256

        while (true) {
            val decoded: ShortArray? = decodeNextBatch(BATCH_SIZE)
            if (decoded == null || decoded.isEmpty()) {
                break
            } else {
                output.accept(decoded)
            }
        }
        return output.getBuffer()
    }

    companion object {
        @JvmStatic
        fun extractHeader(buf: ByteArray, stream: InputStream): InputStream {
            val skipAmount = 0x1C
            val totalPeek = skipAmount + buf.size

            val peekBuffer = ByteArray(totalPeek)
            val bytesRead = stream.read(peekBuffer)

            if (bytesRead < totalPeek) return stream

            System.arraycopy(peekBuffer, skipAmount, buf, 0, buf.size)

            return SequenceInputStream(
                ByteArrayInputStream(peekBuffer, 0, bytesRead),
                stream
            )
        }
    }
}


