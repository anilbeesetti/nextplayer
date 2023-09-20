package dev.anilbeesetti.nextplayer.core.ui.api

data class AndroidPermissionState(
    val permission: String = "",
    val isGranted: Boolean = true,
    val shouldShowRationale: Boolean = false,
    val grantPermission: () -> Unit = {}
)
