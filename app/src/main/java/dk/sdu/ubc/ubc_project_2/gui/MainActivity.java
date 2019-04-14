package dk.sdu.ubc.ubc_project_2.gui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dk.sdu.ubc.ubc_project_2.R;
import dk.sdu.ubc.ubc_project_2.domain.Fingerprint;

import static com.google.common.collect.Maps.newHashMap;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    @BindView(R.id.title)
    TextView title;

    @BindView(R.id.mapView)
    MapView mapView;
    private GoogleMap map;

    private FusedLocationProviderClient fusedLocationClient;
    private WifiManager wifiManager;

    //maps a single location to a collection of fingerprints
    private Multimap<LatLng, Fingerprint> radioMap = HashMultimap.create();

    private int k = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        requestPermission();
        init();
    }

    private void init() {
        //init location provider, is only used to move the map to where the user currently is
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mapView.onCreate(null);
        mapView.onResume();
        mapView.getMapAsync(this);

        updateTitle();
    }

    private void updateTitle() {
        title.setText(getString(R.string.title, k));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            map = googleMap;
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            map.getUiSettings().setMapToolbarEnabled(false);

            //click listener for gathering radio map data
            map.setOnMapClickListener(this::collectFingerprints);

            //move camera to current location to make it easier to find your location on the map
            fusedLocationClient.getLastLocation().addOnSuccessListener(location ->
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 19))
            );
        }
        catch (SecurityException e) {
            Log.d(getString(R.string.app_name), "Permissions not received yet");
        }
    }

    /**
     * Collects fingerprints and saves them in a radio map.
     */
    private void collectFingerprints(LatLng currentLatLng) {
        Toast.makeText(getApplicationContext(), "Gathering data for location\n" +
                "Lat: " + currentLatLng.latitude + "\n" +
                "Lon: " + currentLatLng.longitude, Toast.LENGTH_SHORT).show();
        List<ScanResult> fingerprints = wifiManager.getScanResults();
        if (fingerprints.isEmpty()) {
            Toast.makeText(getApplicationContext(), "No fingerprints have been gathered.\n" +
                    "Check that WiFi is enabled and there are nearby access points", Toast.LENGTH_LONG).show();
        }
        fingerprints.stream()
                .map(scanResult -> new Fingerprint(scanResult.BSSID, scanResult.level))
                .forEach(fingerprint -> radioMap.put(currentLatLng, fingerprint));
    }

    @OnClick(R.id.predictBtn)
    public void predictLocation() {
        //lookup table (based on access point address) of measurements representing current position
        Map<String, Integer> measurements = wifiManager.getScanResults().stream()
                .collect(Collectors.toMap(entry -> entry.BSSID, entry -> entry.level));

        //map of location and distance to that location
        Map<LatLng, Double> distances = calculateDistances(measurements);
        if (distances.isEmpty()) {
            Toast.makeText(getApplicationContext(), "No radiomaps have been gathered.\n" +
                    "Click your location on the map to gather data.", Toast.LENGTH_LONG).show();
        }
        else {
            //find k nearest neighbors
            List<LatLng> kNearestNeighbors = findKNearestNeighbors(distances);

            //calculate lat and lon as average of k nearest neighbors' positions
            LatLng predictedLocation = calculateAvgLatLng(kNearestNeighbors);
            map.clear();
            map.addMarker(new MarkerOptions().position(predictedLocation)
                    .title("Predicted location")
                    .snippet("Lat: " + predictedLocation.latitude + ", " +
                            "Lon: " + predictedLocation.longitude));

            Streams.mapWithIndex(kNearestNeighbors.stream(), (latLng, index) ->
                    new MarkerOptions().position(latLng)
                        .title("Nearest neighbor (#" + (index + 1) + ")")
                        .snippet("Lat: " + latLng.latitude + ", " +
                                "Lon: " + latLng.longitude)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        .alpha(0.25f))
                    .forEach(map::addMarker);

            Toast.makeText(getApplicationContext(), "Predicted location (marker):\n" +
                    "Lat: " + predictedLocation.latitude + "\n" +
                    "Lon: " + predictedLocation.longitude, Toast.LENGTH_SHORT).show();
        }
    }

    private Map<LatLng, Double> calculateDistances(Map<String, Integer> measurements) {
        //map of location and distance to that location
        Map<LatLng, Double> distances = newHashMap();
        //calculate Euclidian distance from each measurement to each radio map entry
        radioMap.asMap().forEach((key, value) -> {
            double distance = Math.sqrt(value.stream()
                    .filter(fingerprint -> measurements.containsKey(fingerprint.getName()))
                    .map(fingerprint -> Math.pow(measurements.get(fingerprint.getName()) - fingerprint.getSignal(), 2))
                    .reduce((d1, d2) -> d1 + d2)
                    .get());
            distances.put(key, distance);
        });
        return distances;
    }

    private List<LatLng> findKNearestNeighbors(Map<LatLng, Double> distances) {
        //find k nearest neighbors
        return distances.entrySet().stream()
                .sorted(Comparator.comparing(Entry::getValue))
                .map(Entry::getKey)
                .limit(k)
                .collect(Collectors.toList());
    }

    private LatLng calculateAvgLatLng(List<LatLng> kNearestNeighbors) {
        //calculate lat and lon as average of k nearest neighbors' positions
        double predictedLat = kNearestNeighbors.stream().mapToDouble(latLng -> latLng.latitude).average().getAsDouble();
        double predictedLon = kNearestNeighbors.stream().mapToDouble(latLng -> latLng.longitude).average().getAsDouble();

        return new LatLng(predictedLat, predictedLon);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dots, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.kBtn:
                showChangeKPrompt();
                break;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
        return true;
    }

    private void showChangeKPrompt() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Change k-value");
        final NumberPicker numberPicker = new NumberPicker(this);
        numberPicker.setMinValue(1);
        numberPicker.setMaxValue(20);
        numberPicker.setValue(k);
        alert.setView(numberPicker);
        alert.setPositiveButton("Ok", (dialog, whichButton) -> {
            k = numberPicker.getValue();
            updateTitle();
        });
        alert.setNegativeButton("Cancel", (dialog, whichButton) -> {});
        alert.show();
    }

    /**
     * Invoked when the user responds to the app's permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // if the request is cancelled, the result arrays are empty
            Log.d(getString(R.string.app_name), "Permission was granted");
            init();
        }
        else {
            Toast.makeText(getApplicationContext(), "Error: permissions denied", Toast.LENGTH_LONG).show();
            Log.d(getString(R.string.app_name), "Permission was denied");
        }
    }

    private void requestPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(getString(R.string.app_name), "Requesting permissions");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }
    }

}
