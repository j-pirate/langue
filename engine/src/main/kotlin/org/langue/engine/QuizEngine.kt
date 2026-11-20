package org.langue.engine

import java.util.Collections
import kotlin.random.Random

/** An exercise representation independent of UI, persistence and language. */
data class QuizQuestion(val entry: Entry, val options: List<Entry>)

/** Additional exercise modes implement this contract without depending on Android or persistence. */
interface ExerciseGenerator<Q> {
    fun next(entries: List<Entry>, previousEntryId: String? = null, random: Random = Random.Default): Q?
}

/**
 * Immutable preparation for one revision of a pack. Retain this object for the active pack,
 * and replace it after a pack switch or update. Preparing belongs on a worker dispatcher;
 * drawing a question only samples cached indices and allocates its three choices.
 */
class PreparedQuiz internal constructor(
    private val entries: List<Entry>,
    private val groups: List<IntArray>,
    private val groupByEntry: IntArray,
    private val indexById: Map<String, Int>,
) {
    val entryCount: Int get() = entries.size
    val equivalenceGroupCount: Int get() = groups.size
    val canGenerate: Boolean get() = groups.size >= 3

    /** O(1) work, independent of pack size; the preceding question is excluded when known. */
    fun next(previousEntryId: String? = null, random: Random = Random.Default): QuizQuestion? {
        if (!canGenerate) return null
        val previous = previousEntryId?.let(indexById::get)
        val selected = if (previous == null) random.nextInt(entries.size)
            else excludingOne(random.nextInt(entries.size - 1), previous)
        val correctGroup = groupByEntry[selected]
        val firstGroup = excludingOne(random.nextInt(groups.size - 1), correctGroup)
        val secondGroup = excludingTwo(random.nextInt(groups.size - 2), correctGroup, firstGroup)
        fun member(group: Int): Entry {
            val indices = groups[group]
            return entries[indices[random.nextInt(indices.size)]]
        }
        val correct = entries[selected]
        return QuizQuestion(correct, listOf(correct, member(firstGroup), member(secondGroup)).shuffled(random))
    }

    private fun excludingOne(index: Int, excluded: Int): Int = if (index >= excluded) index + 1 else index

    private fun excludingTwo(index: Int, first: Int, second: Int): Int {
        val lower = minOf(first, second)
        val upper = maxOf(first, second)
        val afterLower = excludingOne(index, lower)
        return excludingOne(afterLower, upper)
    }
}

object QuizEngine : ExerciseGenerator<QuizQuestion> {
    /**
     * Targets, alternatives and identical sources form transitive equivalence groups.
     * Union by rank with path compression prepares them once for a pack revision.
     * No global cache retains removed packs; the owner controls the prepared session lifetime.
     */
    fun prepare(entries: List<Entry>): PreparedQuiz {
        // Do not retain mutable lists supplied by a caller, including alternatives exposed by a question.
        val snapshot = entries.map { entry ->
            entry.copy(alternatives = Collections.unmodifiableList(ArrayList(entry.alternatives)))
        }
        val parents = IntArray(snapshot.size) { it }
        val ranks = ByteArray(snapshot.size)
        fun root(start: Int): Int {
            var value = start
            while (parents[value] != value) {
                parents[value] = parents[parents[value]]
                value = parents[value]
            }
            return value
        }
        fun join(left: Int, right: Int) {
            val leftRoot = root(left)
            val rightRoot = root(right)
            if (leftRoot == rightRoot) return
            when {
                ranks[leftRoot] < ranks[rightRoot] -> parents[leftRoot] = rightRoot
                ranks[leftRoot] > ranks[rightRoot] -> parents[rightRoot] = leftRoot
                else -> {
                    parents[rightRoot] = leftRoot
                    ranks[leftRoot]++
                }
            }
        }
        val targets = mutableMapOf<String, Int>()
        val sources = mutableMapOf<String, Int>()
        snapshot.forEachIndexed { index, entry ->
            sources.putIfAbsent(VocabularySearch.normalize(entry.source), index)?.let { join(index, it) }
            targets.putIfAbsent(VocabularySearch.normalize(entry.target), index)?.let { join(index, it) }
            entry.alternatives.forEach { text ->
                targets.putIfAbsent(VocabularySearch.normalize(text), index)?.let { join(index, it) }
            }
        }
        val grouped = LinkedHashMap<Int, MutableList<Int>>()
        snapshot.indices.forEach { index -> grouped.getOrPut(root(index)) { mutableListOf() }.add(index) }
        val groups = grouped.values.map { it.toIntArray() }
        val groupByEntry = IntArray(snapshot.size)
        groups.forEachIndexed { group, indices -> indices.forEach { groupByEntry[it] = group } }
        return PreparedQuiz(snapshot, groups, groupByEntry, snapshot.mapIndexed { index, entry -> entry.id to index }.toMap())
    }

    /** Convenience for one-off consumers. Interactive sessions should retain [prepare]'s result. */
    override fun next(entries: List<Entry>, previousEntryId: String?, random: Random): QuizQuestion? =
        prepare(entries).next(previousEntryId, random)
}
