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
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings({"deprecation", "SpellCheckingInspection", "unused"})
@SuppressLint("SetTextI18n")
public class ExploreActivity extends AppCompatActivity implements SensorEventListener, LocationListener, OnMapReadyCallback {

    private static final LatLng EUROPE_CENTER = new LatLng(48.5, 14.0);
    private static final double EUROPE_ZOOM = 2.8;
    private static final double MY_LOCATION_ZOOM = 15.0;
    private static final double FOCUS_CITY_ZOOM = 12.5;

    private MapView mapView;
    private MapLibreMap mapLibreMap;

    // UI Header & Controale Traseu
    private View routeHeader;
    private TextView txtRouteCityName;
    private LinearLayout dayTabsContainer;

    private View scrollRegionChips;
    private View travelModeContainer;
    private TextView chipByCar, chipWalking;

    private View layoutSummaryContainer;
    private TextView txtNextDestination;
    private TextView txtTotalRouteSummary;

    private View panelNavigation;
    private TextView txtInstruction;
    private MaterialButton btnStartTrip;

    // Popups
    private View cardMapPopup;
    private TextView txtPopupCityName, txtPopupLabel, txtPopupDescription;
    private ImageView imgPopupCity;

    private View cardPinPopup;
    private ImageView imgPinPhoto;
    private TextView txtPinType, txtPinName;
    private MaterialButton btnPinAddDelete;

    private View scrollCardsHolder;
    private LinearLayout layoutCardsContainer;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<City> allCitiesList = new ArrayList<>();
    private final List<Marker> activeCityMarkers = new ArrayList<>();
    private final List<Marker> activePlaceMarkers = new ArrayList<>();
    private final Map<Marker, DocumentSnapshot> placeMarkerDataMap = new HashMap<>();

    private final Map<String, Integer> tripPlaceDaysMap = new HashMap<>();
    private final List<DocumentSnapshot> allLoadedCityPlaces = new ArrayList<>();
    private final Set<String> activeFilters = new HashSet<>();
    private final List<TextView> regionChips = new ArrayList<>();

    private City currentSelectedCity = null;
    private String currentCityId = null;

    // Stare Traseu
    private boolean isRouteModeActive = false;
    private boolean isNavigating = false;
    private String selectedTravelMode = "car";
    private int selectedDayNumber = 0;
    private final Set<String> activeRoutePlaceIds = new HashSet<>();
    private Polyline routePolyline;

    private Marker userLocationMarker;
    private Bitmap rawArrowBitmap = null;

    private Location lastKnownLocation = null;
    private Location lastRouteCalculatedLocation = null;

    private final Handler pinPopupHandler = new Handler(Looper.getMainLooper());
    private Runnable pinPopupAutoCloseRunnable;

