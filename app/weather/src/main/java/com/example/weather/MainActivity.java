package com.example.weather;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String WEATHER_PREFS = "weather_prefs";
    private static final String KEY_LAST_LATITUDE = "last_latitude";
    private static final String KEY_LAST_LONGITUDE = "last_longitude";
    private static final float DEFAULT_MAP_ZOOM = 12f;
    private static final String TEMPERATURE_UNIT_CELSIUS = "c";

    private MapView mapView;
    private GoogleMap googleMap;
    private TextView textView;
    private TextView locationTextView;
    private TextView tempTextView;
    private TextView humidTextView;
    private TextView appTempTextView;
    private TextView pressureTextView;
    private TextView cloudTextView;
    private TextView rainTextView;
    private TextView windTextView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        textView = findViewById(R.id.textView);
        locationTextView = findViewById(R.id.locationTextView);
        tempTextView = findViewById(R.id.tempTextView);
        humidTextView = findViewById(R.id.humidTextView);
        appTempTextView = findViewById(R.id.appTempTextView);
        pressureTextView = findViewById(R.id.pressureTextView);
        cloudTextView = findViewById(R.id.cloudTextView);
        rainTextView = findViewById(R.id.rainTextView);
        windTextView = findViewById(R.id.windTextView);
        progressBar = findViewById(R.id.progressBar);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(map -> {
            googleMap = map;
            googleMap.setOnCameraIdleListener(this::refreshWeatherAtMapCenter);

            if (moveToSavedMapCenter()) {
                return;
            }

            if (hasLocationPermission()) {
                moveToCurrentLocation();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE
                );
            }
        });

        findViewById(R.id.refreshButton).setOnClickListener(view -> {
            locationTextView.setText("Đang cập nhật...");
            refreshWeatherAtMapCenter();
        });
        findViewById(R.id.settingsButton).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class))
        );
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean moveToSavedMapCenter() {
        SharedPreferences sharedPreferences = getSharedPreferences(WEATHER_PREFS, MODE_PRIVATE);
        if (!sharedPreferences.contains(KEY_LAST_LATITUDE) || !sharedPreferences.contains(KEY_LAST_LONGITUDE)) {
            return false;
        }

        double latitude = Double.longBitsToDouble(sharedPreferences.getLong(KEY_LAST_LATITUDE, 0L));
        double longitude = Double.longBitsToDouble(sharedPreferences.getLong(KEY_LAST_LONGITUDE, 0L));
        moveMapAndRefreshWeather(new LatLng(latitude, longitude), false);
        return true;
    }

    private void moveToCurrentLocation() {
        if (!hasLocationPermission()) {
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null && googleMap != null) {
                LatLng myLocation = new LatLng(location.getLatitude(), location.getLongitude());
                moveMapAndRefreshWeather(myLocation, true);
            }
        });
    }

    private void moveMapAndRefreshWeather(LatLng location, boolean showCurrentLocationMarker) {
        if (googleMap == null) {
            return;
        }

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_MAP_ZOOM));
        if (showCurrentLocationMarker) {
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions().position(location).title("You are here"));
        }
        saveLastMapCenter(location);
        showInfo(location);
    }

    private void refreshWeatherAtMapCenter() {
        if (googleMap == null) {
            return;
        }

        LatLng centerLocation = googleMap.getCameraPosition().target;
        saveLastMapCenter(centerLocation);
        showInfo(centerLocation);
    }

    private void saveLastMapCenter(LatLng location) {
        getSharedPreferences(WEATHER_PREFS, MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_LATITUDE, Double.doubleToRawLongBits(location.latitude))
                .putLong(KEY_LAST_LONGITUDE, Double.doubleToRawLongBits(location.longitude))
                .apply();
    }

    private void showInfo(LatLng location) {
        textView.setText(convertToDegreeMinutesSeconds(location.longitude) + ", " + convertToDegreeMinutesSeconds(location.latitude));
        locationTextView.setText(getCityName(location.latitude, location.longitude));

        progressBar.setVisibility(View.VISIBLE);
        tempTextView.setVisibility(View.INVISIBLE);
        humidTextView.setVisibility(View.INVISIBLE);
        appTempTextView.setVisibility(View.INVISIBLE);
        pressureTextView.setVisibility(View.INVISIBLE);
        cloudTextView.setVisibility(View.INVISIBLE);
        rainTextView.setVisibility(View.INVISIBLE);
        windTextView.setVisibility(View.INVISIBLE);

        new Thread(() -> {
            HttpURLConnection urlConnection = null;
            try {
                String apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + location.latitude + "&longitude=" + location.longitude + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,showers,snowfall,is_day,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m";
                URL url = new URL(apiUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject baseJsonResponse = new JSONObject(response.toString());
                JSONObject currentJsonObject = baseJsonResponse.getJSONObject("current");
                double temperature2m = currentJsonObject.getDouble("temperature_2m");
                double apparentTemperature = currentJsonObject.getDouble("apparent_temperature");
                double relativeHumidity2m = currentJsonObject.getDouble("relative_humidity_2m");
                double rain = currentJsonObject.getDouble("rain");
                double cloudCover = currentJsonObject.getDouble("cloud_cover");
                double surfacePressure = currentJsonObject.getDouble("surface_pressure");
                double windSpeed10m = currentJsonObject.getDouble("wind_speed_10m");

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tempTextView.setVisibility(View.VISIBLE);
                    humidTextView.setVisibility(View.VISIBLE);
                    appTempTextView.setVisibility(View.VISIBLE);
                    pressureTextView.setVisibility(View.VISIBLE);
                    cloudTextView.setVisibility(View.VISIBLE);
                    rainTextView.setVisibility(View.VISIBLE);
                    windTextView.setVisibility(View.VISIBLE);
                    tempTextView.setText("🌡️" + formatTemperature(temperature2m));
                    humidTextView.setText("💧" + (int) relativeHumidity2m + "%");
                    appTempTextView.setText(getString(R.string.feels_like) + "\n" + formatTemperature(apparentTemperature));
                    pressureTextView.setText(getString(R.string.pressure) + "\n" + (int) surfacePressure + "hPa");
                    cloudTextView.setText("☁\n" + (int) cloudCover + "%");
                    rainTextView.setText("🌧\n" + (int) rain + "mm");
                    windTextView.setText("🌪\n" + (int) windSpeed10m + "km/h");
                });
            } catch (Exception e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }).start();
    }

    public static String convertToDegreeMinutesSeconds(double coordinate) {
        int degree = (int) coordinate;
        coordinate = (coordinate - degree) * 60;
        int minute = (int) coordinate;
        double second = (coordinate - minute) * 60;
        return degree + "° " + minute + "' " + (int) second + "\"";
    }

    private String formatTemperature(double celsius) {
        SharedPreferences sharedPreferences = getSharedPreferences(SettingsFragment.PREF_NAME, MODE_PRIVATE);
        String temperatureUnit = sharedPreferences.getString(
                SettingsFragment.KEY_TEMPERATURE_UNIT,
                TEMPERATURE_UNIT_CELSIUS
        );
        if ("f".equals(temperatureUnit)) {
            return (int) Math.round(celsius * 9 / 5 + 32) + "°F";
        }
        return (int) Math.round(celsius) + "°C";
    }

    private String getCityName(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                if (address.getLocality() != null) {
                    return address.getLocality();
                }
                if (address.getSubAdminArea() != null) {
                    return address.getSubAdminArea();
                }
                if (address.getAdminArea() != null) {
                    return address.getAdminArea();
                }
                if (address.getCountryName() != null) {
                    return address.getCountryName();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return convertToDegreeMinutesSeconds(longitude) + ", " + convertToDegreeMinutesSeconds(latitude);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (googleMap != null) {
            refreshWeatherAtMapCenter();
        }
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
