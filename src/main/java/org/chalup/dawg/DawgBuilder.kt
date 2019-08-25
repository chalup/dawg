package org.chalup.dawg

import okio.Buffer
import okio.ByteString

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

        lateinit var hash: ByteString

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

    private fun TrieNode.calculateHashes(brothersHash: Buffer = Buffer()): TrieNode = apply {
        val childrenHash = Buffer()

        // We're iterating through children backwards, so the intermediate values of
        // childrenHash are in fact brothersHash of successive children.
        children.asReversed().forEach { child ->
            child.calculateHashes(childrenHash)
            childrenHash.write(child.hash)
        }

        fun Buffer.writeBoolean(b: Boolean): Buffer = writeByte(if (b) 1 else 0)

        hash = childrenHash
            .writeInt(letter.toInt())
            .writeBoolean(endOfDawgList)
            .write(brothersHash.peek().readByteString())
            .sha1()
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