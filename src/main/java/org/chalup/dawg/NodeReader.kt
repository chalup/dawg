package org.chalup.dawg

internal interface NodeReader {
    fun size(): Int
    operator fun get(index: Int): Node
}

internal class ListNodeReader(private val nodes: List<Node>) : NodeReader {
    override fun size(): Int = nodes.size
    override fun get(index: Int): Node = nodes[index]
}

internal fun NodeReader.words(): List<String> {
    fun findWords(nodeIndex: Int = 0,
                  prefix: String = "",
                  results: MutableList<String>): Unit = with(get(nodeIndex)) {
        if (endOfWord) results.add(prefix + letter)
        if (!lastChild) findWords(nodeIndex + 1, prefix, results)
        if (firstChildIndex != 0) findWords(firstChildIndex, prefix + letter, results)
    }

    return mutableListOf<String>().apply { findWords(results = this) }
}

internal fun NodeReader.nodesSequence(): Sequence<Node> =
    (0 until size()).iterator().asSequence().map { get(it) }