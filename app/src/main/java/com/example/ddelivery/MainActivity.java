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
        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(new GeoPoint(53.40979, -2.15761));
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        //Iránytű
        compassOverlay = new CompassOverlay(this, new InternalCompassOrientationProvider(this), mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        // SearchView inicializálása
        postcodeListView.setAdapter(adapter);
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
                                    mapView.invalidate(); // Frissíti a térképet
                                }

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

    private void handlePostcodeSearch(String postcode) {
        // Itt írd meg a logikát, hogy mit tegyél a beírt postcode-dal
        // Például, használhatsz egy geokódolási szolgáltatást, hogy a postcode-ot GeoPoint-ra alakítsd, majd középre helyezd a térképen
        // Például:
        // GeoPoint point = geocodePostcode(postcode);
        // if (point != null) {
        //     mapView.getController().setCenter(point);
        // }
    }

    private List<GeoPoint> allGeoPoints = new ArrayList<>();

    private void addMarkersToMap(String postcode, List<GeoPoint> geoPoints) {
        // Előző markerek eltávolítása
        if (markerMap.containsKey(postcode)) {
            for (Marker marker : markerMap.get(postcode)) {
                mapView.getOverlays().remove(marker);
            }
            markerMap.remove(postcode);
        }

        // Új markerek hozzáadása
        List<Marker> markers = new ArrayList<>();

        for (GeoPoint point : geoPoints) {
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
            markers.add(marker);
            allGeoPoints.add(point); // Pont hozzáadása az összes pontok listájához
            Log.d("DEBUG", "GeoPoint: " + point.getLatitude() + ", " + point.getLongitude()+ " allgeo: "+allGeoPoints.size());
        }

        // Ha több mint egy pont van, útvonal kérése az OSRM-től
        if (allGeoPoints.size() > 1) {
            GeoPoint startPoint = allGeoPoints.get(allGeoPoints.size() - 2);
            GeoPoint endPoint = allGeoPoints.get(allGeoPoints.size() - 1);
            Log.d("DEBUG", "Start: " + startPoint + ", End: " + endPoint);
            //requestRoute(startPoint, endPoint);
            // Közvetlenül hívd meg a requestRoute függvényt
            try {
                requestRoute(startPoint, endPoint);
                Log.d("DEBUG", "requestRoute meghívva sikeresen.");
            } catch (Exception e) {
                Log.e("DEBUG", "Hiba a requestRoute meghívásakor: " + e.getMessage());
            }
            adjustMapViewToPoints(allGeoPoints);
        }

        markerMap.put(postcode, markers);
        mapView.invalidate(); // Frissíti a térképet
    }

    private void requestRoute(GeoPoint startPoint, GeoPoint endPoint) {
        String url = "https://router.project-osrm.org/route/v1/driving/"
                + startPoint.getLongitude() + "," + startPoint.getLatitude() + ";"
                + endPoint.getLongitude() + "," + endPoint.getLatitude()
                + "?overview=full&geometries=geojson";

        // Hívás a hálózati kéréshez
        new RouteRequestTask().execute(url);
    }

    private void adjustMapViewToPoints(List<GeoPoint> geoPoints) {
        if (geoPoints == null || geoPoints.isEmpty()) {
            return; // Ha nincsenek pontok, ne tegyünk semmit
        }

        // Határozzuk meg a legnagyobb és legkisebb szélességeket és hosszúságokat
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (GeoPoint point : geoPoints) {
            double lat = point.getLatitude();
            double lon = point.getLongitude();

            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lon < minLon) minLon = lon;
            if (lon > maxLon) maxLon = lon;
        }

        GeoPoint southwest = new GeoPoint(minLat, minLon);
        GeoPoint northeast = new GeoPoint(maxLat, maxLon);

        BoundingBox boundingBox = new BoundingBox(northeast.getLatitude(), northeast.getLongitude(),
                southwest.getLatitude(), southwest.getLongitude());
        double latMargin = (northeast.getLatitude() - southwest.getLatitude()) * 0.1;
        double lonMargin = (northeast.getLongitude() - southwest.getLongitude()) * 0.1;

        BoundingBox adjustedBoundingBox = new BoundingBox(
                boundingBox.getLatNorth() + latMargin,
                boundingBox.getLonEast() + lonMargin,
                boundingBox.getLatSouth() - latMargin,
                boundingBox.getLonWest() - lonMargin
        );

        // Állítsuk be a térkép nézetét
        mapView.zoomToBoundingBox(adjustedBoundingBox, true);
    }


    private class RouteRequestTask extends AsyncTask<String, Void, String> {
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

                    drawRoute(routePoints);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("DEBUG", "JSON Parsing error: " + e.getMessage());
                }
            } else {
                Log.d("DEBUG", "Result is null.");
            }
        }

    }

    private void drawRoute(List<GeoPoint> routePoints) {
        Polyline polyline = new Polyline();
        polyline.setPoints(routePoints);
        polyline.setWidth(5.0f); // Vonal vastagságának beállítása
        polyline.setColor(Color.BLUE); // Állítsd be a vonal színét
        polyline.setVisible(true);
        mapView.getOverlays().add(polyline);
        mapView.invalidate();
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
}
