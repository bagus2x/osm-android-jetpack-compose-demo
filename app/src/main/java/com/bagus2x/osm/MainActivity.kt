package com.bagus2x.osm

import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.bagus2x.osm.ui.theme.OsmTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OsmTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // Camera permission state
                    val cameraPermissionState = rememberMultiplePermissionsState(
                        permissions = listOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )

                    if (cameraPermissionState.allPermissionsGranted) {
                        MapView()
                    } else {
                        Column {
                            val textToShow = if (cameraPermissionState.shouldShowRationale) {
                                // If the user has denied the permission but the rationale can be shown,
                                // then gently explain why the app requires this permission
                                "The camera is important for this app. Please grant the permission."
                            } else {
                                // If it's the first time the user lands on this feature, or the user
                                // doesn't want to be asked again for this permission, explain that the
                                // permission is required
                                "Camera permission required for this feature to be available. " +
                                        "Please grant the permission"
                            }
                            Text(textToShow)
                            Button(onClick = { cameraPermissionState.launchMultiplePermissionRequest() }) {
                                Text("Request permission")
                            }
                        }

                    }
                }
            }
        }
    }

    @Composable
    fun MapView() {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val mapView = remember {
            MapView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        }
        val scope = rememberCoroutineScope()


        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        val mLocationOverlay =
                            MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                        mLocationOverlay.enableMyLocation()
                        mapView.overlays.add(mLocationOverlay)

                        mapView.controller.setCenter(
                            GeoPoint(
                                -7.461404301677735,
                                112.43493941607846
                            )
                        )

                        org.osmdroid.config.Configuration.getInstance()
                            .load(context, PreferenceManager.getDefaultSharedPreferences(context))
                        mapView.setTileSource(TileSourceFactory.MAPNIK);
                        mapView.setMultiTouchControls(true)

                        val controller = mapView.controller
                        controller.setZoom(20.0)
                        mapView.minZoomLevel = 10.0
                        mapView.maxZoomLevel = 40.0
                        mapView.tilesScaleFactor = 1f

                        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                val lat = p?.latitude ?: return false
                                val lng = p.longitude
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    geocoder.getFromLocation(
                                        lat, lng, 1
                                    ) { addresses ->
                                        Toast.makeText(
                                            context,
                                            addresses.getOrNull(0).toString(),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                                    Toast.makeText(
                                        context,
                                        addresses?.getOrNull(0).toString(),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                Toast.makeText(
                                    context,
                                    "${p?.latitude} ${p?.longitude}",
                                    Toast.LENGTH_LONG
                                ).show()
                                return true
                            }

                        }))

                        scope.launch {
                            flow {
                                emit(listOf(0.0 to 0.0, 0.1 to 0.1))
                            }.collectLatest { locations ->
                                locations.forEach { (lat, lng) ->
                                    val startMarker = Marker(mapView)

                                    startMarker.position = GeoPoint(lat, lng)
                                    startMarker.setAnchor(
                                        Marker.ANCHOR_CENTER,
                                        Marker.ANCHOR_BOTTOM
                                    )

                                    mapView.overlays.add(startMarker)
                                }
                            }
                        }

                        mapView.onResume()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        mapView.onPause()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        AndroidView(factory = { mapView })
    }
}
