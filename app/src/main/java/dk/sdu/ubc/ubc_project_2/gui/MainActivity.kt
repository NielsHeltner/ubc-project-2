package dk.sdu.ubc.ubc_project_2.gui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.NumberPicker
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.common.collect.HashMultimap
import dk.sdu.ubc.ubc_project_2.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    lateinit var map: GoogleMap

    lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var wifiManager: WifiManager

    val radioMap: HashMultimap<LatLng, Fingerprint> = HashMultimap.create()
    var k = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermission()
        init()
    }

    private fun init() {
        //init location provider, is only used to move the map to where the user currently is
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        mapView.onCreate(null)
        mapView.onResume()
        mapView.getMapAsync(this)

        predictBtn.setOnClickListener { predictLocation() }

        updateHeader()
    }

    private fun updateHeader() {
        header.text = getString(R.string.title, k)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        try {
            map = googleMap
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            map.uiSettings.isMapToolbarEnabled = false

            //click listener for gathering radio map data
            map.setOnMapClickListener{ collectFingerprints(it) }

            //move camera to current location to make it easier to find your location on the map
            fusedLocationClient.lastLocation.addOnSuccessListener {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 19f)) }
        }
        catch (e: SecurityException) {
            Log.d(getString(R.string.app_name), "Permissions not received yet")
        }
    }

    /**
     * Collects fingerprints and saves them in a radio map.
     */
    private fun collectFingerprints(currentLatLng: LatLng) {
        Toast.makeText(applicationContext, "Gathering data for location\n" +
                "Lat: ${currentLatLng.latitude}\n" +
                "Lon: ${currentLatLng.longitude}", Toast.LENGTH_SHORT).show()
        val fingerprints = wifiManager.scanResults
        if (fingerprints.isEmpty()) {
            Toast.makeText(applicationContext, "No fingerprints have been gathered.\n" +
                    "Check that WiFi is enabled and there are nearby access points", Toast.LENGTH_LONG).show()
        }
        fingerprints
                .map { Fingerprint(it.BSSID, it.level) }
                .forEach {radioMap.put(currentLatLng, it) }
    }

    fun predictLocation() {
        //lookup table (based on access point address) of measurements representing current position
        val measurements = wifiManager.scanResults.associateBy({it.BSSID}, {it.level})

        //map of location and distance to that location
        val distances = calculateDistances(measurements)
        if (distances.isEmpty()) {
            Toast.makeText(applicationContext, "No radiomaps have been gathered.\n" +
                    "Click your location on the map to gather data.", Toast.LENGTH_LONG).show()
        } else {
            //find k nearest neighbors
            val kNearestNeighbors = findKNearestNeighbors(distances)

            //calculate lat and lon as average of k nearest neighbors' positions
            val predictedLocation = calculateAvgLatLng(kNearestNeighbors)
            map.clear()
            map.addMarker(MarkerOptions().position(predictedLocation)
                    .title("Predicted location")
                    .snippet("Lat: ${predictedLocation.latitude}, " +
                             "Lon: ${predictedLocation.longitude}"))

            kNearestNeighbors.forEachIndexed{index, latLng ->
                map.addMarker(MarkerOptions().position(latLng)
                        .title("Nearest neighbor (#" + (index + 1) + ")")
                        .snippet("Lat: ${latLng.latitude}, " +
                                 "Lon: ${latLng.longitude}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        .alpha(0.25f))
            }

            Toast.makeText(applicationContext, "Predicted location (red marker):\n" +
                    "Lat: ${predictedLocation.latitude}\n" +
                    "Lon: ${predictedLocation.longitude}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateDistances(measurements: Map<String, Int>): Map<LatLng, Double> {
        //calculate Euclidian distance from each measurement to each radio map entry
        return radioMap.asMap().mapValues { Math.sqrt(it.value
                    .filter { measurements.containsKey(it.name) }
                    .map { Math.pow((measurements.getValue(it.name) - it.signal).toDouble(), 2.0) }
                    .reduce { d1, d2 -> d1 + d2 })
        }
    }

    private fun findKNearestNeighbors(distances: Map<LatLng, Double>): List<LatLng> {
        //find k nearest neighbors
        return distances
                .toList().sortedBy { (_, value) -> value }.toMap()
                .map { it.key }
                .take(k)
                .toList()
    }

    private fun calculateAvgLatLng(kNearestNeighbors: List<LatLng>): LatLng {
        //calculate lat and lon as average of k nearest neighbors' positions
        val predictedLat = kNearestNeighbors.map { it.latitude }.average()
        val predictedLon = kNearestNeighbors.map { it.longitude }.average()

        return LatLng(predictedLat, predictedLon)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_dots, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.kBtn -> showChangeKPrompt()
            else -> super.onOptionsItemSelected(menuItem)
        }
        return true
    }

    private fun showChangeKPrompt() {
        val alert = AlertDialog.Builder(this)
        alert.setTitle("Change k-value")
        val numberPicker = NumberPicker(this)
        numberPicker.minValue = 1
        numberPicker.maxValue = 20
        numberPicker.value = k
        alert.setView(numberPicker)
        alert.setPositiveButton("Ok") { _, _ ->
            k = numberPicker.value
            updateHeader()
        }
        alert.setNegativeButton("Cancel") { _, _ -> }
        alert.show()
    }

    /**
     * Invoked when the user responds to the app's permission request
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // if the request is cancelled, the result arrays are empty
            Log.d(getString(R.string.app_name), "Permission was granted")
            init()
        } else {
            Toast.makeText(applicationContext, "Error: permissions denied", Toast.LENGTH_LONG).show()
            Log.d(getString(R.string.app_name), "Permission was denied")
        }
    }

    private fun requestPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(getString(R.string.app_name), "Requesting permissions")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        }
    }

    data class Fingerprint(val name: String, val signal: Int)

}