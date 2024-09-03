package com.example.ddelivery;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.location.LocationManager;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.view.View;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    //Map & lokáció
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private MapView mapView = null;
    private CompassOverlay compassOverlay;
    private LocationManager locationManager;

    //Lista/SearchBar
    private List<String> postcodes = new ArrayList<>();
    private SearchView addPostCode;
    private ArrayAdapter<String> adapter;
    private ListView postcodeListView;
    private LinearLayout resizableView;
    private View resizeHandle;
    private ViewGroup.LayoutParams layoutParams;
    //Lista összecsukáshoz kellő variable
    private boolean isExpanded = true; // állapotjelző az animációhoz
    private static final int EXPANDED_HEIGHT = 800; // dp
    private static final int COLLAPSED_HEIGHT = 60; // dp
    private Map<String, List<Marker>> markerMap;
    private Map<String, Polyline> routeMap = new HashMap<>(); // Útvonalak tárolásához szükséges térkép az irányítószámokhoz társítva
    private List<GeoPoint> allGeoPoints = new ArrayList<>();
    GeoPoint fixedStartPoint = new GeoPoint(53.401945, -2.175752);

    private void updateAllGeoPoints(List<GeoPoint> newPoints) {
        // Clear the list before adding new points
        allGeoPoints.clear();
        allGeoPoints.addAll(newPoints);
    }


    double minLat = Double.MAX_VALUE;
    double maxLat = -Double.MAX_VALUE;
    double minLon = Double.MAX_VALUE;
    double maxLon = -Double.MAX_VALUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Configuration.getInstance().setUserAgentValue("com.example.ddelivery");

        //Activity elemek inicializálása
        mapView = findViewById(R.id.map);
        postcodeListView = findViewById(R.id.postcodeListView);
        addPostCode = findViewById(R.id.addPostCode);
        resizableView = findViewById(R.id.resizableView);
        resizeHandle = findViewById(R.id.resizeHandle);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, postcodes);
        postcodeListView.setAdapter(adapter);
        markerMap = new HashMap<>();

        //MapView inicializálása
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(16.0);
        mapView.getController().setCenter(new GeoPoint(53.401945, -2.175752));
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        //Iránytű
        compassOverlay = new CompassOverlay(this, new InternalCompassOrientationProvider(this), mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        addFixedStartPointMarker(fixedStartPoint);

        // SearchView inicializálása
        addPostCode.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!postcodes.contains(query)) {
                    postcodes.add(query);
                    adapter.notifyDataSetChanged();

                    // Geocoding task indítása
                    new GeocodingTask(new GeocodingTask.OnGeocodingCompletedListener() {
                        @Override
                        public void onGeocodingCompleted(List<GeoPoint> geoPoints) {
                            addMarkersToMap(query, geoPoints); // Markerek hozzáadása a térképhez
                        }
                    }).execute(query);
                } else {
                    Toast.makeText(MainActivity.this, "Postcode already in list", Toast.LENGTH_SHORT).show();
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        //Postcode törlése
        postcodeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String postcodeToDelete = postcodes.get(position);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Postcode")
                        .setMessage("Are you sure you want to delete this postcode?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Eltávolítjuk az elemet a listából
                                postcodes.remove(postcodeToDelete);
                                adapter.notifyDataSetChanged();

                                // Markerek eltávolítása a térképről
                                if (markerMap.containsKey(postcodeToDelete)) {
                                    for (Marker marker : markerMap.get(postcodeToDelete)) {
                                        mapView.getOverlays().remove(marker);
                                    }
                                    markerMap.remove(postcodeToDelete);
                                }

                                // Útvonal (kék vonal) eltávolítása a térképről
                                if (routeMap.containsKey(postcodeToDelete)) {
                                    Polyline polyline = routeMap.get(postcodeToDelete);
                                    mapView.getOverlays().remove(polyline);
                                    routeMap.remove(postcodeToDelete); // Törlés a routeMap-ből
                                }

                                // **Új kódrész**: Pontok eltávolítása a allGeoPoints listából
                                List<GeoPoint> pointsToRemove = new ArrayList<>();
                                if (markerMap.containsKey(postcodeToDelete)) {
                                    for (Marker marker : markerMap.get(postcodeToDelete)) {
                                        pointsToRemove.add(marker.getPosition());
                                    }
                                }
                                allGeoPoints.removeAll(pointsToRemove);
                                allGeoPoints.remove(postcodeToDelete);

                                Log.d("DEBUG", " allgeo: "+allGeoPoints.size());
                                Log.d("DEBUG", " postcodes: "+postcodes.size());
                                mapView.invalidate(); // Frissíti a térképet


                                // Visszajelzés a felhasználónak
                                Toast.makeText(MainActivity.this, "Postcode deleted: " + postcodeToDelete, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        // Set up touch listener for the resize handle
        resizeHandle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleResize();
            }
        });
    }

    private void toggleResize() {
        int startHeight = isExpanded ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT;
        int endHeight = isExpanded ? COLLAPSED_HEIGHT : EXPANDED_HEIGHT;
        isExpanded = !isExpanded;

        ValueAnimator animator = ValueAnimator.ofInt(startHeight, endHeight);
        animator.setDuration(300); // duration of animation
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int animatedHeight = (int) valueAnimator.getAnimatedValue();
                ViewGroup.LayoutParams params = resizableView.getLayoutParams();
                params.height = animatedHeight;
                resizableView.setLayoutParams(params);
            }
        });
        animator.start();
    }

    private void addFixedStartPointMarker(GeoPoint fixedStartPoint) {
        Marker startMarker = new Marker(mapView);
        startMarker.setPosition(fixedStartPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("Fixed Start Point");
        //startMarker.setIcon(getResources().getDrawable(R.drawable.ic_start_marker)); // Ha van egyéni marker ikonod

        mapView.getOverlays().add(startMarker);
        mapView.invalidate();

        allGeoPoints.add(fixedStartPoint); // A fix kezdőpont hozzáadása az összes pontok listájához
    }

    private void addMarkersToMap(String postcode, List<GeoPoint> geoPoints) {
        // Update allGeoPoints with the new points
        updateAllGeoPoints(geoPoints);

        // Add new markers and routes
        List<Marker> markers = new ArrayList<>();
        for (GeoPoint point : geoPoints) {
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
            markers.add(marker);
        }

        markerMap.put(postcode, markers);

        if (!geoPoints.isEmpty()) {
            GeoPoint endPoint = geoPoints.get(geoPoints.size() - 1);
            try {
                requestRoute(postcode, endPoint);
            } catch (Exception e) {
                Log.e("DEBUG", "Error requesting route: " + e.getMessage());
            }
        }

        mapView.invalidate();
    }

    private void requestRoute(String postcode, GeoPoint endPoint) {
        String url = "https://router.project-osrm.org/route/v1/driving/"
                + fixedStartPoint.getLongitude() + "," + fixedStartPoint.getLatitude() + ";"
                + endPoint.getLongitude() + "," + endPoint.getLatitude()
                + "?overview=full&geometries=geojson";

        // Hívás a hálózati kéréshez
        new RouteRequestTask(postcode).execute(url);
    }



    private void drawRoute(String postcode, List<GeoPoint> routePoints) {
        // Ha már létezik az útvonal az adott irányítószámhoz, először töröljük
        if (routeMap.containsKey(postcode)) {
            Polyline existingPolyline = routeMap.get(postcode);
            mapView.getOverlays().remove(existingPolyline); // Töröljük a régi útvonalat
            routeMap.remove(postcode); // Töröljük az irányítószámhoz tartozó régi bejegyzést is
        }

        // Új útvonal létrehozása
        Polyline polyline = new Polyline();
        polyline.setPoints(routePoints);
        polyline.setWidth(5.0f); // Vonal vastagságának beállítása
        polyline.setColor(Color.BLUE); // Állítsd be a vonal színét
        polyline.setVisible(true);
        mapView.getOverlays().add(polyline); // Útvonal hozzáadása a térképhez

        // Útvonal tárolása az irányítószámhoz
        routeMap.put(postcode, polyline); // Frissítsük az útvonalat az újra
        Log.d("DEBUG", "Új útvonal megrajzolva az irányítószámhoz: " + postcode);
        mapView.invalidate(); // Frissítjük a térképet, hogy megjelenjen az új útvonal
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        compassOverlay.enableCompass();

    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        compassOverlay.disableCompass();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.setDestroyMode(true);

    }


    private class RouteRequestTask extends AsyncTask<String, Void, String> {
        public String postcode;

        public RouteRequestTask(String postcode) {
            this.postcode = postcode; // Tárolja az irányítószámot a későbbi használatra
        }
        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                Log.d("DEBUG", "OSRM Response: " + result.toString());
                return result.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d("DEBUG", "onPostExecute FV ");
            if (result != null) {
                try {
                    JSONObject json = new JSONObject(result);
                    Log.d("DEBUG", "Parsed JSON: " + json.toString());
                    JSONArray routes = json.getJSONArray("routes");
                    if (routes.length() == 0) {
                        Log.d("DEBUG", "No routes found in the response.");
                        return;
                    }
                    JSONObject route = routes.getJSONObject(0);
                    JSONObject geometry = route.getJSONObject("geometry");
                    JSONArray coordinates = geometry.getJSONArray("coordinates");

                    Log.d("DEBUG", "Coordinates: " + coordinates.toString());

                    List<GeoPoint> routePoints = new ArrayList<>();
                    for (int i = 0; i < coordinates.length(); i++) {
                        JSONArray point = coordinates.getJSONArray(i);
                        double lon = point.getDouble(0);
                        double lat = point.getDouble(1);
                        routePoints.add(new GeoPoint(lat, lon));
                    }

                    drawRoute(postcode, routePoints);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("DEBUG", "JSON Parsing error: " + e.getMessage());
                }
            } else {
                Log.d("DEBUG", "Result is null.");
            }
        }

    }



}


