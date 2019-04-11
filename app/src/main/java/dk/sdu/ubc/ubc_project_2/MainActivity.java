package dk.sdu.ubc.ubc_project_2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView title;

    private WifiManager wifiManager;
    private ListAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermission();

        title = findViewById(R.id.title);

        recyclerView = findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        listAdapter = new ListAdapter(new ArrayList());
        recyclerView.setAdapter(listAdapter);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        FloatingActionButton myFab = findViewById(R.id.floatingActionButton);
        myFab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scan();
            }
        });
    }

    private void scan() {
        try {
            List<ScanResult> wifiList = wifiManager.getScanResults();
            listAdapter.add(wifiList);
            title.setText("Found " + wifiList.size() + " access points in last scan, " + listAdapter.getItemCount() + " total");

            Log.d(getString(R.string.app_name), "Found " + wifiList.size() + " access points");
            for (ScanResult scanResult : wifiList) {
                long timestamp = scanResult.timestamp;
                String name = scanResult.BSSID;
                int strength = scanResult.level;
                Log.d(getString(R.string.app_name), "Timestamp: " + timestamp + ", name: " + name + ", strength: " + strength);
            }
        }
        catch (NullPointerException e) {
            Log.d(getString(R.string.app_name), "Unable to retrieve WiFi information.");
            Toast.makeText(getApplicationContext(), "Unable to retrieve WiFi information.", Toast.LENGTH_LONG).show();
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
