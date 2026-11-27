package com.traceon.domain

import com.traceon.domain.classification.Candidate
import com.traceon.domain.classification.ClassificationInput
import com.traceon.domain.classification.ContentClassifier
import com.traceon.domain.classification.SequenceDetector
import com.traceon.domain.classification.SimilarityEngine
import com.traceon.domain.model.RelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentClassifierTest {
    private val classifier = ContentClassifier()

    @Test fun androidTutorialIsClassifiedWithRelatedTopic() {
        val r = classifier.classify(ClassificationInput(title = "Kotlin coroutines tutorial for Android"))
        assertEquals("Android", r.category)
        assertEquals("Technology", r.group)
        assertEquals(0.6f, r.confidence, 0.02f)
        assertTrue(r.topics.map { it.name }.containsAll(listOf("Android", "Programming")))
    }

    @Test fun comedyFromTitle() {
        assertEquals("Comedy", classifier.classify(ClassificationInput(title = "Funny cat compilation")).category)
    }

    @Test fun noEvidenceMeansNoCategoryAndZeroConfidence() {
        val r = classifier.classify(ClassificationInput(title = "Zxqv blorp"))
        assertNull(r.category)
        assertEquals(0f, r.confidence, 0f)
        assertTrue(r.topics.isEmpty())
        assertEquals(listOf("blorp", "zxqv"), r.keywords)
    }

    @Test fun matchesWholeWordsOnly() {
        assertNull(classifier.classify(ClassificationInput(title = "Rain in Spain")).category)
    }

    @Test fun aBareUrlTitleIsNeverClassified() {
        assertNull(classifier.classify(ClassificationInput(title = "https://youtu.be/xyz")).category)
    }

    @Test fun domainHintAloneCanClassify() {
        val r = classifier.classify(ClassificationInput(title = "Untitled", domain = "developer.android.com"))
        assertEquals("Android", r.category)
        assertEquals(0.5f, r.confidence, 0.01f)
    }

    @Test fun tagsContributeEvidence() {
        val r = classifier.classify(ClassificationInput(title = "Weekend project", tags = listOf("jetpack compose", "android studio")))
        assertEquals("Android", r.category)
    }

    @Test fun machineLearningBeatsGenericProgramming() {
        assertEquals("Machine Learning", classifier.classify(ClassificationInput(title = "Machine learning with python and tensorflow")).category)
    }

    @Test fun confidenceNeverExceedsCap() {
        val r = classifier.classify(ClassificationInput(title = "android android android jetpack compose android studio gradle apk androidx termux adb"))
        assertTrue(r.confidence <= 0.95f)
    }
}

class SimilarityTest {
    @Test fun numberedSeriesAreDetected() {
        assertTrue(SequenceDetector.sameSeries("Kotlin Basics Part 1", "Kotlin Basics Part 2"))
        assertFalse(SequenceDetector.sameSeries("Kotlin Basics Part 1", "Kotlin Basics Part 1"))
        assertFalse(SequenceDetector.sameSeries("Intro to Rust", "Intro to Go"))
    }

    @Test fun proposesTypedRelationsRankedByScore() {
        val me = Candidate(1, "Jetpack Compose basics", setOf("jetpack", "compose", "basics"), 10L, 1L, null)
        val sameCreator = Candidate(2, "x", setOf("jetpack", "compose", "state"), 10L, 1L, null)
        val otherPlatform = Candidate(3, "y", setOf("jetpack", "compose", "tips"), null, 2L, null)
        val weak = Candidate(4, "z", setOf("jetpack", "gradle"), null, 1L, null)
        val unrelated = Candidate(5, "w", setOf("cooking"), null, 1L, null)
        val out = SimilarityEngine.propose(me, listOf(unrelated, weak, otherPlatform, sameCreator))
        assertEquals(listOf(2L, 3L, 4L), out.map { it.otherId })
        assertEquals(RelationType.SAME_CREATOR, out[0].type)
        assertEquals(RelationType.CROSS_PLATFORM, out[1].type)
        assertEquals(RelationType.SIMILAR, out[2].type)
    }
}
