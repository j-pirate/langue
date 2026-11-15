package org.langue.engine

import kotlin.random.Random

/** An exercise representation independent of UI, persistence and language. */
data class QuizQuestion(val entry: Entry, val options: List<Entry>)

/** Additional exercise modes implement this contract without depending on Android or persistence. */
interface ExerciseGenerator<Q> {
    fun next(entries: List<Entry>, previousEntryId: String? = null, random: Random = Random.Default): Q?
}

object QuizEngine : ExerciseGenerator<QuizQuestion> {
    /**
     * Targets, alternatives and identical sources form transitive equivalence groups.
     * Distinct groups are required for all three choices, including the distractors.
     */
    override fun next(entries: List<Entry>, previousEntryId: String?, random: Random): QuizQuestion? {
        if (entries.size < 3) return null
        val parents = IntArray(entries.size) { it }
        fun root(start: Int): Int {
            var value = start
            while (parents[value] != value) {
                parents[value] = parents[parents[value]]
                value = parents[value]
            }
            return value
        }
        fun join(left: Int, right: Int) { parents[root(left)] = root(right) }
        val targets = mutableMapOf<String, Int>()
        val sources = mutableMapOf<String, Int>()
        entries.forEachIndexed { index, entry ->
            val source = VocabularySearch.normalize(entry.source)
            sources.putIfAbsent(source, index)?.let { join(index, it) }
            (listOf(entry.target) + entry.alternatives).forEach { text ->
                val target = VocabularySearch.normalize(text)
                targets.putIfAbsent(target, index)?.let { join(index, it) }
            }
        }
        val groups = entries.indices.groupBy { root(it) }.values.toList()
        if (groups.size < 3) return null
        val eligible = entries.indices.filter { entries[it].id != previousEntryId }
        val selected = (if (eligible.isEmpty()) entries.indices.toList() else eligible).random(random)
        val correct = entries[selected]
        val distractors = groups.filter { root(it.first()) != root(selected) }.shuffled(random).take(2)
            .map { entries[it.random(random)] }
        return QuizQuestion(correct, (distractors + correct).shuffled(random))
    }
}
