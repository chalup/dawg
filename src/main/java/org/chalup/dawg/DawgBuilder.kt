package org.chalup.dawg

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

typealias Logger = (() -> String) -> Unit

internal class DawgBuilder(private val log: Logger = {}) {
    fun build(words: List<String>): List<Node> = this
        .step("Building DAWG with ${words.size} words") { words }
        .step("Sorting words") { sortedWith(compareBy<String> { it.length }.then(naturalOrder())) }
        .step("Building trie") { buildTree(this).setChildMarkers() }
        .step("Calculating hashes") { calculateHashes() }
        .step("Reducing nodes") { reduceGraph() }
        .step("Indexing nodes") { indexedNodes() }
        .step("Converting TrieNodes to DAWG Nodes") {
            map {
                Node(
                    letter = it.letter,
                    firstChildIndex = it.children.firstOrNull()?.dawgIndex ?: 0,
                    endOfWord = it.endOfWord,
                    lastChild = it.endOfDawgList
                )
            }
        }

    private fun <T, R> T.step(text: String, block: T.() -> R): R {
        log { text }
        return block()
    }

    private class TrieNode(var parents: MutableList<TrieNode> = mutableListOf(),
                           var children: MutableList<TrieNode> = mutableListOf(),
                           var letter: Char = ' ',
                           val depthGroup: Int = -1,
                           var endOfWord: Boolean = false,
                           var isDirectChild: Boolean = false,
                           var endOfDawgList: Boolean = false,
                           var isPruned: Boolean = false,
                           var dawgIndex: Int = -1) {
        constructor(letter: Char, depthGroup: Int, parent: TrieNode) : this(
            parents = mutableListOf<TrieNode>(parent),
            letter = letter,
            depthGroup = depthGroup
        ) {
            parent.children.add(this)
        }

        lateinit var hash: Hasher.HashCode

        fun findChild(letter: Char) = children.find { it.letter == letter }
        fun addChild(letter: Char, depthGroup: Int) = TrieNode(letter, depthGroup, this)

        private fun <T> MutableList<T>.startingFrom(element: T) = subList(indexOf(element), size)

        private fun getNextNodes() = parents.first().children.startingFrom(this)

        fun replaceWith(node: TrieNode) {
            if (isPruned) return

            check(!node.isPruned)
            check(this.depthGroup == node.depthGroup)

            val oldNodes = this.getNextNodes()
            val newNodes = node.getNextNodes()
            check(oldNodes.size == newNodes.size)

            oldNodes.asReversed()
                .zip(newNodes.asReversed())
                .forEach { (old, new) ->
                    new.parents.addAll(old.parents)

                    old.parents.forEach { oldParent ->
                        if (oldParent.children.firstOrNull() == old) {
                            oldParent.children = newNodes.startingFrom(new)
                        }
                    }

                    old.isPruned = true
                }
        }
    }

    private fun buildTree(words: List<String>) = TrieNode().apply {
        words
            .asReversed()
            .forEach { word ->
                word
                    .foldIndexed(this) { index: Int, node: TrieNode, letter: Char ->
                        node.run { findChild(letter) ?: addChild(letter, depthGroup = word.length - 1 - index) }
                    }
                    .apply { endOfWord = true }
            }
    }

    private fun TrieNode.setChildMarkers(): TrieNode = apply {
        if (children.isNotEmpty()) {
            children.first().isDirectChild = true
            children.last().endOfDawgList = true

            children.forEach { it.setChildMarkers() }
        }
    }

    private fun TrieNode.calculateHashes(brothersHash: ByteArray = ByteArray(0)): TrieNode = apply {
        var childrenHash = ByteArray(0)

        // We're iterating through children backwards, so the intermediate values of
        // childrenHash are in fact brothersHash of successive children.
        children.asReversed().forEach { child ->
            child.calculateHashes(childrenHash)
            childrenHash += child.hash.data
        }

        hash = Hasher
            .put(childrenHash)
            .put(letter)
            .put(endOfWord)
            .put(brothersHash)
            .hash()
    }

    private fun TrieNode.reduceGraph(): TrieNode = apply {
        val maximumDepth = children.asSequence().map { it.depthGroup }.max()!!

        fun TrieNode.findNodesAtDepth(depth: Int): Set<TrieNode> =
            children.flatMapTo(hashSetOf()) {
                mutableListOf<TrieNode>().apply {
                    if (it.depthGroup == depth) add(it)
                    if (it.depthGroup >= depth) addAll(it.findNodesAtDepth(depth))
                }
            }

        (maximumDepth downTo 0).forEach { depthGroup ->
            log { "  Depth $depthGroup: " }
            findNodesAtDepth(depthGroup)
                .also { log { "    ${it.count()} nodes" } }
                .groupBy { it.hash }
                .values
                .asSequence()
                .map { it.partition { node -> node.isDirectChild } }
                .forEach { (directChildren, otherChildren) ->
                    val replacement = otherChildren.firstOrNull { !it.isPruned }
                        ?: directChildren.firstOrNull { !it.isPruned }

                    if (replacement != null) {
                        directChildren.forEach {
                            if (it !== replacement) {
                                it.replaceWith(replacement)
                            }
                        }
                    }
                }
        }
    }

    private fun TrieNode.indexedNodes(indexed: MutableList<TrieNode> = mutableListOf()): List<TrieNode> {
        children
            .takeIf { it.firstOrNull()?.let { node -> node.isDirectChild && node.dawgIndex == -1 } == true }
            ?.run {
                forEach {
                    it.dawgIndex = indexed.size
                    indexed.add(it)
                }

                forEach {
                    it.indexedNodes(indexed)
                }
            }

        return indexed
    }
}

/**
 * More efficient (roughly 50%) hashing method than the one using okio. Loosely based on Guava Hashing, but tailored
 * for our concrete use case to save some allocations and CPU time: it's not thread safe and it's not reentrant safe.
 */
private object Hasher {
    private val digest = MessageDigest.getInstance("SHA-1")
    private val scratch = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

    fun put(value: Boolean) = apply {
        val byte: Byte = if (value) 1 else 0
        digest.update(byte)
    }

    fun put(value: Char) = apply {
        scratch.putChar(value)
        try {
            digest.update(scratch.array(), 0, Char.SIZE_BYTES)
        } finally {
            scratch.clear()
        }
    }

    fun put(value: ByteArray) = apply {
        digest.update(value)
    }

    fun hash(): HashCode {
        return HashCode(digest.digest())
    }

    class HashCode(val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            (other === this) || (other is HashCode && data.contentEquals(other.data))

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
}