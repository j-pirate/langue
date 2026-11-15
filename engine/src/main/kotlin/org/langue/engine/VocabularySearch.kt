package org.langue.engine

import java.text.Normalizer
import java.util.Locale

/** Normalization is for matching only. Original Unicode strings are always retained for display. */
object VocabularySearch {
    private val marks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(marks, "").lowercase(Locale.ROOT).replace("ß", "ss")
        .trim().replace(whitespace, " ")

    fun search(pack: LanguagePack, query: String): List<Entry> {
        val needle = normalize(query)
        if (needle.isEmpty()) return pack.entries
        return pack.entries.filter { entry ->
            (listOf(entry.source, entry.target) + entry.alternatives + listOfNotNull(entry.transliteration))
                .any { normalize(it).contains(needle) }
        }
    }
}
