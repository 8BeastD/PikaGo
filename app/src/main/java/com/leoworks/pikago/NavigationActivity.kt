package com.leoworks.pikago.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.leoworks.pikago.R

class NavigationActivity : AppCompatActivity() {

    private var googleMap: GoogleMap? = null
    private var myLatLng: LatLng? = null
    private lateinit var destLatLng: LatLng
    private var orderId: String = "-"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grant ->
        val fine = grant[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grant[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) enableMyLocation() else
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "-"
        val lat = intent.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_DEST_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) { Toast.makeText(this, "Missing destination", Toast.LENGTH_LONG).show(); finish(); return }
        destLatLng = LatLng(lat, lng)

        findViewById<MaterialToolbar>(R.id.topAppBar).apply {
            title = "Navigate"
            subtitle = orderId
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        findViewById<ExtendedFloatingActionButton>(R.id.fabStart).setOnClickListener { openGoogleMapsTurnByTurn() }
        findViewById<ExtendedFloatingActionButton>(R.id.fabRecenter).setOnClickListener { fitBounds() }

        // Add the map fragment programmatically into mapContainer (preview-safe)
        val tag = "nav_map"
        val mapFragment = (supportFragmentManager.findFragmentByTag(tag) as? SupportMapFragment)
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it, tag)
                    .commitNow()
            }

        mapFragment.getMapAsync { map ->
            googleMap = map
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false

            map.addMarker(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(destLatLng)
                    .title("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
            )

            enableMyLocation()
        }
    }

    private fun enableMyLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }

        googleMap?.isMyLocationEnabled = true
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) myLatLng = LatLng(loc.latitude, loc.longitude)
                fitBounds()
            }
            .addOnFailureListener { fitBounds() }
    }

    private fun fitBounds() {
        val map = googleMap ?: return
        val builder = LatLngBounds.Builder().include(destLatLng)
        myLatLng?.let { builder.include(it) }
        val bounds = runCatching { builder.build() }.getOrNull()
        if (bounds != null) map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        else map.animateCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 15f))
    }

    private fun openGoogleMapsTurnByTurn() {
        val uri = Uri.parse("google.navigation:q=${destLatLng.latitude},${destLatLng.longitude}&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LNG = "extra_dest_lng"
    }
}
