package com.example.ddelivery;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private MapView mapView = null;
    private MyLocationNewOverlay myLocationOverlay;
    private CompassOverlay compassOverlay;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //OSMDroid alapértelmezett beállítások, cache konfiguráció
        Configuration.getInstance().setUserAgentValue("com.example.ddelivery");

        setContentView(R.layout.activity_main);

        //MapView inicializálása
        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(17.5);
        mapView.getController().setCenter(new GeoPoint(47.0828, 17.9056));

        //Helymeghatározás
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();

        mapView.getOverlays().add(myLocationOverlay);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        checkLocationPermission();
        //Saját icon helyzethez (Nem működik rendesen de legalább szar)
        /*
        Drawable currentDraw = ResourcesCompat.getDrawable(getResources(), R.drawable.navarrow, null);
        Bitmap currentIcon;
        currentIcon = null;
        if (currentDraw != null) {
            currentIcon = ((BitmapDrawable) currentDraw).getBitmap();
            currentIcon = Bitmap.createScaledBitmap(currentIcon,50,50,true);
        }

        myLocationOverlay.setPersonIcon(currentIcon);
        myLocationOverlay.enableFollowLocation();

        myLocationOverlay.setPersonIcon( currentIcon );
        */

        //Gomb, ugrás a saját lokációra
        Button btnCenterMap = findViewById(R.id.btn_center_map);
        btnCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myLocationOverlay.getMyLocation() != null) {
                    mapView.getController().setZoom(18.0);
                    mapView.getController().setCenter(new GeoPoint(myLocationOverlay.getMyLocation().getLatitude(),myLocationOverlay.getMyLocation().getLongitude()));
                } else {
                    Toast.makeText(MainActivity.this, "Current location not available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //Iránytű
        compassOverlay = new CompassOverlay(this, new InternalCompassOrientationProvider(this), mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        myLocationOverlay.enableMyLocation();
        compassOverlay.enableCompass();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        myLocationOverlay.disableMyLocation();
        compassOverlay.disableCompass();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.setDestroyMode(true);
    }

    //Helyadatok frissítésevel kapcsolatos beállítások
    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
        }
    }



    //Helyadatok lekérdezése
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            GeoPoint newLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            mapView.getController().setCenter(newLocation);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {}
    };

    //Engedélykérés eredményszámítás
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //Helyadathozzáférés függvényében indítja az updatet
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            startLocationUpdates();
        }
    }

    //Helyadatok frissítésének leállítása, ha az engedély megtagadva
    private void stopLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }
}
