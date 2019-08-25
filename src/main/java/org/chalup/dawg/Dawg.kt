package org.chalup.dawg

import okio.Sink
import okio.Source

class Dawg
internal constructor(private val nodeReader: NodeReader) {
    operator fun contains(word: String): Boolean = TODO()

    fun encode(sink: Sink, format: DawgFormat): Unit = TODO()

    companion object {
        @JvmStatic
        fun decode(source: Source): Dawg = TODO()

        @JvmStatic
        fun generate(words: List<String>): Dawg = TODO()
    }
}