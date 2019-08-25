package org.chalup.dawg

import okio.BufferedSink
import okio.BufferedSource

abstract class DawgFormat(internal val id: String,
                          internal val version: Byte) {
    internal abstract fun encode(nodeReader: NodeReader, sink: BufferedSink)
    internal abstract fun decode(source: BufferedSource): NodeReader
}