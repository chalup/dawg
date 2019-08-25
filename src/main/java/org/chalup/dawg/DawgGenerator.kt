package org.chalup.dawg

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import okio.buffer
import okio.sink
import java.io.File
import kotlin.system.exitProcess


class DawgGenerator {
    @Parameter(names = ["-v", "--verbose"])
    private var isVerbose: Boolean = false

    @Parameter(names = ["--format"],
               description = "Name of the encoding format. Currently the only supported format is 'int'")
    private var formatName: String = IntFormat.id

    @Parameter(names = ["-o", "--output-file"],
               description = "Output file",
               required = true)
    private lateinit var outputFilePath: String

    @Parameter(description = "Input file",
               required = true)
    private lateinit var inputFilePath: String

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = DawgGenerator()
            .apply {
                try {
                    JCommander.newBuilder()
                        .addObject(this)
                        .build()
                        .parse(*args)
                } catch (e: ParameterException) {
                    e.usage()
                    exitProcess(-1)
                }
            }
            .run()
    }

    private fun run() {
        println("Generating DAWG from $inputFilePath")

        val logger: Logger = when {
            isVerbose -> { block -> println(block()) }
            else -> { _ -> }
        }
        val format = supportedFormats.first { it.id == formatName }

        val words = File(inputFilePath).readLines()

        DawgBuilder(logger)
            .build(words)
            .let { ListNodeReader(it) }
            .let { Dawg(it) }
            .also { dawg ->
                logger { "Checking the DAWG creation" }
                val inputSet = words.toSet()
                val dawgSet = dawg.words().toSet()

                if (inputSet != dawgSet) {
                    println("ERROR: created DAWG contents do not match the input")

                    val missingWords = inputSet - dawgSet
                    val extraWords = dawgSet - inputSet

                    println("Missing words count: ${missingWords.size}, e.g. ${missingWords.take(5)}")
                    println("Extra words count: ${extraWords.size}, e.g. ${extraWords.take(5)}")
                }
            }
            .run { File(outputFilePath).sink().buffer().use { sink -> encode(sink, format) } }

        println("Saved in $outputFilePath in '$formatName' format")
    }
}