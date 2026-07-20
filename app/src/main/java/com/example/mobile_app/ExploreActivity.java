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
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("SpellCheckingInspection")
@SuppressLint("SetTextI18n")
public class ExploreActivity extends AppCompatActivity implements SensorEventListener, LocationListener, OnRequestPermissionsResultCallback, OnMapReadyCallback {

    private static final LatLng EUROPE_CENTER = new LatLng(48.5, 14.0);
    private static final double EUROPE_ZOOM = 2.8;
    private static final double MY_LOCATION_ZOOM = 15.0;
    private static final double FOCUS_CITY_ZOOM = 12.5;
    private static final double CITY_LEVEL_ZOOM_THRESHOLD = 9.5;

    private MapView mapView;
    private MapLibreMap mapLibreMap;

    private View cardMapPopup;
    private LinearLayout layoutCardsContainer;
    private TextView txtPopupCityName;
    private TextView txtPopupLabel;
    private TextView txtPopupDescription;
    private ImageView imgPopupCity;

    // Pop-up pentru Pin
    private View cardPinPopup;
    private ImageView imgPinPhoto;
    private TextView txtPinType;
    private TextView txtPinName;

    private final Handler pinPopupHandler = new Handler(Looper.getMainLooper());
    private Runnable pinPopupAutoCloseRunnable;

    private final List<TextView> regionChips = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final List<City> allCitiesList = new ArrayList<>();
    private final List<Marker> activeCityMarkers = new ArrayList<>();
    private final List<Marker> activePlaceMarkers = new ArrayList<>();
    private final Set<String> activeFilters = new HashSet<>();

    // Mapăm markerul de atracție de documentul său Firestore
    private final Map<Marker, DocumentSnapshot> placeMarkerDataMap = new HashMap<>();
    private final List<DocumentSnapshot> loadedPlaceDocs = new ArrayList<>();

    private City currentSelectedCity = null;
    private String focusedCityId = null;

