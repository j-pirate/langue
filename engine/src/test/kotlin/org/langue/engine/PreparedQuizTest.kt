package org.langue.engine

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs remain in test sources and are never bundled with the application. */
class PreparedQuizTest {
    private fun entry(index: Int, target: String = "target.$index", alternatives: List<String> = emptyList()) =
        Entry("id.$index", "category", "source.$index", target, alternatives)

    @Test fun preparedSessionPreservesTransitiveEquivalencesAndAvoidsThePreviousQuestion() {
        val entries = listOf(
            entry(0, "alpha", listOf("sháred-a")),
            entry(1, "SHARED-A", listOf("shared-b")),
            entry(2, "shared-b"),
            entry(3, "delta").copy(source = "école"),
            entry(4, "epsilon").copy(source = "ECOLE"),
            entry(5, "zeta"),
        )
        val quiz = QuizEngine.prepare(entries)
        assertEquals(6, quiz.entryCount)
        assertEquals(3, quiz.equivalenceGroupCount)
        assertTrue(quiz.canGenerate)
        var previous: String? = null
        repeat(1_000) { seed ->
            val question = requireNotNull(quiz.next(previous, Random(seed)))
            assertNotEquals(previous, question.entry.id)
            assertEquals(3, question.options.size)
            assertEquals(1, question.options.count { it.id in setOf("id.0", "id.1", "id.2") })
            assertEquals(1, question.options.count { it.id in setOf("id.3", "id.4") })
            assertEquals(1, question.options.count { it.id == question.entry.id })
            previous = question.entry.id
        }
    }

    @Test fun everyLegalGroupCombinationAndEntryCanBeSampled() {
        val entries = (0 until 8).map { entry(it) }
        val quiz = QuizEngine.prepare(entries)
        val correctIds = mutableSetOf<String>()
        val choices = mutableSetOf<Set<String>>()
        repeat(5_000) { seed ->
            val question = requireNotNull(quiz.next("unknown-id", Random(seed)))
            correctIds += question.entry.id
            choices += question.options.map { it.id }.toSet()
            assertEquals(3, question.options.map { it.id }.toSet().size)
            assertTrue(question.options.all { it in entries })
        }
        assertEquals(entries.map { it.id }.toSet(), correctIds)
        // There are 8 choose 3 legal sets. The seeded draws exercise both index-exclusion boundaries.
        assertEquals(56, choices.size)
    }

    @Test fun preparationOwnsASnapshotOfEntriesAndAlternatives() {
        val mutableAlternatives = mutableListOf("variant")
        val mutableEntries = mutableListOf(entry(0, alternatives = mutableAlternatives), entry(1), entry(2))
        val quiz = QuizEngine.prepare(mutableEntries)
        mutableAlternatives += "target.1"
        mutableEntries.clear()
        assertEquals(3, quiz.entryCount)
        assertEquals(3, quiz.equivalenceGroupCount)
        val question = requireNotNull(quiz.next(random = Random(1)))
        val first = question.options.single { it.id == "id.0" }
        assertEquals(listOf("variant"), first.alternatives)
        try {
            (first.alternatives as MutableList<String>).add("unexpected")
            fail("A question must not expose mutable prepared alternatives")
        } catch (_: UnsupportedOperationException) {
            // The prepared session remains immutable even through the public question model.
        }
    }

    @Test fun newPreparationReflectsPackUpdateWithoutChangingTheOldSnapshot() {
        val original = (0..2).map { entry(it) }
        val old = QuizEngine.prepare(original)
        val updated = QuizEngine.prepare(original.map { if (it.id == "id.0") it.copy(target = "target.1") else it })
        assertTrue(old.canGenerate)
        assertNotNull(old.next())
        assertEquals(2, updated.equivalenceGroupCount)
        assertFalse(updated.canGenerate)
        assertNull(updated.next())
    }

    @Test fun insufficientIndependentContentRemainsUnavailable() {
        assertNull(QuizEngine.prepare(emptyList()).next())
        assertNull(QuizEngine.prepare(listOf(entry(0), entry(1))).next())
        assertNull(QuizEngine.prepare(listOf(entry(0, "same"), entry(1, "SÁME"), entry(2))).next())
    }

    @Test(timeout = 30_000) fun fiftyThousandEntryPackReusesItsPreparationForTwentyThousandQuestions() {
        // Structural access checks and a generous whole-test deadline catch accidental repeated
        // normalization/union-building, without relying on noisy millisecond microbenchmarks.
        val source = CountingEntries(50_000)
        val preparationStart = System.nanoTime()
        val quiz = QuizEngine.prepare(source)
        val preparedNanos = System.nanoTime() - preparationStart
        val readsAfterPreparation = source.reads
        assertEquals(50_000, quiz.entryCount)
        assertEquals(50_000, quiz.equivalenceGroupCount)
        val random = Random(42)
        var previous: String? = null
        val drawStart = System.nanoTime()
        repeat(20_000) {
            val question = requireNotNull(quiz.next(previous, random))
            assertNotEquals(previous, question.entry.id)
            assertEquals(3, question.options.map { option -> option.id }.toSet().size)
            previous = question.entry.id
        }
        val drawingNanos = System.nanoTime() - drawStart
        assertEquals("Drawing must not revisit the input pack", readsAfterPreparation, source.reads)
        println("Quiz performance: 50000 entries prepared in ${preparedNanos / 1_000_000} ms; " +
            "20000 cached questions in ${drawingNanos / 1_000_000} ms")
    }

    private class CountingEntries(override val size: Int) : AbstractList<Entry>() {
        var reads = 0
            private set
        override fun get(index: Int): Entry {
            reads++
            return Entry("id.$index", "category", "source.$index", "target.$index")
        }
    }
}
