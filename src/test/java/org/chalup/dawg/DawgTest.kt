package org.chalup.dawg

import com.google.common.truth.Truth
import org.junit.jupiter.api.Test

class DawgTest {
    @Test
    fun `generated dawg should return the same word list as the input`() {
        val words = listOf("darować",
                           "konfiskowania",
                           "odpijmyż",
                           "powystawianiem",
                           "przeżartować",
                           "przeżartujmy",
                           "respiracyjnego",
                           "respirować",
                           "respirujmy",
                           "zabłąkaliście")

        val dawgWords = Dawg.generate(words).words()

        Truth.assertThat(dawgWords).containsExactlyElementsIn(words)
    }
}