    private Marker userLocationMarker;
    private Bitmap rawArrowBitmap = null;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);

        // ── BARĂ STATUS NEAGRĂ ȘI BARĂ NAVIGARE ALBĂ ──
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.WHITE);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        setContentView(R.layout.activity_explore);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        cardMapPopup = findViewById(R.id.cardMapPopup);
        layoutCardsContainer = findViewById(R.id.layoutCardsContainer);
        txtPopupCityName = findViewById(R.id.txtPopupCityName);
        txtPopupLabel = findViewById(R.id.txtPopupLabel);
        txtPopupDescription = findViewById(R.id.txtPopupDescription);
        imgPopupCity = findViewById(R.id.imgPopupCity);

        cardPinPopup = findViewById(R.id.cardPinPopup);
        imgPinPhoto = findViewById(R.id.imgPinPhoto);
        txtPinType = findViewById(R.id.txtPinType);
        txtPinName = findViewById(R.id.txtPinName);

        if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);
        if (cardPinPopup != null) cardPinPopup.setVisibility(View.GONE);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        setupRegionChips();
        setupButtons();
        setupBottomNavigation();
        setupCategoryChipsListener();

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

        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;

        String freeStyleUrl = "https://tiles.openfreemap.org/styles/liberty";

        mapLibreMap.setStyle(new Style.Builder().fromUri(freeStyleUrl), style -> {
            mapLibreMap.setCameraPosition(new CameraPosition.Builder()
                    .target(EUROPE_CENTER)
                    .zoom(EUROPE_ZOOM)
                    .build());

            initUserLocationMarker();
            preloadAllDataFromFirestore();
            setupCameraChangeListener();
            setupMapClickListener();
        });
    }

    // ── DISPARIȚIE POP-UP LA CLICK ORIUNDE PE HARTĂ ──
    private void setupMapClickListener() {
        if (mapLibreMap != null) {
            mapLibreMap.addOnMapClickListener(point -> {
                hidePinPopup();
                if (cardMapPopup != null) {
                    cardMapPopup.setVisibility(View.GONE);
                }
                return false;
            });
        }
    }

    private void setupCameraChangeListener() {
        if (mapLibreMap != null) {
            mapLibreMap.addOnCameraIdleListener(() -> {
                double currentZoom = mapLibreMap.getCameraPosition().zoom;
                if (currentZoom < CITY_LEVEL_ZOOM_THRESHOLD) {
                    clearPlaceMarkers();
                    loadedPlaceDocs.clear();
                }
            });
        }
    }

    private void clearPlaceMarkers() {
        for (Marker m : activePlaceMarkers) {
            mapLibreMap.removeMarker(m);
        }
        activePlaceMarkers.clear();
        placeMarkerDataMap.clear();
    }

    private void initUserLocationMarker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, this);
            }

            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_navigation_arrow);
            if (drawable != null) {
                int pxSize = (int) (14 * getResources().getDisplayMetrics().density);
                Bitmap bitmap = Bitmap.createBitmap(pxSize, pxSize, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, pxSize, pxSize);
                drawable.draw(canvas);
                rawArrowBitmap = bitmap;
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR && userLocationMarker != null && rawArrowBitmap != null) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float azimuthDegrees = (float) Math.toDegrees(orientationAngles[0]);

            Bitmap rotatedBitmap = rotateBitmap(rawArrowBitmap, azimuthDegrees);
            Icon icon = IconFactory.getInstance(this).fromBitmap(rotatedBitmap);
            userLocationMarker.setIcon(icon);
        }
    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (mapLibreMap == null) return;
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());

        if (userLocationMarker == null && rawArrowBitmap != null) {
            Icon icon = IconFactory.getInstance(this).fromBitmap(rawArrowBitmap);
            userLocationMarker = mapLibreMap.addMarker(new MarkerOptions().position(newPoint).setIcon(icon));
        } else if (userLocationMarker != null) {
            userLocationMarker.setPosition(newPoint);
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
    }

    private void displayAllLoadedCities() {
        if (mapLibreMap == null) return;

        for (Marker m : activeCityMarkers) mapLibreMap.removeMarker(m);
        activeCityMarkers.clear();
        layoutCardsContainer.removeAllViews();

        for (City city : allCitiesList) {
            displayCityOnMapAndBottom(city);
        }
    }

    private void displayCityOnMapAndBottom(City city) {
        if (city.getLat() == null || city.getLon() == null || mapLibreMap == null) return;

        int sizePx = (int) (18 * getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaint.setColor(Color.parseColor("#023859"));
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.5f, ballPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.5f, strokePaint);

        Icon cityIcon = IconFactory.getInstance(this).fromBitmap(bitmap);

        Marker marker = mapLibreMap.addMarker(new MarkerOptions()
                .position(new LatLng(city.getLat(), city.getLon()))
                .setTitle(city.getName())
                .setIcon(cityIcon));

        activeCityMarkers.add(marker);

        View cardView = LayoutInflater.from(this).inflate(R.layout.item_recommended_city, layoutCardsContainer, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (180 * getResources().getDisplayMetrics().density),
                (int) (112 * getResources().getDisplayMetrics().density)
        );
        params.setMargins(8, 0, 8, 0);
        cardView.setLayoutParams(params);

        TextView txtRecName = cardView.findViewById(R.id.txtRecName);
        TextView txtRecMatch = cardView.findViewById(R.id.txtRecMatch);
        TextView txtRecRating = cardView.findViewById(R.id.txtRecRating);
        ImageView imgRecCity = cardView.findViewById(R.id.imgRecCity);

        if (txtRecName != null) txtRecName.setText(city.getName());
        if (txtRecMatch != null && city.getTagline() != null) txtRecMatch.setText(city.getTagline());

        if (txtRecRating != null) {
            Double rating = city.getRating();
            txtRecRating.setText(rating != null ? String.format(Locale.US, "★ %.1f", rating) : "★ 5.0");
        }

        if (imgRecCity != null) {
            String rawUrl = city.getImageUrl();
            if (rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
                Glide.with(this).load(rawUrl).placeholder(R.drawable.rome).error(R.drawable.rome).into(imgRecCity);
            } else {
                imgRecCity.setImageResource(R.drawable.rome);
            }
        }

        cardView.setOnClickListener(v -> {
            showDetailsPopup(city);
            focusOnCity(city);
        });

        layoutCardsContainer.addView(cardView);

        mapLibreMap.setOnMarkerClickListener(m -> {
            for (City c : allCitiesList) {
                if (c.getName().equalsIgnoreCase(m.getTitle())) {
                    showDetailsPopup(c);
                    focusOnCity(c);
                    return true;
                }
            }

            if (placeMarkerDataMap.containsKey(m)) {
                DocumentSnapshot doc = placeMarkerDataMap.get(m);
                if (doc != null) {
                    showPinPopupForDoc(doc, m);
                }
                return true;
            }

            return false;
        });
    }

    private void focusOnCity(City city) {
        if (mapLibreMap == null) return;
        this.currentSelectedCity = city;
        this.focusedCityId = city.getId();

        // Încărcăm mai întâi atracțiile/restaurantele din Firestore
        loadPlacesForCity(city.getId());
    }

    private void loadPlacesForCity(String cityId) {
        clearPlaceMarkers();
        loadedPlaceDocs.clear();

        db.collectionGroup("attractions").get().addOnCompleteListener(task1 -> {
            if (task1.isSuccessful() && task1.getResult() != null) {
                for (DocumentSnapshot doc : task1.getResult().getDocuments()) {
                    String docCityId = doc.getString("cityId");
                    if (docCityId != null && docCityId.equalsIgnoreCase(cityId)) {
                        loadedPlaceDocs.add(doc);
                    }
                }
            }

            db.collectionGroup("restaurants").get().addOnCompleteListener(task2 -> {
                if (task2.isSuccessful() && task2.getResult() != null) {
                    for (DocumentSnapshot doc : task2.getResult().getDocuments()) {
                        String docCityId = doc.getString("cityId");
                        if (docCityId != null && docCityId.equalsIgnoreCase(cityId)) {
                            loadedPlaceDocs.add(doc);
                        }
                    }
                }
                // Randăm pinii și facem zoom pe aglomerare pe firul principal
                runOnUiThread(this::renderFilteredPlaceMarkers);
            });
        });
    }

    // ── FILTRARE DINAMICĂ MARKERE PER CATEGORIE ──
    private void renderFilteredPlaceMarkers() {
        clearPlaceMarkers();

        double sumLat = 0;
        double sumLon = 0;
        int count = 0;

        for (DocumentSnapshot doc : loadedPlaceDocs) {
            String catString = doc.getString("category");
            String normCategory = (catString != null) ? catString.toLowerCase() : "historical";
            if (normCategory.equals("brunch")) normCategory = "restaurant";

            if (activeFilters.isEmpty() || activeFilters.contains(normCategory)) {
                Double lat = doc.getDouble("lat");
                Double lon = doc.getDouble("lon");

                if (lat != null && lon != null) {
                    addPlaceMarkerFromDoc(doc);
                    sumLat += lat;
                    sumLon += lon;
                    count++;
                }
            }
        }

        // Calculare punct central (aglomerare de pini)
        LatLng targetCenter;
        if (count > 0) {
            targetCenter = new LatLng(sumLat / count, sumLon / count);
        } else if (currentSelectedCity != null) {
            targetCenter = new LatLng(currentSelectedCity.getLat(), currentSelectedCity.getLon());
        } else {
            return;
        }

        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetCenter, FOCUS_CITY_ZOOM), 750);
    }

    private void addPlaceMarkerFromDoc(DocumentSnapshot doc) {
        Double lat = doc.getDouble("lat");
        Double lon = doc.getDouble("lon");
        String name = doc.getString("name");
        String catString = doc.getString("category");

        if (lat == null || lon == null || name == null) return;

        int glyphResId = R.drawable.ic_historical;
        if (catString != null) {
            switch (catString.toLowerCase()) {
                case "museum": glyphResId = R.drawable.ic_museum; break;
                case "restaurant": case "brunch": glyphResId = R.drawable.ic_restaurant; break;
                case "cafe": glyphResId = R.drawable.ic_cafe; break;
                case "monument": glyphResId = R.drawable.ic_monument; break;
                case "nature": glyphResId = R.drawable.ic_nature; break;
                default: glyphResId = R.drawable.ic_historical; break;
            }
        }

        Drawable iconDrawable = ContextCompat.getDrawable(this, glyphResId);
        if (iconDrawable != null) {
            int sizePx = (int) (26 * getResources().getDisplayMetrics().density);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            iconDrawable.setBounds(0, 0, sizePx, sizePx);
            iconDrawable.draw(canvas);

            Icon icon = IconFactory.getInstance(this).fromBitmap(bitmap);

            Marker marker = mapLibreMap.addMarker(new MarkerOptions()
                    .position(new LatLng(lat, lon))
                    .setTitle(name)
                    .setSnippet(catString)
                    .setIcon(icon));

            activePlaceMarkers.add(marker);
            placeMarkerDataMap.put(marker, doc);
        }
    }

    // ── AFISARE POP-UP CU IMAGINE DIN FIRESTORE + AUTO-CLOSE ÎN 2 SECUNDE ──
    private void showPinPopupForDoc(DocumentSnapshot doc, Marker marker) {
        if (cardPinPopup == null || mapLibreMap == null || marker == null) return;

        String name = doc.getString("name");
        String category = doc.getString("category");
        String imageUrl = doc.getString("imageUrl");

        if (txtPinName != null) txtPinName.setText(name);
        if (txtPinType != null) txtPinType.setText(category != null ? category.toUpperCase(Locale.ROOT) : "ATTRACTION");

        if (imgPinPhoto != null) {
            if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.rome)
                        .error(R.drawable.rome)
                        .into(imgPinPhoto);
            } else {
                imgPinPhoto.setImageResource(R.drawable.rome);
            }
        }

        // Calculăm poziția pe ecran a coordonatei marker-ului
        android.graphics.PointF screenPosition = mapLibreMap.getProjection().toScreenLocation(marker.getPosition());

        // Măsurăm pop-up-ul pentru a obține dimensiunile exacte
        cardPinPopup.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = cardPinPopup.getMeasuredWidth();
        int popupHeight = cardPinPopup.getMeasuredHeight();

        // Poziționăm cardul fix deasupra pinului
        cardPinPopup.setX(screenPosition.x - (popupWidth / 2f));
        cardPinPopup.setY(screenPosition.y - popupHeight - (30 * getResources().getDisplayMetrics().density)); // offset deasupra pinului

        cardPinPopup.setVisibility(View.VISIBLE);

        // Auto-close după 2 secunde
        cancelPinPopupAutoClose();
        pinPopupAutoCloseRunnable = this::hidePinPopup;
        pinPopupHandler.postDelayed(pinPopupAutoCloseRunnable, 2000L);
    }
    private void cancelPinPopupAutoClose() {
        if (pinPopupAutoCloseRunnable != null) {
            pinPopupHandler.removeCallbacks(pinPopupAutoCloseRunnable);
        }
    }

    private void hidePinPopup() {
        cancelPinPopupAutoClose();
        if (cardPinPopup != null) {
            cardPinPopup.setVisibility(View.GONE);
        }
    }

    // ── CONFIGURARE CHIPS-URI REGIUNI EUROPA (FĂRĂ TAB-UL "ALL DAYS") ──
    private void setupRegionChips() {
        TextView chipAll = findViewById(R.id.chipAllEurope);
        TextView chipWestern = findViewById(R.id.chipWesternEurope);
        TextView chipCentral = findViewById(R.id.chipCentralEurope);
        TextView chipSouthern = findViewById(R.id.chipSouthernEurope);
        TextView chipNorthern = findViewById(R.id.chipNorthernEurope);
        TextView chipEastern = findViewById(R.id.chipEasternEurope);

        regionChips.clear();
        regionChips.add(chipAll);
        regionChips.add(chipWestern);
        regionChips.add(chipCentral);
        regionChips.add(chipSouthern);
        regionChips.add(chipNorthern);
        regionChips.add(chipEastern);

        setOnRegionClickListener(chipAll, EUROPE_CENTER, EUROPE_ZOOM, "all");
        setOnRegionClickListener(chipWestern, new LatLng(48.8, 2.3), 3.8, "western");
        setOnRegionClickListener(chipCentral, new LatLng(51.1, 10.4), 3.8, "central");
        setOnRegionClickListener(chipSouthern, new LatLng(40.4, 15.0), 3.8, "southern");
        setOnRegionClickListener(chipNorthern, new LatLng(60.1, 18.6), 3.5, "northern");
        setOnRegionClickListener(chipEastern, new LatLng(45.9, 25.0), 3.8, "eastern");
    }
    private void filterRegionOnMapAndCards(String regionKey) {
        if (mapLibreMap == null) return;

        // Curățăm cardurile existente de jos și markerii de orașe de pe hartă
        layoutCardsContainer.removeAllViews();
        for (Marker m : activeCityMarkers) {
            mapLibreMap.removeMarker(m);
        }
        activeCityMarkers.clear();

        // Curățăm și pinii de atracții/restaurante vizibili
        clearPlaceMarkers();
        loadedPlaceDocs.clear();

        // Închidem pop-up-ul de detalii oraș dacă era deschis
        if (cardMapPopup != null) {
            cardMapPopup.setVisibility(View.GONE);
        }

        for (City city : allCitiesList) {
            if ("all".equalsIgnoreCase(regionKey)) {
                displayCityOnMapAndBottom(city);
                continue;
            }

            if (city.getCountryId() == null) continue;
            boolean matches = false;
            String countryLower = city.getCountryId().toLowerCase();

            switch (regionKey.toLowerCase()) {
                case "western":
                    matches = countryLower.contains("france") || countryLower.contains("belgium") || countryLower.contains("uk") || countryLower.contains("netherlands");
                    break;
                case "central":
                    matches = countryLower.contains("germany") || countryLower.contains("austria") || countryLower.contains("switzerland") || countryLower.contains("czech") || countryLower.contains("hungary") || countryLower.contains("poland");
                    break;
                case "southern":
                    matches = countryLower.contains("italy") || countryLower.contains("spain") || countryLower.contains("portugal") || countryLower.contains("greece");
                    break;
                case "eastern":
                    matches = countryLower.contains("romania") || countryLower.contains("bulgaria") || countryLower.contains("moldova") || countryLower.contains("ukraine");
                    break;
                case "northern":
                    matches = countryLower.contains("sweden") || countryLower.contains("norway") || countryLower.contains("finland") || countryLower.contains("denmark");
                    break;
            }

            if (matches) {
                displayCityOnMapAndBottom(city);
            }
        }
    }

    private void setOnRegionClickListener(TextView chip, LatLng center, double zoomLevel, String regionKey) {
        if (chip != null) {
            chip.setOnClickListener(v -> {
                // Ascunde cardul de detalii oraș și pop-up-ul de pin
                if (cardMapPopup != null) {
                    cardMapPopup.setVisibility(View.GONE);
                }
                hidePinPopup();

                for (TextView c : regionChips) {
                    c.setBackgroundResource(R.drawable.bg_chip);
                    c.setTextColor(Color.parseColor("#011C40"));
                }
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(Color.WHITE);

                if (mapLibreMap != null) {
                    mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoomLevel), 700);
                }

                filterRegionOnMapAndCards(regionKey);
            });
        }
    }
    private void showDetailsPopup(City city) {
        this.currentSelectedCity = city;
        if (cardMapPopup != null) {
            if (txtPopupCityName != null) txtPopupCityName.setText(city.getName());
            if (txtPopupLabel != null && city.getCountryId() != null) {
                txtPopupLabel.setText(city.getCountryId().toUpperCase());
            }

            if (txtPopupDescription != null) {
                String desc = city.getDescription();
                txtPopupDescription.setText((desc != null && !desc.trim().isEmpty()) ? desc : "Discover " + city.getName());
            }

            if (imgPopupCity != null) {
                String rawUrl = city.getImageUrl();
                if (rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
                    Glide.with(this).load(rawUrl).placeholder(R.drawable.rome).error(R.drawable.rome).into(imgPopupCity);
                } else {
                    imgPopupCity.setImageResource(R.drawable.rome);
                }
            }

            cardMapPopup.setVisibility(View.VISIBLE);
        }
    }

    // ── CHIPS DE FILTRE (MUSEUMS, RESTAURANTS, CAFES, ETC.) ──
    private void setupCategoryChipsListener() {
        activeFilters.add("museum");
        activeFilters.add("restaurant");
        activeFilters.add("historical");
        activeFilters.add("nature");
        activeFilters.add("monument");
        activeFilters.add("cafe");

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
                    // Re-randăm pinii aplicației în funcție de noile categorii bifate
                    renderFilteredPlaceMarkers();
                });
            }
        }
    }

    private void setupButtons() {
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> {
            if (mapLibreMap != null) mapLibreMap.animateCamera(CameraUpdateFactory.zoomIn());
        });
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> {
            if (mapLibreMap != null) mapLibreMap.animateCamera(CameraUpdateFactory.zoomOut());
        });

        findViewById(R.id.btnMyLocation).setOnClickListener(v -> {
            if (userLocationMarker != null && userLocationMarker.getPosition() != null && mapLibreMap != null) {
                hidePinPopup();
                if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocationMarker.getPosition(), MY_LOCATION_ZOOM), 800);
            } else {
                Toast.makeText(this, "Se preia semnalul GPS...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBottomNavigation() {
        LinearLayout btnDiscover = findViewById(R.id.btnDiscover);
        LinearLayout btnMap      = findViewById(R.id.btnMap);
        LinearLayout btnWishlist = findViewById(R.id.btnWishlist);
        LinearLayout btnAI       = findViewById(R.id.btnAI);

        int activeColor = ContextCompat.getColor(this, R.color.light_turq_1);
        int inactiveColor = Color.parseColor("#AAAAAA");

        if (btnMap != null) {
            ImageView imgMap = (ImageView) btnMap.getChildAt(0);
            TextView txtMap = (TextView) btnMap.getChildAt(1);

            if (imgMap != null) imgMap.setColorFilter(activeColor);
            if (txtMap != null) {
                txtMap.setTextColor(activeColor);
                txtMap.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }

        resetInactiveTabVisuals(btnDiscover, inactiveColor);
        resetInactiveTabVisuals(btnWishlist, inactiveColor);
        resetInactiveTabVisuals(btnAI, inactiveColor);

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
                Intent intent = new Intent(ExploreActivity.this, AIHubActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }

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

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }
    @Override protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPinPopupAutoClose();
        mapView.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initUserLocationMarker();
        }
    }
}