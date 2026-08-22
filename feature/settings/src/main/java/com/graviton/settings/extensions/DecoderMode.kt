package com.graviton.settings.extensions

import androidx.compose.runtime.Composable
import com.graviton.core.model.DecoderMode

@Composable
fun DecoderMode.name(): String {
    return when (this) {
        DecoderMode.AUTO -> "Auto"
        DecoderMode.HARDWARE -> "HW"
        DecoderMode.SOFTWARE -> "SW"
    }
}
