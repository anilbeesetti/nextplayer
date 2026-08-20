package com.graviton.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class LoopMode {
    OFF,
    ONE,
    ALL,
}
