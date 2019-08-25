package org.chalup.dawg

import okio.Sink
import okio.Source
import okio.buffer

class Dawg
internal constructor(private val nodeReader: NodeReader) {
    operator fun contains(word: String): Boolean {
        var nodeIndex = 0
        var charIndex = 0

        do {
            val node = nodeReader[nodeIndex]

            if (node.letter == word[charIndex]) {
                if (charIndex + 1 == word.length) {
                    return node.endOfWord
                } else {
                    nodeIndex = node.firstChildIndex
                    charIndex += 1
                }
            } else if (!node.lastChild) {
                nodeIndex += 1
            } else {
                nodeIndex = 0
            }
        } while (nodeIndex != 0)

        return false
    }

    fun words(): List<String> = nodeReader.words()
    fun encode(sink: Sink, format: DawgFormat = supportedFormats.maxBy { it.version }!!): Unit = with(sink.buffer()) {
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