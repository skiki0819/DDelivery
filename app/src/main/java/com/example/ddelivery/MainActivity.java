package com.example.ddelivery;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
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

//Szenzor
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.TextView;

import org.osmdroid.config.Configuration;
import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
    //Map & lokáció
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private MapView mapView = null;
    private MyLocationNewOverlay myLocationOverlay;
    private CompassOverlay compassOverlay;
    private LocationManager locationManager;
    //Szenzor
    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    TextView mangetometerText;
    TextView accelemeterText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //OSMDroid alapértelmezett beállítások, cache konfiguráció
        Configuration.getInstance().setUserAgentValue("com.example.ddelivery");



        //Szenzor
        mangetometerText = findViewById(R.id.mangetometerText);
        accelemeterText = findViewById(R.id.accelemeterText);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //MapView inicializálása
        //mapView.setTileSource(TileSourceFactory.MAPNIK);
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

    @SuppressLint("DefaultLocale")
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(sensorEvent.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
            float acceleField = accelerometerReading[0];
            accelemeterText.setText(String.format("%d kph", (int) acceleField));
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(sensorEvent.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
            float xMagneticField = magnetometerReading[0];
            mangetometerText.setText(String.format("%d µT", (int) xMagneticField));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //android doksiba volt, nemtom jólesz e valamire függvény..
    /*
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);
        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        // "orientationAngles" now has up-to-date information.
    }
     */

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        myLocationOverlay.enableMyLocation();
        compassOverlay.enableCompass();

        //Szenzor listennerek
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        myLocationOverlay.disableMyLocation();
        compassOverlay.disableCompass();

        //Szenzor listenner leállítása
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.setDestroyMode(true);
    }

}