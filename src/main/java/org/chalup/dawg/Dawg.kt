package org.chalup.dawg

import okio.Sink
import okio.Source
import okio.buffer

class Dawg
internal constructor(private val nodeReader: NodeReader) {
    operator fun contains(word: String): Boolean {
    }

    fun words(): List<String> = nodeReader.words()
    fun encode(sink: Sink, format: DawgFormat): Unit = with(sink.buffer()) {
        writeUtf8("DAWG")
        writeByte(format.version.toInt())
        format.encode(nodeReader, this)
        flush()
    }

    companion object {
        @JvmStatic
        fun decode(source: Source): Dawg = with(source.buffer()) {
            check(readUtf8(4) == "DAWG")

            val formatVersion = readByte()
            val format = supportedFormats.firstOrNull { it.version == formatVersion }
                ?: throw UnsupportedOperationException("Unsupported format version $formatVersion")

            Dawg(format.decode(this))
        }

        @JvmStatic
        fun generate(words: List<String>): Dawg =
            DawgBuilder()
                .build(words)
                .let { ListNodeReader(it) }
                .let { Dawg(it) }
    }
}