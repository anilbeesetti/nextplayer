package dev.anilbeesetti.nextplayer.feature.player.cast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.PlaybackOutputRow
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection

/** Nearby cast devices, scanned while the sheet shows; tapping one starts casting to it. */
@Composable
internal fun CastDeviceRows(connection: PlaybackConnection, deviceName: String?) {
    val context = LocalContext.current
    val selector = remember(context) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) !=
            ConnectionResult.SUCCESS
        ) {
            null
        } else {
            runCatching { CastContext.getSharedInstance(context).mergedSelector }.getOrNull()
        }
    } ?: return
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    if (!granted) {
        PlaybackOutputRow(
            icon = Icons.Rounded.Cast,
            title = stringResource(R.string.cast_to_device),
            summary = stringResource(R.string.allow_nearby_devices),
            selected = false,
            onClick = { launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) },
        )
        return
    }
    val router = remember(context) { MediaRouter.getInstance(context) }
    // Routes change in place, so every callback publishes even an equal-looking list.
    var routes by remember {
        mutableStateOf(
            router.castRoutes(selector),
            neverEqualPolicy(),
        )
    }
    DisposableEffect(
        router,
        selector,
    ) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refresh()

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) =
                refresh()

            override fun onRouteSelected(
                router: MediaRouter,
                selectedRoute: MediaRouter.RouteInfo,
                reason: Int,
            ) = refresh()

            override fun onRouteUnselected(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
                reason: Int,
            ) = refresh()

            private fun refresh() {
                routes = router.castRoutes(selector)
            }
        }
        router.addCallback(
            selector,
            callback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        onDispose { router.removeCallback(callback) }
    }
    routes.forEach { route ->
        // A session selects a group route around the device, so the device is found by name.
        val casting = route.isSelected || route.name == deviceName
        val connecting =
            route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING ||
                (casting && connection == PlaybackConnection.CONNECTING)
        val connected = casting && connection == PlaybackConnection.CAST
        PlaybackOutputRow(
            icon = if (route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_TV) {
                Icons.Rounded.Tv
            } else {
                Icons.Rounded.Cast
            },
            title = route.name,
            summary = stringResource(
                when {
                    connecting -> R.string.connecting
                    connected -> R.string.connected
                    else -> R.string.available
                },
            ),
            selected = connecting || connected,
            onClick = { if (!connecting && !connected) route.select() },
        )
    }
}

private fun MediaRouter.castRoutes(selector: MediaRouteSelector) = routes.filter {
    !it.isDefaultOrBluetooth && it.isEnabled && it.matchesSelector(selector)
}
