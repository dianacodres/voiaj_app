package com.example.mobile_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("SpellCheckingInspection")
@SuppressLint("SetTextI18n")
public class ExploreActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final String TAG = "HARTA_VOIAJ";

    private static final GeoPoint EUROPE_CENTER = new GeoPoint(48.5, 14.0);
    private static final double EUROPE_ZOOM = 5.2;
    private static final double MY_LOCATION_ZOOM = 15.0;
    private static final double CITY_LEVEL_ZOOM_THRESHOLD = 10.0;
    private static final double FOCUS_CITY_ZOOM = 13.5;

    private MapView mapView;
    private View cardMapPopup;
    private LinearLayout layoutCardsContainer;
    private TextView txtPopupCityName;
    private TextView txtPopupLabel;
    private TextView txtPopupDescription; // LINIE NOUĂ: Referință pentru descriere dinamică
    private ImageView imgPopupCity;        // LINIE NOUĂ: Referință pentru imagine dinamică popup

    private final List<TextView> regionChips = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final List<City> allCitiesList = new ArrayList<>();
    private final List<Marker> activeCityMarkers = new ArrayList<>();
    private final List<Marker> activePlaceMarkers = new ArrayList<>();
    private final Set<String> activeFilters = new HashSet<>();
    private String focusedCityId = null;
    private City currentSelectedCity = null;

    private Marker userLocationMarker;
    private Bitmap rawArrowBitmap = null;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activitatea a pornit.");

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, getSharedPreferences("osmdroid", MODE_PRIVATE));
        setContentView(R.layout.activity_explore);

        mapView = findViewById(R.id.map);
        cardMapPopup = findViewById(R.id.cardMapPopup);
        layoutCardsContainer = findViewById(R.id.layoutCardsContainer);
        txtPopupCityName = findViewById(R.id.txtPopupCityName);
        txtPopupLabel = findViewById(R.id.txtPopupLabel);
        txtPopupDescription = findViewById(R.id.txtPopupDescription); // Legare dinamică descriptor
        imgPopupCity = findViewById(R.id.imgPopupCity);               // Legare dinamică imagine popup

        if (cardMapPopup != null) {
            cardMapPopup.setVisibility(View.GONE);
        }

        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getController().setZoom(EUROPE_ZOOM);
        mapView.getController().setCenter(EUROPE_CENTER);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        setupButtons();
        setupFiltersAndRegions();
        setupBottomNavigation();
        setupZoomListener();
        setupCategoryChipsListener();

        initUserLocationMarker();
        preloadAllDataFromFirestore();

        View btnPopupDetails = findViewById(R.id.btnPopupDetails);
        if (btnPopupDetails != null) {
            btnPopupDetails.setOnClickListener(v -> {
                if (currentSelectedCity != null) {
                    Intent intent = new Intent(ExploreActivity.this, CityDetailActivity.class);
                    intent.putExtra("cityId", currentSelectedCity.getId());
                    intent.putExtra("countryId", currentSelectedCity.getCountryId());
                    startActivity(intent);
                }
            });
        }
    }

    private void preloadAllDataFromFirestore() {
        db.collectionGroup("cities").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                allCitiesList.clear();
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    Double latitude = doc.getDouble("lat");
                    Double longitude = doc.getDouble("lon");
                    String cityName = doc.getString("name");

                    if (latitude != null && longitude != null && cityName != null) {
                        City city = new City();
                        city.setId(doc.getId());
                        city.setName(cityName);
                        city.setLat(latitude);
                        city.setLon(longitude);
                        city.setCountryId(doc.getString("countryId"));
                        city.setTagline(doc.getString("tagline"));
                        city.setRating(doc.getDouble("rating"));
                        city.setImageUrl(doc.getString("imageUrl"));
                        city.setDescription(doc.getString("description"));

                        allCitiesList.add(city);
                    }
                }
                runOnUiThread(this::displayAllLoadedCities);
            }
        });

        db.collectionGroup("attractions").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    createAndCachePlaceMarker(doc);
                }
                runOnUiThread(this::applyVisibilityAndFilters);
            }
        });

        db.collectionGroup("restaurants").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    createAndCachePlaceMarker(doc);
                }
                runOnUiThread(this::applyVisibilityAndFilters);
            }
        });
    }

    private void createAndCachePlaceMarker(DocumentSnapshot doc) {
        Double latitude = doc.getDouble("lat");
        Double longitude = doc.getDouble("lon");
        String name = doc.getString("name");
        String catString = doc.getString("category");
        String cityId = doc.getString("cityId");

        if (latitude == null || longitude == null || name == null) return;

        int glyphResId = R.drawable.ic_historical;
        if (catString != null) {
            switch (catString.toLowerCase()) {
                case "museum": glyphResId = R.drawable.ic_museum; break;
                case "restaurant": case "brunch": glyphResId = R.drawable.ic_restaurant; break;
                case "cafe": glyphResId = R.drawable.ic_cafe; break;
                case "monument": glyphResId = R.drawable.ic_monument; break;
                case "nature": glyphResId = R.drawable.ic_nature; break;
                case "historical": case "neighborhood": case "religious": default: glyphResId = R.drawable.ic_historical; break;
            }
        }

        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(latitude, longitude));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        String normCategory = (catString != null) ? catString.toLowerCase() : "historical";
        if (normCategory.equals("brunch")) {
            normCategory = "restaurant";
        }
        marker.setSnippet(normCategory);
        marker.setTitle(name);
        marker.setId(cityId != null ? cityId : "");

        Drawable icon = ContextCompat.getDrawable(this, glyphResId);
        if (icon != null) {
            int sizePx = (int) (36 * getResources().getDisplayMetrics().density);
            Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            icon.setBounds(0, 0, sizePx, sizePx);
            icon.draw(c);
            marker.setIcon(new BitmapDrawable(getResources(), b));
        }

        marker.setVisible(false);
        synchronized (activePlaceMarkers) {
            activePlaceMarkers.add(marker);
        }
        runOnUiThread(() -> mapView.getOverlays().add(marker));
    }

    private void displayAllLoadedCities() {
        for (Marker m : activeCityMarkers) mapView.getOverlays().remove(m);
        activeCityMarkers.clear();
        layoutCardsContainer.removeAllViews();

        for (City city : allCitiesList) {
            displayCityOnMapAndBottom(city);
        }
        mapView.invalidate();
    }

    private void displayCityOnMapAndBottom(City city) {
        if (city.getLat() == null || city.getLon() == null) return;

        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(city.getLat(), city.getLon()));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setInfoWindow(null);

        int sizePx = 32;
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaint.setColor(Color.parseColor("#023859"));
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, 11f, ballPaint);
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, 11f, strokePaint);
        marker.setIcon(new BitmapDrawable(getResources(), bitmap));

        marker.setOnMarkerClickListener((m, map) -> {
            showDetailsPopup(city);
            focusOnCity(city);
            return true;
        });

        activeCityMarkers.add(marker);
        mapView.getOverlays().add(marker);

        View cardView = LayoutInflater.from(this).inflate(R.layout.item_recommended_city, layoutCardsContainer, false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (180 * getResources().getDisplayMetrics().density),
                (int) (100 * getResources().getDisplayMetrics().density)
        );
        params.setMargins(8, 0, 8, 0);
        cardView.setLayoutParams(params);

        TextView txtRecName = cardView.findViewById(R.id.txtRecName);
        TextView txtRecMatch = cardView.findViewById(R.id.txtRecMatch);
        TextView txtRecRating = cardView.findViewById(R.id.txtRecRating);
        ImageView imgRecCity = cardView.findViewById(R.id.imgRecCity);

        if (txtRecName != null) txtRecName.setText(city.getName());
        if (txtRecMatch != null && city.getTagline() != null) {
            txtRecMatch.setText(city.getTagline());
        }

        if (txtRecRating != null) {
            Double rating = city.getRating();
            txtRecRating.setText(rating != null ? String.format(Locale.US, "★ %.1f", rating) : "★ 5.0");
        }

        if (imgRecCity != null) {
            String imgName = city.getImageUrl();
            int resId = 0;
            if (imgName != null && !imgName.trim().isEmpty()) {
                imgName = imgName.replace(".jpg", "").replace(".png", "").trim();
                resId = getResources().getIdentifier(imgName, "drawable", getPackageName());
            }
            imgRecCity.setImageResource(resId != 0 ? resId : R.drawable.rome);
        }

        cardView.setOnClickListener(v -> {
            showDetailsPopup(city);
            focusOnCity(city);
        });
        layoutCardsContainer.addView(cardView);
    }

    private void showDetailsPopup(City city) {
        this.currentSelectedCity = city;
        if (cardMapPopup != null) {
            if (txtPopupCityName != null) txtPopupCityName.setText(city.getName());
            if (txtPopupLabel != null && city.getCountryId() != null) {
                txtPopupLabel.setText(city.getCountryId().toUpperCase());
            }

            // REZOLVARE POPUP DESCRIPTIV DINAMIC: Extrage descrierea, formatează lungimea cu truncation (...)
            if (txtPopupDescription != null) {
                String desc = city.getDescription();
                if (desc != null && !desc.trim().isEmpty()) {
                    // Dacă textul este mai lung de 65 caractere, îl tăiem curat și îi adăugăm elipsa
                    if (desc.length() > 65) {
                        txtPopupDescription.setText(desc.substring(0, 62).trim() + "...");
                    } else {
                        txtPopupDescription.setText(desc.trim() + "...");
                    }
                } else {
                    txtPopupDescription.setText("Discover the beauty of " + city.getName() + "...");
                }
            }

            // REZOLVARE POPUP IMAGINE DINAMICĂ: Actualizează miniatura din interiorul popup-ului cu imaginea orașului ales
            if (imgPopupCity != null) {
                String imgName = city.getImageUrl();
                int resId = 0;
                if (imgName != null && !imgName.trim().isEmpty()) {
                    imgName = imgName.replace(".jpg", "").replace(".png", "").trim();
                    resId = getResources().getIdentifier(imgName, "drawable", getPackageName());
                }
                imgPopupCity.setImageResource(resId != 0 ? resId : R.drawable.rome);
            }

            cardMapPopup.setVisibility(View.VISIBLE);
        }
    }

    private void focusOnCity(City city) {
        this.focusedCityId = city.getId();
        double targetZoom = FOCUS_CITY_ZOOM;
        double offsetLat = 0.012;
        GeoPoint adjustedCenter = new GeoPoint(city.getLat() - offsetLat, city.getLon());

        mapView.getController().animateTo(adjustedCenter, targetZoom, 800L);
        applyVisibilityAndFilters();
    }

    private void applyVisibilityAndFilters() {
        double zoom = mapView.getZoomLevelDouble();
        boolean isZoomedIn = zoom >= CITY_LEVEL_ZOOM_THRESHOLD;

        for (Marker m : activeCityMarkers) {
            m.setEnabled(!isZoomedIn);
        }

        for (Marker m : activePlaceMarkers) {
            String cityIdOfMarker = m.getId();
            String category = m.getSnippet();

            if (isZoomedIn && focusedCityId != null && focusedCityId.equalsIgnoreCase(cityIdOfMarker)) {
                boolean shouldShow = category != null && activeFilters.contains(category);
                m.setEnabled(shouldShow);
                m.setVisible(shouldShow);
            } else {
                m.setEnabled(false);
                m.setVisible(false);
            }
        }
        mapView.invalidate();
    }

    private void setupCategoryChipsListener() {
        activeFilters.add("museum"); activeFilters.add("restaurant");
        activeFilters.add("historical"); activeFilters.add("nature"); activeFilters.add("monument"); activeFilters.add("cafe");

        int[] chipIds = {R.id.chipMuseum, R.id.chipRestaurant, R.id.chipHistorical, R.id.chipNature, R.id.chipCafe, R.id.chipMonument};
        String[] categories = {"museum", "restaurant", "historical", "nature", "cafe", "monument"};

        for (int i = 0; i < chipIds.length; i++) {
            final String currentCategory = categories[i];
            View chipView = findViewById(chipIds[i]);
            if (chipView != null) {
                chipView.setOnClickListener(v -> {
                    if (activeFilters.contains(currentCategory)) {
                        activeFilters.remove(currentCategory);
                        v.setAlpha(0.4f);
                    } else {
                        activeFilters.add(currentCategory);
                        v.setAlpha(1.0f);
                    }
                    applyVisibilityAndFilters();
                });
            }
        }
    }

    private void filterRegionOnMapAndCards(String regionKey) {
        layoutCardsContainer.removeAllViews();
        for (City city : allCitiesList) {
            if (city.getCountryId() == null) continue;
            boolean matches = false;
            String countryLower = city.getCountryId().toLowerCase();

            switch (regionKey.toLowerCase()) {
                case "western":
                    matches = countryLower.contains("france") || countryLower.contains("belgium") || countryLower.contains("uk") || countryLower.contains("netherlands");
                    break;
                case "central":
                    matches = countryLower.contains("germany") || countryLower.contains("austria") || countryLower.contains("switzerland");
                    break;
                case "southern":
                case "mediterranean":
                    matches = countryLower.contains("italy") || countryLower.contains("spain") || countryLower.contains("portugal") || countryLower.contains("greece");
                    break;
                case "eastern":
                    matches = countryLower.contains("romania") || countryLower.contains("bulgaria") || countryLower.contains("moldova");
                    break;
                case "northern":
                    matches = countryLower.contains("sweden") || countryLower.contains("norway") || countryLower.contains("finland") || countryLower.contains("denmark");
                    break;
            }

            if (matches) {
                displayCityOnMapAndBottom(city);
            }
        }
        focusedCityId = null;
        if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);
        applyVisibilityAndFilters();
    }

    private void initUserLocationMarker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, this);
            }

            userLocationMarker = new Marker(mapView);
            userLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            userLocationMarker.setInfoWindow(null);

            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation_arrow);
            if (drawable instanceof BitmapDrawable) {
                rawArrowBitmap = ((BitmapDrawable) drawable).getBitmap();
                updateUserMarkerSize(mapView.getZoomLevelDouble());
            }
            mapView.getOverlays().add(userLocationMarker);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        }
    }

    private void updateUserMarkerSize(double zoomLevel) {
        if (userLocationMarker == null || rawArrowBitmap == null) return;
        int dpSize;
        if (zoomLevel <= 5.5) dpSize = 6;
        else if (zoomLevel <= 8.5) dpSize = 8;
        else if (zoomLevel <= 12.0) dpSize = 10;
        else dpSize = 12;

        int pixelSize = (int) (dpSize * getResources().getDisplayMetrics().density);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(rawArrowBitmap, pixelSize, pixelSize, true);
        userLocationMarker.setIcon(new BitmapDrawable(getResources(), scaledBitmap));
    }

    private void setupFiltersAndRegions() {
        TextView filterEurope = findViewById(R.id.filterAll);
        TextView filterWestern = findViewById(R.id.filterWesternEurope);
        TextView filterCentral = findViewById(R.id.filterCentralEurope);
        TextView filterEastern = findViewById(R.id.filterEasternEurope);
        TextView filterNorthern = findViewById(R.id.filterNorthernEurope);
        TextView filterSouthern = findViewById(R.id.filterSouthernEurope);
        TextView filterMed = findViewById(R.id.filterMediterranean);

        if (filterEurope != null) regionChips.add(filterEurope);
        if (filterWestern != null) regionChips.add(filterWestern);
        if (filterCentral != null) regionChips.add(filterCentral);
        if (filterEastern != null) regionChips.add(filterEastern);
        if (filterNorthern != null) regionChips.add(filterNorthern);
        if (filterSouthern != null) regionChips.add(filterSouthern);
        if (filterMed != null) regionChips.add(filterMed);

        if (filterEurope != null) {
            filterEurope.setOnClickListener(v -> {
                updateSelectedChipVisuals(filterEurope);
                focusedCityId = null;
                if (cardMapPopup != null) {
                    cardMapPopup.setVisibility(View.GONE);
                }
                mapView.getController().animateTo(EUROPE_CENTER, EUROPE_ZOOM, 700L);
                displayAllLoadedCities();
            });
        }

        setOnRegionClickListener(filterWestern, new GeoPoint(48.5, 2.5), 6.0, "western");
        setOnRegionClickListener(filterCentral, new GeoPoint(49.8, 15.0), 6.0, "central");
        setOnRegionClickListener(filterEastern, new GeoPoint(47.5, 25.5), 5.8, "eastern");
        setOnRegionClickListener(filterNorthern, new GeoPoint(62.0, 15.0), 5.2, "northern");
        setOnRegionClickListener(filterSouthern, new GeoPoint(41.9, 12.6), 6.0, "southern");
        setOnRegionClickListener(filterMed, new GeoPoint(39.0, 18.0), 5.8, "mediterranean");
    }

    private void setOnRegionClickListener(TextView chip, GeoPoint center, double zoomLevel, String regionKey) {
        if (chip != null) {
            chip.setOnClickListener(v -> {
                updateSelectedChipVisuals(chip);
                mapView.getController().animateTo(center, zoomLevel, 700L);
                filterRegionOnMapAndCards(regionKey);
            });
        }
    }

    private void updateSelectedChipVisuals(TextView selectedChip) {
        for (TextView chip : regionChips) {
            if (chip == selectedChip) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.blue));
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
                chip.setTextColor(Color.parseColor("#011C40"));
            }
        }
    }

    private void setupButtons() {
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> mapView.getController().zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> mapView.getController().zoomOut());

        findViewById(R.id.btnMyLocation).setOnClickListener(v -> {
            if (userLocationMarker != null && userLocationMarker.getPosition() != null) {
                // REZOLVARE POPUP ME: Închidem pop-up-ul de detalii instantaneu când utilizatorul își caută propria locație
                if (cardMapPopup != null) {
                    cardMapPopup.setVisibility(View.GONE);
                }

                findAndFocusNearestCity(userLocationMarker.getPosition());
                mapView.getController().animateTo(userLocationMarker.getPosition(), MY_LOCATION_ZOOM, 800L);
            } else {
                Toast.makeText(this, "Se preia semnalul GPS...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void findAndFocusNearestCity(GeoPoint userPoint) {
        City nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (City c : allCitiesList) {
            double dist = Math.pow(c.getLat() - userPoint.getLatitude(), 2) + Math.pow(c.getLon() - userPoint.getLongitude(), 2);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = c;
            }
        }
        if (nearest != null && minDistance < 1.5) {
            this.focusedCityId = nearest.getId();
        } else {
            this.focusedCityId = "rome";
        }
        applyVisibilityAndFilters();
    }

    private void setupZoomListener() {
        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) { return false; }
            @Override
            public boolean onZoom(ZoomEvent event) {
                updateUserMarkerSize(event.getZoomLevel());
                applyVisibilityAndFilters();
                return false;
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR && userLocationMarker != null) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float azimuthDegrees = (float) Math.toDegrees(orientationAngles[0]);
            userLocationMarker.setRotation(-azimuthDegrees);
            mapView.invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (userLocationMarker != null) {
            GeoPoint newPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
            userLocationMarker.setPosition(newPoint);
            mapView.invalidate();
        }
    }

    private void setupBottomNavigation() {
        // Legăm containerele LinearLayout prin ID-urile lor directe din noul tău XML
        LinearLayout btnDiscover = findViewById(R.id.btnDiscover);
        LinearLayout btnMap      = findViewById(R.id.btnMap);
        LinearLayout btnWishlist = findViewById(R.id.btnWishlist);
        LinearLayout btnAI       = findViewById(R.id.btnAI);

        // ── CONFIGURARE STĂRI VIZUALE (Map este fila curentă activă) ──
        int activeColor = ContextCompat.getColor(this, R.color.light_turq_1);
        int inactiveColor = Color.parseColor("#AAAAAA");

        // 1. Sincronizăm starea vizuală pentru Map (Activ)
        if (btnMap != null) {
            ImageView imgMap = (ImageView) btnMap.getChildAt(0);
            TextView txtMap = (TextView) btnMap.getChildAt(1);

            if (imgMap != null) imgMap.setColorFilter(activeColor);
            if (txtMap != null) {
                txtMap.setTextColor(activeColor);
                txtMap.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }

        // 2. Ne asigurăm că celelalte butoane au culoarea neutră de inactiv (Gri)
        resetInactiveTabVisuals(btnDiscover, inactiveColor);
        resetInactiveTabVisuals(btnWishlist, inactiveColor);
        resetInactiveTabVisuals(btnAI, inactiveColor);

        // ── ASCULTĂTORI CLICK PENTRU SCHIMBAREA ECRANELOR (Inclusiv AI) ──
        if (btnDiscover != null) {
            btnDiscover.setOnClickListener(v -> {
                Intent intent = new Intent(ExploreActivity.this, DiscoverActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        if (btnWishlist != null) {
            btnWishlist.setOnClickListener(v -> {
                Intent intent = new Intent(ExploreActivity.this, JourneyActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        if (btnAI != null) {
            btnAI.setOnClickListener(v -> {
                // Trimite utilizatorul direct către AIHubActivity din proiectul tău
                Intent intent = new Intent(ExploreActivity.this, AIHubActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }

    // Metodă utilitară privată pentru a curăța stările reziduale ale fetelor inactive
    private void resetInactiveTabVisuals(LinearLayout tabLayout, int inactiveColor) {
        if (tabLayout != null) {
            ImageView img = (ImageView) tabLayout.getChildAt(0);
            TextView txt = (TextView) tabLayout.getChildAt(1);

            if (img != null) img.setColorFilter(inactiveColor);
            if (txt != null) {
                txt.setTextColor(inactiveColor);
                txt.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initUserLocationMarker();
            } else {
                Toast.makeText(this, "Location permission denied - can't show your position on the map.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
