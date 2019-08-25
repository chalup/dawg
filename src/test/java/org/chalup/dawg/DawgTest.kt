package org.chalup.dawg

import com.google.common.truth.Truth
import okio.Buffer
import org.junit.jupiter.api.Test

class DawgTest {
    @Test
    fun `generated dawg should return the same word list as the input`() {
        val dawgWords = Dawg.generate(words).words()
        Truth.assertThat(dawgWords).containsExactlyElementsIn(words)
    }

    @Test
    fun `round trip through IntFormat should maintain all the data`() {
        val decodedWords = Buffer().use { buffer ->
            Dawg.generate(words).encode(buffer, IntFormat)
            Dawg.decode(buffer).words()
        }

        Truth.assertThat(decodedWords).containsExactlyElementsIn(words)
    }

    @Test
    fun `dawg should contain all the input words`() {
        val dawg = Dawg.generate(words)
        for (word in words) {
            Truth.assertThat(word in dawg).isTrue()
        }
    }

    @Test
    fun `dawg does not contain prefixes of the input words`() {
        val dawg = Dawg.generate(words)

        Truth.assertThat("dar" in dawg).isFalse()
    }

    @Test
    fun `dawg should not contain words outside of the input word list`() {
        val dawg = Dawg.generate(words)

        Truth.assertThat("abrakadabra" in dawg).isFalse()
    }

    companion object {
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
    }
}