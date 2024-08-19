package com.example.ddelivery;

import android.os.AsyncTask;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GeocodingTask extends AsyncTask<String, Void, List<GeoPoint>> {

    private final OnGeocodingCompletedListener listener;

    public GeocodingTask(OnGeocodingCompletedListener listener) {
        this.listener = listener;
    }

    @Override
    protected List<GeoPoint> doInBackground(String... params) {
        String postcode = params[0];
        List<GeoPoint> geoPoints = new ArrayList<>();

        try {
            // Nominatim API hívás
            String apiUrl = "https://nominatim.openstreetmap.org/search?format=json&q=" + postcode;
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            // JSON feldolgozás
            JSONArray jsonArray = new JSONArray(response.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                double lat = jsonObject.getDouble("lat");
                double lon = jsonObject.getDouble("lon");
                geoPoints.add(new GeoPoint(lat, lon));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return geoPoints;
    }

    @Override
    protected void onPostExecute(List<GeoPoint> geoPoints) {
        if (listener != null) {
            listener.onGeocodingCompleted(geoPoints);
        }
    }

    public interface OnGeocodingCompletedListener {
        void onGeocodingCompleted(List<GeoPoint> geoPoints);
    }
}
