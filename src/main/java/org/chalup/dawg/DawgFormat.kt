package org.chalup.dawg

import okio.BufferedSink
import okio.BufferedSource
import okio.utf8Size

abstract class DawgFormat(internal val id: String,
                          internal val version: Byte) {
    internal abstract fun encode(nodeReader: NodeReader, sink: BufferedSink)
    internal abstract fun decode(source: BufferedSource): NodeReader
}

internal val supportedFormats: List<DawgFormat> = listOf(IntFormat)

/*
Encodes each node as a single 32-bit int:

MSB                             LSB
..CWIIII IIIIIIII IIIIIIII LLLLLLLL

C - last child flag
W - end of word flag
I - index of the first child (0 if the node has no children)
L - index of the letter

The node list is preceded by the UTF-8 string with a letters dictionary.
 */
internal object IntFormat : DawgFormat(id = "int",
                                       version = 1) {

    override fun encode(nodeReader: NodeReader, sink: BufferedSink) {
        check(nodeReader.size() <= 0xF_FFFF)

        // prepare letters lookup
        val letters = nodeReader
            .nodesSequence()
            .map { it.letter }
            .distinct()
            .toList()
            .also { check(it.size < 0xFF) }

        val letterIndexLookup = letters.withIndex().associate { (index, letter) -> letter to index }

        // encode dictionary
        val dictionary = letters.joinToString(separator = "")
        sink.writeLong(dictionary.utf8Size())
        sink.writeUtf8(dictionary)

        // nodes count
        sink.writeInt(nodeReader.size())

        // nodes
        nodeReader
            .nodesSequence()
            .map { it.encodeAsInt(letterIndexLookup) }
            .forEach { sink.writeInt(it) }
    }

    private fun Node.encodeAsInt(letterIndexLookup: Map<Char, Int>): Int {
        var encoded = 0

        encoded += firstChildIndex shl 8
        encoded += letterIndexLookup.getValue(letter)
        if (endOfWord) encoded += END_OF_WORD_FLAG
        if (lastChild) encoded += LAST_CHILD_FLAG

        return encoded
    }

    override fun decode(source: BufferedSource): NodeReader {
        val letterLookup =
            source.readUtf8(source.readLong()).withIndex().associate { (index, letter) -> index to letter }
        val encodedNodes = IntArray(source.readInt()) { source.readInt() }

        return IntNodeReader(encodedNodes, letterLookup)
    }

    private class IntNodeReader(private val intEncodedNodes: IntArray,
                                private val letterLookup: Map<Int, Char>) : NodeReader {
        override fun size(): Int = intEncodedNodes.size
        override fun get(index: Int): Node = intEncodedNodes[index].decode()

        private fun Int.decode() = Node(
            letter = letterLookup.getValue(this and 0xFF),
            firstChildIndex = (this and 0x0FFFFF00) ushr 8,
            lastChild = this and LAST_CHILD_FLAG != 0,
            endOfWord = this and END_OF_WORD_FLAG != 0
        )
    }

    private const val END_OF_WORD_FLAG: Int = 0x10000000
    private const val LAST_CHILD_FLAG: Int = 0x20000000
}