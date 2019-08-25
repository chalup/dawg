package org.chalup.dawg

internal data class Node(val letter: Char,
                         val firstChildIndex: Int,
                         val lastChild: Boolean,
                         val endOfWord: Boolean)