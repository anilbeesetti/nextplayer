package com.graviton.feature.player.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build

/** Real session-bound EQ. API 28+ uses a configurable 15-band pre-EQ, older devices use hardware bands. */
class SessionEqualizer private constructor(
    val frequenciesHz: IntArray,
    val gainRangeDb: ClosedFloatingPointRange<Float>,
    private val setEnabledBlock: (Boolean) -> Unit,
    private val setGainsBlock: (FloatArray) -> Unit,
    private val releaseBlock: () -> Unit,
) {
    val bandCount: Int get() = frequenciesHz.size

    fun setEnabled(enabled: Boolean) = runCatching { setEnabledBlock(enabled) }.isSuccess
    fun setGains(gainsDb: FloatArray): Boolean {
        if (gainsDb.size != bandCount) return false
        return runCatching { setGainsBlock(gainsDb) }.isSuccess
    }
    fun release() = runCatching(releaseBlock)

    companion object {
        private val FIFTEEN_BANDS = intArrayOf(
            25, 40, 63, 100, 160, 250, 400, 630, 1_000, 1_600, 2_500, 4_000, 6_300, 10_000, 16_000,
        )

        fun create(audioSessionId: Int): SessionEqualizer? {
            if (audioSessionId <= 0) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                createDynamicsProcessing(audioSessionId) ?: createBasic(audioSessionId)
            } else {
                createBasic(audioSessionId)
            }
        }

        private fun createBasic(audioSessionId: Int): SessionEqualizer? = runCatching {
            val effect = Equalizer(0, audioSessionId)
            val frequencies = IntArray(effect.numberOfBands.toInt()) { index ->
                effect.getCenterFreq(index.toShort()) / 1_000
            }
            val range = effect.bandLevelRange
            SessionEqualizer(
                frequenciesHz = frequencies,
                gainRangeDb = range[0] / 100f..range[1] / 100f,
                setEnabledBlock = { effect.enabled = it },
                setGainsBlock = { gains ->
                    gains.forEachIndexed { index, gain ->
                        effect.setBandLevel(index.toShort(), (gain * 100).toInt().toShort())
                    }
                },
                releaseBlock = { effect.release() },
            )
        }.getOrNull()

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
        private fun createDynamicsProcessing(audioSessionId: Int): SessionEqualizer? = runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                FIFTEEN_BANDS.size,
                false,
                0,
                false,
                0,
                true,
            ).build()
            val effect = DynamicsProcessing(0, audioSessionId, config)
            for (channel in 0 until effect.channelCount) {
                val eq = effect.getPreEqByChannelIndex(channel)
                eq.isEnabled = true
                FIFTEEN_BANDS.forEachIndexed { index, frequency ->
                    val band = eq.getBand(index)
                    band.isEnabled = true
                    band.cutoffFrequency = frequency.toFloat()
                    eq.setBand(index, band)
                }
                effect.setPreEqByChannelIndex(channel, eq)
            }
            SessionEqualizer(
                frequenciesHz = FIFTEEN_BANDS.copyOf(),
                gainRangeDb = -15f..15f,
                setEnabledBlock = { effect.enabled = it },
                setGainsBlock = { gains ->
                    for (channel in 0 until effect.channelCount) {
                        val eq = effect.getPreEqByChannelIndex(channel)
                        gains.forEachIndexed { index, gain ->
                            val band = eq.getBand(index)
                            band.gain = gain.coerceIn(-15f, 15f)
                            eq.setBand(index, band)
                        }
                        effect.setPreEqByChannelIndex(channel, eq)
                    }
                },
                releaseBlock = { effect.release() },
            )
        }.getOrNull()
    }
}