    // Handler pentru prevenera pâlpâirii traseului la update-uri rapide de GPS (Debounce)
    private final Handler routeDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable routeDebounceRunnable;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);

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

        initViews();

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        setupRegionChips();
        setupButtons();
        setupBottomNavigation();
        setupCategoryChipsListener();

        mapView.getMapAsync(this);
    }

    private void initViews() {
        routeHeader = findViewById(R.id.routeHeader);
        txtRouteCityName = findViewById(R.id.txtRouteCityName);
        View btnCloseRoute = findViewById(R.id.btnCloseRoute);
        dayTabsContainer = findViewById(R.id.dayTabsContainer);

        scrollRegionChips = findViewById(R.id.scrollRegionChips);
        travelModeContainer = findViewById(R.id.travelModeContainer);
        chipByCar = findViewById(R.id.chipByCar);
        chipWalking = findViewById(R.id.chipWalking);

        layoutSummaryContainer = findViewById(R.id.layoutSummaryContainer);
        txtNextDestination = findViewById(R.id.txtNextDestination);
        txtTotalRouteSummary = findViewById(R.id.txtTotalRouteSummary);

        panelNavigation = findViewById(R.id.panelNavigation);
        txtInstruction = findViewById(R.id.txtInstruction);
        btnStartTrip = findViewById(R.id.btnStartTrip);

        cardMapPopup = findViewById(R.id.cardMapPopup);
        txtPopupCityName = findViewById(R.id.txtPopupCityName);
        txtPopupLabel = findViewById(R.id.txtPopupLabel);
        txtPopupDescription = findViewById(R.id.txtPopupDescription);
        imgPopupCity = findViewById(R.id.imgPopupCity);

        cardPinPopup = findViewById(R.id.cardPinPopup);
        imgPinPhoto = findViewById(R.id.imgPinPhoto);
        txtPinType = findViewById(R.id.txtPinType);
        txtPinName = findViewById(R.id.txtPinName);
        btnPinAddDelete = findViewById(R.id.btnPinAddDelete);

        scrollCardsHolder = findViewById(R.id.scrollCardsHolder);
        layoutCardsContainer = findViewById(R.id.layoutCardsContainer);

        if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);
        if (cardPinPopup != null) cardPinPopup.setVisibility(View.GONE);

        if (btnCloseRoute != null) btnCloseRoute.setOnClickListener(v -> exitRouteMode());
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;
        String freeStyleUrl = "https://tiles.openfreemap.org/styles/liberty";

        mapLibreMap.setStyle(new Style.Builder().fromUri(freeStyleUrl), style -> {
            mapLibreMap.setCameraPosition(new CameraPosition.Builder().target(EUROPE_CENTER).zoom(EUROPE_ZOOM).build());
            initUserLocationMarker();
            preloadAllDataFromFirestore();
            setupMapClickListener();
            handleIncomingIntent();
        });
    }

    private void setupMapClickListener() {
        if (mapLibreMap != null) {
            mapLibreMap.addOnMapClickListener(point -> {
                hidePinPopup();
                if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);
                return false;
            });

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
                        showPinPopupWithAddDelete(doc, m);
                    }
                    return true;
                }

                return false;
            });
        }
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("cityId")) {
            String cityId = intent.getStringExtra("cityId");
            String cityName = intent.getStringExtra("cityName");
            String tripId = intent.getStringExtra("tripId");
            this.currentCityId = cityId;
            enterRouteMode(cityId, cityName != null ? cityName : "Your Trip", tripId);
        }
    }

    private void enterRouteMode(String cityId, String cityName, String tripId) {
        isRouteModeActive = true;
        this.currentCityId = cityId;
        if (routeHeader != null) routeHeader.setVisibility(View.VISIBLE);
        if (travelModeContainer != null) travelModeContainer.setVisibility(View.VISIBLE);
        if (scrollRegionChips != null) scrollRegionChips.setVisibility(View.GONE);
        if (scrollCardsHolder != null) scrollCardsHolder.setVisibility(View.GONE);
        if (txtRouteCityName != null) txtRouteCityName.setText(cityName);

        setupTravelModeChips();
        loadTripPlacesFromFirestore(cityId, tripId);
    }

    private void exitRouteMode() {
        // Oprim complet modul traseu
        isRouteModeActive = false;
        isNavigating = false;

        // Oprim orice recalculare de rută rămasă în așteptare
        if (routeDebounceRunnable != null) {
            routeDebounceHandler.removeCallbacks(routeDebounceRunnable);
            routeDebounceRunnable = null;
        }

        // Ascundem elementele UI ale traseului
        if (routeHeader != null) routeHeader.setVisibility(View.GONE);
        if (travelModeContainer != null) travelModeContainer.setVisibility(View.GONE);
        if (layoutSummaryContainer != null) layoutSummaryContainer.setVisibility(View.GONE);
        if (panelNavigation != null) panelNavigation.setVisibility(View.GONE);

        // Reafișăm UI-ul principal
        if (scrollRegionChips != null) scrollRegionChips.setVisibility(View.VISIBLE);
        if (scrollCardsHolder != null) scrollCardsHolder.setVisibility(View.VISIBLE);

        // Resetăm butonul START/STOP
        if (btnStartTrip != null) {
            btnStartTrip.setText("START");
            btnStartTrip.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.blue)
            );
        }

        // Ștergem traseul de pe hartă
        clearRoutePolyline();

        // Ștergem toate datele rutei
        activeRoutePlaceIds.clear();
        tripPlaceDaysMap.clear();
        allLoadedCityPlaces.clear();

        // Resetăm locația calculată pentru rută
        lastRouteCalculatedLocation = null;
        if (layoutSummaryContainer != null) {
            layoutSummaryContainer.setVisibility(View.GONE);
        }
        // Ștergem definitiv textele din sumar
        if (txtNextDestination != null) {
            txtNextDestination.setText("");
            txtNextDestination.setVisibility(View.GONE);
        }

        if (txtTotalRouteSummary != null) {
            txtTotalRouteSummary.setText("");
            txtTotalRouteSummary.setVisibility(View.GONE);
        }

        if (txtInstruction != null) {
            txtInstruction.setText("");
            txtInstruction.setVisibility(View.GONE);
        }

        // Resetăm orașul curent
        currentCityId = null;

        // Curățăm pinii locurilor și reafișăm toate orașele
        clearPlaceMarkers();
        displayAllLoadedCities();

        // Resetăm chip-ul „All Europe”
        TextView chipAll = findViewById(R.id.chipAllEurope);
        if (chipAll != null) {
            for (TextView c : regionChips) {
                c.setBackgroundResource(R.drawable.bg_chip);
                c.setTextColor(Color.parseColor("#011C40"));
            }

            chipAll.setBackgroundResource(R.drawable.bg_chip_selected);
            chipAll.setTextColor(Color.WHITE);
        }
    }

    private void setupTravelModeChips() {
        if (chipByCar == null || chipWalking == null) return;

        chipByCar.setOnClickListener(v -> {
            selectedTravelMode = "car";
            chipByCar.setBackgroundResource(R.drawable.bg_chip_selected);
            chipByCar.setTextColor(Color.WHITE);
            chipWalking.setBackgroundResource(R.drawable.bg_chip);
            chipWalking.setTextColor(Color.parseColor("#011C40"));
            recalculateAndDrawRoute();
        });

        chipWalking.setOnClickListener(v -> {
            selectedTravelMode = "foot";
            chipWalking.setBackgroundResource(R.drawable.bg_chip_selected);
            chipWalking.setTextColor(Color.WHITE);
            chipByCar.setBackgroundResource(R.drawable.bg_chip);
            chipByCar.setTextColor(Color.parseColor("#011C40"));
            recalculateAndDrawRoute();
        });
    }

    private void loadTripPlacesFromFirestore(String cityId, String tripId) {
        allLoadedCityPlaces.clear();
        tripPlaceDaysMap.clear();
        activeRoutePlaceIds.clear();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (tripId != null && user != null) {
            db.collection("users").document(user.getUid())
                    .collection("savedTrips").document(tripId)
                    .get()
                    .addOnSuccessListener(tripDoc -> {
                        if (tripDoc.exists() && tripDoc.get("itinerary") != null) {
                            List<Map<String, Object>> itineraryList = (List<Map<String, Object>>) tripDoc.get("itinerary");
                            loadCityPlacesWithTripMap(cityId, itineraryList);
                        } else {
                            loadCityPlacesWithTripMap(cityId, null);
                        }
                    })
                    .addOnFailureListener(e -> loadCityPlacesWithTripMap(cityId, null));
        } else {
            loadCityPlacesWithTripMap(cityId, null);
        }
    }

    private void loadCityPlacesWithTripMap(String cityId, @Nullable List<Map<String, Object>> itineraryList) {
        db.collectionGroup("attractions").get().addOnCompleteListener(task1 -> {
            if (task1.isSuccessful() && task1.getResult() != null) {
                for (DocumentSnapshot doc : task1.getResult().getDocuments()) {
                    String docCityId = doc.getString("cityId");
                    if (docCityId != null && docCityId.equalsIgnoreCase(cityId)) {
                        allLoadedCityPlaces.add(doc);
                    }
                }
            }
            db.collectionGroup("restaurants").get().addOnCompleteListener(task2 -> {
                if (task2.isSuccessful() && task2.getResult() != null) {
                    for (DocumentSnapshot doc : task2.getResult().getDocuments()) {
                        String docCityId = doc.getString("cityId");
                        if (docCityId != null && docCityId.equalsIgnoreCase(cityId)) {
                            allLoadedCityPlaces.add(doc);
                        }
                    }
                }

                if (itineraryList != null) {
                    for (Map<String, Object> item : itineraryList) {
                        String poiId = (String) item.get("poiId");
                        Long dayLong = (Long) item.get("day");
                        int dayNumber = dayLong != null ? dayLong.intValue() : 1;

                        for (DocumentSnapshot doc : allLoadedCityPlaces) {
                            String docName = doc.getString("name");
                            if ((poiId != null && poiId.equalsIgnoreCase(doc.getId())) ||
                                    (docName != null && poiId != null && docName.toLowerCase().contains(poiId.toLowerCase()))) {
                                tripPlaceDaysMap.put(doc.getId(), dayNumber);
                                activeRoutePlaceIds.add(doc.getId());
                            }
                        }
                    }
                }

                runOnUiThread(() -> {
                    buildDayTabs();
                    renderPlaceMarkers();
                    recalculateAndDrawRoute();
                });
            });
        });
    }

    private void buildDayTabs() {
        if (dayTabsContainer == null) return;
        dayTabsContainer.removeAllViews();

        int maxDays = 1;
        for (Integer d : tripPlaceDaysMap.values()) {
            if (d != null && d > maxDays) {
                maxDays = d;
            }
        }

        addDayTab("All Days", 0);
        for (int i = 1; i <= maxDays; i++) {
            addDayTab("Day " + i, i);
        }
    }

    private void addDayTab(String label, int dayNum) {
        TextView tab = new TextView(this);
        tab.setText(label);
        tab.setTextSize(11f);
        tab.setPadding(24, 10, 24, 10);
        tab.setTextColor(Color.WHITE);
        tab.setTypeface(null, android.graphics.Typeface.BOLD);
        tab.setBackgroundResource(dayNum == selectedDayNumber ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        if (dayNum != selectedDayNumber) tab.setAlpha(0.6f);

        tab.setOnClickListener(v -> {
            selectedDayNumber = dayNum;
            buildDayTabs();
            renderPlaceMarkers();
            recalculateAndDrawRoute();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 12, 0);
        dayTabsContainer.addView(tab, params);
    }

    private void renderPlaceMarkers() {
        clearPlaceMarkers();

        for (DocumentSnapshot doc : allLoadedCityPlaces) {
            String catString = doc.getString("category");
            String normCategory = (catString != null) ? catString.toLowerCase() : "historical";
            if (normCategory.equals("brunch")) normCategory = "restaurant";

            if (activeFilters.isEmpty() || activeFilters.contains(normCategory)) {
                int dayNumber = tripPlaceDaysMap.containsKey(doc.getId()) ? tripPlaceDaysMap.get(doc.getId()) : 0;
                boolean isPartOfCurrentRoute = activeRoutePlaceIds.contains(doc.getId()) &&
                        (selectedDayNumber == 0 || dayNumber == selectedDayNumber);

                addPlaceMarkerFromDoc(doc, isPartOfCurrentRoute);
            }
        }
    }

    private void addPlaceMarkerFromDoc(DocumentSnapshot doc, boolean isPartOfRoute) {
        Double lat = doc.getDouble("lat");
        Double lon = doc.getDouble("lon");
        String name = doc.getString("name");
        String catString = doc.getString("category");

        if (lat == null || lon == null || name == null) return;

        int glyphResId;
        if (catString != null) {
            switch (catString.toLowerCase()) {
                case "museum": glyphResId = R.drawable.ic_museum; break;
                case "restaurant": case "brunch": glyphResId = R.drawable.ic_restaurant; break;
                case "cafe": glyphResId = R.drawable.ic_cafe; break;
                case "monument": glyphResId = R.drawable.ic_monument; break;
                case "nature": glyphResId = R.drawable.ic_nature; break;
                default: glyphResId = R.drawable.ic_historical; break;
            }
        } else {
            glyphResId = R.drawable.ic_historical;
        }

        Drawable iconDrawable = ContextCompat.getDrawable(this, glyphResId);
        if (iconDrawable != null) {
            int sizePx = (int) ((isPartOfRoute ? 32 : 26) * getResources().getDisplayMetrics().density);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            if (isPartOfRoute) {
                Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setColor(Color.parseColor("#023859"));
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(6f);
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.3f, strokePaint);
            }

            iconDrawable.setBounds(2, 2, sizePx - 2, sizePx - 2);
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

    // ── CALCULEAZĂ TRASEUL DIN LOCAȚIA MOCK / GPS FĂRĂ PÂLPÂIRE ──
    private void recalculateAndDrawRoute() {
        if (!isRouteModeActive) return;

        List<LatLng> waypoints = new ArrayList<>();
        List<String> destinationNames = new ArrayList<>();

        // 1. Întotdeauna primul punct este locația GPS curentă
        if (lastKnownLocation != null) {
            waypoints.add(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));
        }

        // 2. Colectăm toate locațiile active din activeRoutePlaceIds (inclusiv cele adăugate dinamic prin ADD)
        for (DocumentSnapshot doc : allLoadedCityPlaces) {
            String docId = doc.getId();

            if (activeRoutePlaceIds.contains(docId)) {
                int dayNumber = tripPlaceDaysMap.containsKey(docId) ? tripPlaceDaysMap.get(docId) : 0;

                // Dacă suntem pe "All Days" (0) SAU dacă punctul aparține zilei curente SAU a fost adăugat manual (dayNumber == 0)
                if (selectedDayNumber == 0 || dayNumber == selectedDayNumber || dayNumber == 0) {
                    Double lat = doc.getDouble("lat");
                    Double lon = doc.getDouble("lon");
                    String name = doc.getString("name");

                    if (lat != null && lon != null) {
                        waypoints.add(new LatLng(lat, lon));
                        destinationNames.add(name != null ? name : "Oprire");
                    }
                }
            }
        }

        if (waypoints.size() < 2) {
            clearRoutePolyline();
            if (layoutSummaryContainer != null) layoutSummaryContainer.setVisibility(View.GONE);
            return;
        }

        executor.execute(() -> {
            StringBuilder urlBuilder = new StringBuilder("https://router.project-osrm.org/route/v1/");
            urlBuilder.append(selectedTravelMode.equals("car") ? "driving/" : "foot/");

            for (int i = 0; i < waypoints.size(); i++) {
                urlBuilder.append(waypoints.get(i).getLongitude()).append(",").append(waypoints.get(i).getLatitude());
                if (i < waypoints.size() - 1) urlBuilder.append(";");
            }
            urlBuilder.append("?overview=full&geometries=geojson&steps=true");

            Request request = new Request.Builder().url(urlBuilder.toString()).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonStr = response.body().string();
                    JSONObject json = new JSONObject(jsonStr);
                    JSONArray routes = json.getJSONArray("routes");

                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        double distanceMeters = route.getDouble("distance");
                        double rawDurationSec = route.getDouble("duration");

                        double durationSeconds = "foot".equals(selectedTravelMode) ? (distanceMeters / 1.25) : rawDurationSec;

                        double firstLegDist = 0;
                        double firstLegDuration = 0;
                        String turnInstruction = "";

                        JSONArray legs = route.optJSONArray("legs");
                        if (legs != null && legs.length() > 0) {
                            JSONObject firstLeg = legs.getJSONObject(0);
                            firstLegDist = firstLeg.optDouble("distance", 0);
                            firstLegDuration = firstLeg.optDouble("duration", 0);
                            if ("foot".equals(selectedTravelMode)) {
                                firstLegDuration = (firstLegDist / 1.25);
                            }

                            JSONArray steps = firstLeg.optJSONArray("steps");
                            if (steps != null && steps.length() > 0) {
                                JSONObject step = steps.getJSONObject(0);
                                double stepDist = step.optDouble("distance", 0);
                                JSONObject maneuver = step.optJSONObject("maneuver");
                                String modifier = maneuver != null ? maneuver.optString("modifier", "") : "";

                                String dir = "înainte";
                                if (modifier.contains("left")) dir = "la stânga";
                                else if (modifier.contains("right")) dir = "la dreapta";

                                int distM = (int) Math.round(stepDist);
                                turnInstruction = "În " + (distM > 0 ? distM + " m" : "100 m") + " mergeți " + dir;
                            }
                        }

                        JSONArray coordinates = route.getJSONObject("geometry").getJSONArray("coordinates");
                        List<LatLng> polylinePoints = new ArrayList<>();

                        for (int i = 0; i < coordinates.length(); i++) {
                            JSONArray point = coordinates.getJSONArray(i);
                            polylinePoints.add(new LatLng(point.getDouble(1), point.getDouble(0)));
                        }

                        final String firstName = !destinationNames.isEmpty() ? destinationNames.get(0) : "Prima oprire";
                        final double fDist = firstLegDist;
                        final double fDur = firstLegDuration;
                        final double totalDur = durationSeconds;
                        final String dynInstruction = turnInstruction.isEmpty() ? "Urmați traseul spre " + firstName : turnInstruction + " spre " + firstName;

                        runOnUiThread(() -> {
                            drawRoutePolyline(polylinePoints);
                            // Aici se face actualizarea automată
                            updateNavigationUI(firstName, fDist, fDur, dynInstruction, distanceMeters, totalDur);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("OSRM_ROUTE", "Eroare la calcularea rutei", e);
            }
        });
    }

    private void drawRoutePolyline(List<LatLng> points) {
        clearRoutePolyline();
        if (mapLibreMap != null) {
            routePolyline = mapLibreMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .color(Color.parseColor("#26658C"))
                    .width(6f));
        }
    }

    private void clearRoutePolyline() {
        if (routePolyline != null && mapLibreMap != null) {
            mapLibreMap.removePolyline(routePolyline);
            routePolyline = null;
        }
    }

    private void updateRouteSummary(double totalDistMeters, double totalDurationSec, String nextName, double nextDistMeters, double nextDurSec, String instructionText) {
        if (layoutSummaryContainer != null) {
            layoutSummaryContainer.setVisibility(View.VISIBLE);
        }

        double totalKm = totalDistMeters / 1000.0;
        int totalMins = (int) Math.round(totalDurationSec / 60.0);
        int totalHours = totalMins / 60;
        int totalRemMins = totalMins % 60;
        String totalTimeStr = totalHours > 0 ? totalHours + "h " + totalRemMins + "m" : totalMins + " min";

        if (txtTotalRouteSummary != null) {
            txtTotalRouteSummary.setText("⏱ Total: " + totalTimeStr + " · " + String.format(Locale.US, "%.1f km", totalKm));
        }

        double nextKm = nextDistMeters / 1000.0;
        int nextMins = (int) Math.round(nextDurSec / 60.0);
        int nextHours = nextMins / 60;
        int nextRemMins = nextMins % 60;
        String nextTimeStr = nextHours > 0 ? nextHours + "h " + nextRemMins + "m" : nextMins + " min";

        if (txtNextDestination != null) {
            txtNextDestination.setText("📍 Next: " + nextName + " (" + String.format(Locale.US, "%.1f km", nextKm) + " · " + nextTimeStr + ")");
        }

        if (txtInstruction != null) {
            txtInstruction.setText(instructionText);
        }
    }

    // ── POPUP PIN CU ADD/DELETE RE-CONFIGURAT PENTRU RE-CALCULARE INSTANTĂ ──
    private void showPinPopupWithAddDelete(DocumentSnapshot doc, Marker marker) {
        if (cardPinPopup == null || mapLibreMap == null || marker == null) return;

        String name = doc.getString("name");
        String category = doc.getString("category");
        String imageUrl = doc.getString("imageUrl");
        String docId = doc.getId();

        if (txtPinName != null) txtPinName.setText(name);
        if (txtPinType != null) txtPinType.setText(category != null ? category.toUpperCase(Locale.ROOT) : "ATTRACTION");

        if (imgPinPhoto != null) {
            if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                Glide.with(this).load(imageUrl).placeholder(R.drawable.rome).error(R.drawable.rome).into(imgPinPhoto);
            } else {
                imgPinPhoto.setImageResource(R.drawable.rome);
            }
        }

        if (isRouteModeActive && btnPinAddDelete != null) {
            btnPinAddDelete.setVisibility(View.VISIBLE);
            boolean isAlreadyAdded = activeRoutePlaceIds.contains(docId);
            btnPinAddDelete.setText(isAlreadyAdded ? "DELETE" : "ADD");
            btnPinAddDelete.setBackgroundTintList(ContextCompat.getColorStateList(this, isAlreadyAdded ? android.R.color.holo_red_dark : R.color.blue));

            btnPinAddDelete.setOnClickListener(v -> {
                if (isAlreadyAdded) {
                    activeRoutePlaceIds.remove(docId);
                } else {
                    activeRoutePlaceIds.add(docId);
                    // Dacă adăugăm o locație nouă, o asociem automat zilei curente selectate
                    if (selectedDayNumber > 0) {
                        tripPlaceDaysMap.put(docId, selectedDayNumber);
                    }
                }
                cardPinPopup.setVisibility(View.GONE);

                // Resetăm ultima locație calculată pentru a forța re-desenarea imediată a liniei fără delay
                lastRouteCalculatedLocation = null;

                renderPlaceMarkers();
                recalculateAndDrawRoute();
            });
        } else if (btnPinAddDelete != null) {
            btnPinAddDelete.setVisibility(View.GONE);
        }

        android.graphics.PointF screenPosition = mapLibreMap.getProjection().toScreenLocation(marker.getPosition());
        cardPinPopup.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        cardPinPopup.setX(screenPosition.x - (cardPinPopup.getMeasuredWidth() / 2f));
        cardPinPopup.setY(screenPosition.y - cardPinPopup.getMeasuredHeight() - (25 * getResources().getDisplayMetrics().density));

        cardPinPopup.setVisibility(View.VISIBLE);

        cancelPinPopupAutoClose();
        pinPopupAutoCloseRunnable = this::hidePinPopup;
        pinPopupHandler.postDelayed(pinPopupAutoCloseRunnable, 2500L);
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

        if (layoutCardsContainer != null) layoutCardsContainer.removeAllViews();
        for (Marker m : activeCityMarkers) mapLibreMap.removeMarker(m);
        activeCityMarkers.clear();

        clearPlaceMarkers();
        allLoadedCityPlaces.clear();

        if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);

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
                if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);
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
                    renderPlaceMarkers();
                });
            }
        }
    }

    private void setupButtons() {
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> { if (mapLibreMap != null) mapLibreMap.animateCamera(CameraUpdateFactory.zoomIn()); });
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> { if (mapLibreMap != null) mapLibreMap.animateCamera(CameraUpdateFactory.zoomOut()); });

        if (btnStartTrip != null) {
            btnStartTrip.setOnClickListener(v -> {
                if (!isNavigating) {
                    isNavigating = true;
                    btnStartTrip.setText("STOP");
                    btnStartTrip.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_red_dark));
                    if (panelNavigation != null) panelNavigation.setVisibility(View.VISIBLE);
                } else {
                    stopNavigationMode();
                }
            });
        }

        // ── BUTON "ME": FACEM ZOOM PE MOCK/GPS CURENT ȘI ÎNCĂRCĂM PINII ORAȘULUI APROPIAT ──
        findViewById(R.id.btnMyLocation).setOnClickListener(v -> {
            if (lastKnownLocation != null && mapLibreMap != null) {
                hidePinPopup();
                if (cardMapPopup != null) cardMapPopup.setVisibility(View.GONE);

                LatLng myPos = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, MY_LOCATION_ZOOM), 800);

                // Găsim orașul cel mai apropiat și îi încărcăm pinii
                City closestCity = findClosestCity(lastKnownLocation);
                if (closestCity != null && (currentCityId == null || !currentCityId.equalsIgnoreCase(closestCity.getId()))) {
                    this.currentCityId = closestCity.getId();
                    loadCityPlacesWithTripMap(closestCity.getId(), null);
                } else if (allLoadedCityPlaces.isEmpty() && currentCityId != null) {
                    loadCityPlacesWithTripMap(currentCityId, null);
                } else {
                    renderPlaceMarkers();
                }
            } else {
                Toast.makeText(this, "Se preia semnalul GPS...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private City findClosestCity(Location location) {
        City closest = null;
        float minDistance = Float.MAX_VALUE;

        for (City city : allCitiesList) {
            if (city.getLat() != null && city.getLon() != null) {
                float[] results = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(), city.getLat(), city.getLon(), results);
                if (results[0] < minDistance) {
                    minDistance = results[0];
                    closest = city;
                }
            }
        }
        return closest;
    }

    private void stopNavigationMode() {
        isNavigating = false;
        if (panelNavigation != null) panelNavigation.setVisibility(View.GONE);
        if (btnStartTrip != null) {
            btnStartTrip.setText("START");
            btnStartTrip.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.blue));
        }
    }

    private void clearPlaceMarkers() {
        for (Marker m : activePlaceMarkers) mapLibreMap.removeMarker(m);
        activePlaceMarkers.clear();
        placeMarkerDataMap.clear();
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
        if (mapLibreMap == null || isRouteModeActive) return;

        for (Marker m : activeCityMarkers) mapLibreMap.removeMarker(m);
        activeCityMarkers.clear();
        if (layoutCardsContainer != null) layoutCardsContainer.removeAllViews();

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

        if (layoutCardsContainer != null) {
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
        }
    }

    // Adaugă asta în clasa ExploreActivity
    private void updateNavigationUI(String nextName, double nextDistMeters, double nextDurSec, String instructionText, double totalDistMeters, double totalDurationSec) {
        if (layoutSummaryContainer == null || txtNextDestination == null || txtTotalRouteSummary == null || txtInstruction == null) return;

        layoutSummaryContainer.setVisibility(View.VISIBLE);

        // Sumar Total
        double totalKm = totalDistMeters / 1000.0;
        int totalMins = (int) Math.round(totalDurationSec / 60.0);
        int totalHours = totalMins / 60;
        int totalRemMins = totalMins % 60;
        String totalTimeStr = totalHours > 0 ? totalHours + "h " + totalRemMins + "m" : totalMins + " min";
        txtTotalRouteSummary.setText("⏱ Total: " + totalTimeStr + " · " + String.format(Locale.US, "%.1f km", totalKm));

        // Next Destination
        double nextKm = nextDistMeters / 1000.0;
        int nextMins = (int) Math.round(nextDurSec / 60.0);
        String nextTimeStr = nextMins + " min";
        txtNextDestination.setText("📍 Next: " + nextName + " (" + String.format(Locale.US, "%.1f km", nextKm) + " · " + nextTimeStr + ")");

        // Instrucțiune
        txtInstruction.setText(instructionText);
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

    private void focusOnCity(City city) {
        if (mapLibreMap == null) return;
        this.currentCityId = city.getId();
        LatLng targetCenter = new LatLng(city.getLat() - 0.003, city.getLon());
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetCenter, FOCUS_CITY_ZOOM), 750);
        loadCityPlacesWithTripMap(city.getId(), null);
    }

    private void initUserLocationMarker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this);
                    locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0f, this);

                    Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    Location lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    Location lastPass = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

                    Location best = lastGps;
                    if (best == null || (lastNet != null && lastNet.getTime() > best.getTime())) best = lastNet;
                    if (best == null || (lastPass != null && lastPass.getTime() > best.getTime())) best = lastPass;

                    if (best != null) {
                        onLocationChanged(best);
                    }
                } catch (Exception e) {
                    Log.e("GPS_INIT", "Eroare solicitare GPS", e);
                }
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

    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (lastKnownLocation != null && lastKnownLocation.isFromMockProvider() && !location.isFromMockProvider()) {
            return;
        }

        this.lastKnownLocation = location;

        if (mapLibreMap == null) return;
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());

        if (userLocationMarker == null && rawArrowBitmap != null) {
            Icon icon = IconFactory.getInstance(this).fromBitmap(rawArrowBitmap);
            userLocationMarker = mapLibreMap.addMarker(new MarkerOptions().position(newPoint).setIcon(icon));
        } else if (userLocationMarker != null) {
            userLocationMarker.setPosition(newPoint);
        }

        // Prevenim pâlpâirea traseului la fiecare micro-mișcare de GPS
        if (isRouteModeActive) {
            if (lastRouteCalculatedLocation == null || lastRouteCalculatedLocation.distanceTo(location) > 15) {
                lastRouteCalculatedLocation = location;

                if (routeDebounceRunnable != null) {
                    routeDebounceHandler.removeCallbacks(routeDebounceRunnable);
                }
                routeDebounceRunnable = this::recalculateAndDrawRoute;
                routeDebounceHandler.postDelayed(routeDebounceRunnable, 300);
            }
        }
    }

    private void setupBottomNavigation() {
        LinearLayout btnDiscover = findViewById(R.id.btnDiscover);
        LinearLayout btnWishlist = findViewById(R.id.btnWishlist);
        LinearLayout btnAI       = findViewById(R.id.btnAI);

        if (btnDiscover != null) {
            btnDiscover.setOnClickListener(v -> {
                Intent intent = new Intent(ExploreActivity.this, DiscoverActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }

        if (btnWishlist != null) {
            btnWishlist.setOnClickListener(v -> {
                Intent intent = new Intent(ExploreActivity.this, JourneyActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }

        if (btnAI != null) {
            btnAI.setOnClickListener(v -> {
                Intent intent = new Intent(ExploreActivity.this, AIHubActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPinPopupAutoClose();
        if (routeDebounceRunnable != null) {
            routeDebounceHandler.removeCallbacks(routeDebounceRunnable);
        }
        executor.shutdown();
        mapView.onDestroy();
    }
}