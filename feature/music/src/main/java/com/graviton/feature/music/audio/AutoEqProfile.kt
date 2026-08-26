package com.graviton.feature.music.audio

import kotlin.math.abs
import kotlin.math.ln

data class AutoEqPoint(val frequencyHz: Float, val gainDb: Float)
data class AutoEqProfile(val name: String, val points: List<AutoEqPoint>)

/** Imports the common AutoEq GraphicEQ text format and projects it onto device EQ bands. */
object AutoEqImporter {
    private val pair = Regex("""([0-9]+(?:\.[0-9]+)?)\s+([+-]?[0-9]+(?:\.[0-9]+)?)""")

    fun parse(name: String, raw: String): AutoEqProfile? {
        val body = raw.substringAfter("GraphicEQ:", raw)
        val points = pair.findAll(body).mapNotNull { match ->
            val frequency = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val gain = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            AutoEqPoint(frequency, gain.coerceIn(-24f, 24f)).takeIf { frequency in 10f..48_000f }
        }.distinctBy { it.frequencyHz }.sortedBy { it.frequencyHz }.toList()
        return points.takeIf { it.size >= 2 }?.let { AutoEqProfile(name.ifBlank { "Imported AutoEq" }, it) }
    }

    fun project(profile: AutoEqProfile, frequenciesHz: IntArray): FloatArray = FloatArray(frequenciesHz.size) { index ->
        interpolate(profile.points, frequenciesHz[index].toFloat()).coerceIn(-15f, 15f)
    }

    private fun interpolate(points: List<AutoEqPoint>, frequency: Float): Float {
        points.firstOrNull()?.takeIf { frequency <= it.frequencyHz }?.let { return it.gainDb }
        points.lastOrNull()?.takeIf { frequency >= it.frequencyHz }?.let { return it.gainDb }
        val upperIndex = points.indexOfFirst { it.frequencyHz >= frequency }.coerceAtLeast(1)
        val lower = points[upperIndex - 1]
        val upper = points[upperIndex]
        val logFrequency = ln(frequency)
        val span = ln(upper.frequencyHz) - ln(lower.frequencyHz)
        if (abs(span) < 0.0001f) return lower.gainDb
        val amount = (logFrequency - ln(lower.frequencyHz)) / span
        return lower.gainDb + (upper.gainDb - lower.gainDb) * amount
    }
}
