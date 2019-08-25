package org.chalup.dawg

internal interface NodeReader {
    fun size(): Int
    operator fun get(index: Int): Node
}