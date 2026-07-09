package com.example.mobile_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PlanTripActivity extends AppCompatActivity {

    private int currentDays = 4;
    private boolean isFlexibleMode = true;
    private long departureDate = -1;
    private long returnDate = -1;
    private boolean selectingDeparture = true;

    private TextView txtDays, txtNights, txtDateSelection;
    private TextView txtSelectedDates, txtResultDays, txtResultNights;
    private LinearLayout layoutFlexible, layoutExactDates;
    private CardView cardDateResult;
    private TextView btnFlexible, btnExactDates;
    private TextView chipWeekend, chip1Week, chip2Weeks;

    // Destinatia - primita din CityDetailActivity (cu fallback pe Rome, ca in layout)
    private String cityId, cityName, countryId;

    private TripRepository tripRepository;
    private String currentUid;

    // Selectiile de atractii/restaurante pentru trip-ul curent (ID-uri)
    private final Set<String> selectedAttractionIds = new HashSet<>();
    private final Set<String> selectedRestaurantIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plan_trip);

        android.util.Log.d("PLAN_TRIP", "Activity started successfully");

        tripRepository = new TripRepository();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = firebaseUser != null ? firebaseUser.getUid() : null;

        // Destinatia vine din CityDetailActivity; daca lipseste (ex: test direct), ramane Rome/Italy ca in XML
        cityId = getIntent().getStringExtra("cityId");
        cityName = getIntent().getStringExtra("cityName");
        countryId = getIntent().getStringExtra("countryId");
        if (cityId == null) cityId = "rome";
        if (cityName == null) cityName = "Rome";
        if (countryId == null) countryId = "italy";

        TextView txtDestName = findViewById(R.id.txtDestName);
        TextView txtDestCountry = findViewById(R.id.txtDestCountry);
        ImageView imgDestination = findViewById(R.id.imgDestination);
        if (txtDestName != null) txtDestName.setText(cityName);
        if (txtDestCountry != null) txtDestCountry.setText(capitalize(countryId));
        updateDestinationImage(imgDestination, cityId);

        TextView btnChange = findViewById(R.id.btnChange);
        if (btnChange != null) {
            btnChange.setOnClickListener(v -> showCityPicker(txtDestName, txtDestCountry, imgDestination));
        }

        // WindowInsets - verifica ca planTripRoot exista in XML
        View root = findViewById(R.id.planTripRoot);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return insets;
            });
        }

        // Buton inchidere - verifica ca exista
        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        // Toggle
        btnFlexible = findViewById(R.id.btnFlexible);
        btnExactDates = findViewById(R.id.btnExactDates);
        layoutFlexible = findViewById(R.id.layoutFlexible);
        layoutExactDates = findViewById(R.id.layoutExactDates);
        cardDateResult = findViewById(R.id.cardDateResult);
        txtDays = findViewById(R.id.txtDays);
        txtNights = findViewById(R.id.txtNights);
        txtDateSelection = findViewById(R.id.txtDateSelection);
        txtSelectedDates = findViewById(R.id.txtSelectedDates);
        txtResultDays = findViewById(R.id.txtResultDays);
        txtResultNights = findViewById(R.id.txtResultNights);
        chipWeekend = findViewById(R.id.chipWeekend);
        chip1Week = findViewById(R.id.chip1Week);
        chip2Weeks = findViewById(R.id.chip2Weeks);

        if (btnFlexible != null)
            btnFlexible.setOnClickListener(v -> switchToFlexible());
        if (btnExactDates != null)
            btnExactDates.setOnClickListener(v -> switchToExactDates());

        View btnMinus = findViewById(R.id.btnMinus);
        View btnPlus = findViewById(R.id.btnPlus);

        if (btnMinus != null) {
            btnMinus.setOnClickListener(v -> {
                if (currentDays > 1) {
                    currentDays--;
                    updateDaysDisplay();
                    resetChips();
                }
            });
        }

        if (btnPlus != null) {
            btnPlus.setOnClickListener(v -> {
                currentDays++;
                updateDaysDisplay();
                resetChips();
            });
        }

        if (chipWeekend != null)
            chipWeekend.setOnClickListener(v -> setDays(2, chipWeekend));
        if (chip1Week != null)
            chip1Week.setOnClickListener(v -> setDays(7, chip1Week));
        if (chip2Weeks != null)
            chip2Weeks.setOnClickListener(v -> setDays(14, chip2Weeks));

        // Calendar
        CalendarView calendarView = findViewById(R.id.calendarView);
        if (calendarView != null) {
            calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                long selectedTime = selected.getTimeInMillis();

                if (selectingDeparture) {
                    departureDate = selectedTime;
                    selectingDeparture = false;
                    if (txtDateSelection != null)
                        txtDateSelection.setText("Now select your return date");
                    calendarView.setMinDate(selectedTime + 86400000);
                } else {
                    returnDate = selectedTime;
                    selectingDeparture = true;
                    calendarView.setMinDate(0);
                    showDateResult();
                }
            });
        }

        // Reset
        View btnReset = findViewById(R.id.btnResetDates);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                departureDate = -1;
                returnDate = -1;
                selectingDeparture = true;
                if (txtDateSelection != null)
                    txtDateSelection.setText("Select your departure date");
                if (cardDateResult != null)
                    cardDateResult.setVisibility(View.GONE);
                btnReset.setVisibility(View.GONE);
                if (calendarView != null)
                    calendarView.setMinDate(0);
            });
        }

        updateDaysDisplay();

        MaterialButton btnPlanWithAI = findViewById(R.id.btnPlanWithAI);
        TextView txtSaveAsDraft = findViewById(R.id.txtSaveAsDraft);

        if (btnPlanWithAI != null) {
            btnPlanWithAI.setOnClickListener(v -> saveTrip("planned"));
        }
        if (txtSaveAsDraft != null) {
            txtSaveAsDraft.setOnClickListener(v -> saveTrip("draft"));
        }

        View btnSelectAttractions = findViewById(R.id.btnSelectAttractions);
        View btnSelectRestaurants = findViewById(R.id.btnSelectRestaurants);
        if (btnSelectAttractions != null) {
            btnSelectAttractions.setOnClickListener(v -> openAttractionsPicker());
        }
        if (btnSelectRestaurants != null) {
            btnSelectRestaurants.setOnClickListener(v -> openRestaurantsPicker());
        }
    }

    /**
     * Salveaza trip-ul curent in savedTrips, cu status "planned" (buton Plan with AI)
     * sau "draft" (Save as draft). Generarea efectiva a itinerariului cu AI e o
     * feature separata, nu implementata aici - deocamdata doar salvam intentia de calatorie.
     */
    private void saveTrip(String status) {
        if (currentUid == null) {
            Toast.makeText(this, "Trebuie sa fii autentificat pentru a salva un trip.", Toast.LENGTH_LONG).show();
            return;
        }

        String startDate = null;
        String endDate = null;
        int daysCount = currentDays;

        if (!isFlexibleMode && departureDate != -1 && returnDate != -1) {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            startDate = isoFormat.format(new Date(departureDate));
            endDate = isoFormat.format(new Date(returnDate));
            daysCount = (int) ((returnDate - departureDate) / (1000 * 60 * 60 * 24));
        } else if (!isFlexibleMode) {
            // Exact dates e activ dar nu s-au ales inca ambele date
            Toast.makeText(this, "Selecteaza data de plecare si de intoarcere.", Toast.LENGTH_SHORT).show();
            return;
        }
        // In modul Flexible, startDate/endDate raman null (ca la "Lisbon 5-day idea" din Wishlist),
        // se stie doar durata (daysCount).

        Trip trip = Trip.createSaved(cityId, cityName, countryId, startDate, endDate, daysCount, status);

        // Temporar: toate selectiile merg pe ziua 1, pana adaugam interfata de alegere a zilei
        List<ItineraryItem> itinerary = new ArrayList<>();
        for (String attractionId : selectedAttractionIds) {
            itinerary.add(new ItineraryItem(attractionId, "attraction", 1));
        }
        for (String restaurantId : selectedRestaurantIds) {
            itinerary.add(new ItineraryItem(restaurantId, "restaurant", 1));
        }
        trip.setItinerary(itinerary);

        tripRepository.addSavedTrip(currentUid, trip, new TripRepository.TripCallback() {
            @Override
            public void onSuccess(Trip savedTrip) {
                String message = "planned".equals(status) ? "Calatorie planificata!" : "Salvat ca draft";
                Toast.makeText(PlanTripActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PlanTripActivity.this,
                        "Nu am putut salva: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Deschide bottom sheet-ul cu atractiile orasului curent selectat. */
    private void openAttractionsPicker() {
        PoiRepository poiRepository = new PoiRepository();
        poiRepository.getAttractions(countryId, cityId, new PoiRepository.AttractionListCallback() {
            @Override
            public void onSuccess(List<Attraction> attractions) {
                List<PoiRow> rows = new ArrayList<>();
                for (Attraction a : attractions) {
                    rows.add(PoiRow.fromAttraction(a));
                }
                Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
                showPoiPicker("Attractions", rows, selectedAttractionIds);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PlanTripActivity.this,
                        "Nu am putut incarca atractiile: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Deschide bottom sheet-ul cu restaurantele orasului curent selectat. */
    private void openRestaurantsPicker() {
        PoiRepository poiRepository = new PoiRepository();
        poiRepository.getRestaurants(countryId, cityId, new PoiRepository.RestaurantListCallback() {
            @Override
            public void onSuccess(List<Restaurant> restaurants) {
                List<PoiRow> rows = new ArrayList<>();
                for (Restaurant r : restaurants) {
                    rows.add(PoiRow.fromRestaurant(r));
                }
                Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
                showPoiPicker("Restaurants", rows, selectedRestaurantIds);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PlanTripActivity.this,
                        "Nu am putut incarca restaurantele: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Bottom sheet generic, cu cautare live, reutilizat pentru atractii si restaurante. */
    private void showPoiPicker(String title, List<PoiRow> allRows, Set<String> selectedIds) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_poi_picker, null);
        dialog.setContentView(view);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        TextView txtTitle = view.findViewById(R.id.txtPoiPickerTitle);
        EditText edtSearch = view.findViewById(R.id.edtPoiSearch);
        RecyclerView recyclerPoi = view.findViewById(R.id.recyclerPoi);
        TextView txtEmpty = view.findViewById(R.id.txtPoiEmpty);

        txtTitle.setText(title);
        recyclerPoi.setLayoutManager(new LinearLayoutManager(this));

        Runnable applyFilter = () -> {
            String query = edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<PoiRow> filtered = new ArrayList<>();
            for (PoiRow row : allRows) {
                if (query.isEmpty() || row.name.toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(row);
                }
            }
            if (filtered.isEmpty()) {
                recyclerPoi.setVisibility(View.GONE);
                txtEmpty.setVisibility(View.VISIBLE);
            } else {
                recyclerPoi.setVisibility(View.VISIBLE);
                txtEmpty.setVisibility(View.GONE);
                recyclerPoi.setAdapter(new PoiAdapter(filtered, selectedIds,
                        (poiId, isSelected) -> { /* selectia se salveaza deja in Set-ul partajat */ }));
            }
        };

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        applyFilter.run();
        dialog.show();
    }

    /**
     * Incarca orasele din Firestore, le sorteaza alfabetic (tara, apoi oras)
     * si deschide bottom sheet-ul de selectie.
     */
    private void showCityPicker(TextView txtDestName, TextView txtDestCountry, ImageView imgDestination) {
        CityRepository cityRepository = new CityRepository();
        cityRepository.getAllCities(new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                List<City> sorted = new ArrayList<>(cities);
                Collections.sort(sorted, (a, b) -> {
                    int countryCompare = capitalize(a.getCountryId()).compareToIgnoreCase(capitalize(b.getCountryId()));
                    if (countryCompare != 0) return countryCompare;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                openCityPickerDialog(sorted, txtDestName, txtDestCountry, imgDestination);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PlanTripActivity.this,
                        "Nu am putut incarca orasele: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Deschide bottom sheet-ul cu lista de orase grupata pe tara (alfabetic),
     * cu bara de cautare care filtreaza live pe masura ce tastezi.
     */
    private void openCityPickerDialog(List<City> allCitiesSorted, TextView txtDestName,
                                      TextView txtDestCountry, ImageView imgDestination) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_city_picker, null);
        dialog.setContentView(view);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        EditText edtSearch = view.findViewById(R.id.edtCitySearch);
        RecyclerView recyclerCities = view.findViewById(R.id.recyclerCityPicker);
        TextView txtEmptyState = view.findViewById(R.id.txtCityPickerEmpty);

        recyclerCities.setLayoutManager(new LinearLayoutManager(this));

        CityPickerAdapter.OnCitySelectedListener selectListener = selected -> {
            cityId = selected.getId();
            cityName = selected.getName();
            countryId = selected.getCountryId();
            if (txtDestName != null) txtDestName.setText(cityName);
            if (txtDestCountry != null) txtDestCountry.setText(capitalize(countryId));
            updateDestinationImage(imgDestination, cityId);
            dialog.dismiss();
        };

        Runnable applyFilter = () -> {
            String query = edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<CityListRow> rows = buildCityRows(allCitiesSorted, query);
            if (rows.isEmpty()) {
                recyclerCities.setVisibility(View.GONE);
                txtEmptyState.setVisibility(View.VISIBLE);
            } else {
                recyclerCities.setVisibility(View.VISIBLE);
                txtEmptyState.setVisibility(View.GONE);
                recyclerCities.setAdapter(new CityPickerAdapter(rows, selectListener));
            }
        };

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        applyFilter.run();
        dialog.show();
    }

    /**
     * Construieste randurile de afisat (headere de tara + orase), filtrate dupa query.
     * Un header apare doar daca are cel putin un oras care se potriveste cautarii.
     */
    private List<CityListRow> buildCityRows(List<City> sortedCities, String query) {
        List<CityListRow> rows = new ArrayList<>();
        String lastCountry = null;
        for (City city : sortedCities) {
            if (!query.isEmpty() && !city.getName().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            String countryLabel = capitalize(city.getCountryId());
            if (!countryLabel.equals(lastCountry)) {
                rows.add(CityListRow.header(countryLabel));
                lastCountry = countryLabel;
            }
            rows.add(CityListRow.cityRow(city));
        }
        return rows;
    }

    /**
     * Cauta un drawable local cu numele exact al cityId (ex: "rome" -> R.drawable.rome).
     * Daca nu exista poza pentru orasul respectiv, cade pe un placeholder generic.
     */
    private void updateDestinationImage(ImageView imageView, String cityId) {
        if (imageView == null || cityId == null) return;
        int imageResId = getResources().getIdentifier(cityId, "drawable", getPackageName());
        if (imageResId == 0) {
            imageResId = R.drawable.interests_prelogin_img_3; // fallback generic
        }
        imageView.setImageResource(imageResId);
    }

    private void switchToFlexible() {
        isFlexibleMode = true;
        if (layoutFlexible != null)
            layoutFlexible.setVisibility(View.VISIBLE);
        if (layoutExactDates != null)
            layoutExactDates.setVisibility(View.GONE);
        if (btnFlexible != null) {
            btnFlexible.setBackgroundResource(R.drawable.bg_toggle_selected);
            btnFlexible.setTextColor(getColor(R.color.white));
            btnFlexible.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (btnExactDates != null) {
            btnExactDates.setBackgroundResource(android.R.color.transparent);
            btnExactDates.setTextColor(getColor(R.color.navy_blue_1));
            btnExactDates.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        departureDate = -1;
        returnDate = -1;
        selectingDeparture = true;
    }

    private void switchToExactDates() {
        isFlexibleMode = false;
        if (layoutFlexible != null)
            layoutFlexible.setVisibility(View.GONE);
        if (layoutExactDates != null)
            layoutExactDates.setVisibility(View.VISIBLE);
        if (cardDateResult != null)
            cardDateResult.setVisibility(View.GONE);
        if (txtDateSelection != null)
            txtDateSelection.setText("Select your departure date");
        if (btnExactDates != null) {
            btnExactDates.setBackgroundResource(R.drawable.bg_toggle_selected);
            btnExactDates.setTextColor(getColor(R.color.white));
            btnExactDates.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (btnFlexible != null) {
            btnFlexible.setBackgroundResource(android.R.color.transparent);
            btnFlexible.setTextColor(getColor(R.color.navy_blue_1));
            btnFlexible.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void updateDaysDisplay() {
        if (txtDays != null)
            txtDays.setText(String.valueOf(currentDays));
        if (txtNights != null)
            txtNights.setText((currentDays - 1) + " nights");
    }

    private void setDays(int days, TextView selectedChip) {
        currentDays = days;
        updateDaysDisplay();
        resetChips();
        if (selectedChip != null) {
            selectedChip.setBackgroundResource(R.drawable.bg_chip_selected);
            selectedChip.setTextColor(getColor(R.color.white));
        }
    }

    private void resetChips() {
        if (chipWeekend != null) {
            chipWeekend.setBackgroundResource(R.drawable.bg_chip);
            chipWeekend.setTextColor(getColor(R.color.navy_blue_1));
        }
        if (chip1Week != null) {
            chip1Week.setBackgroundResource(R.drawable.bg_chip);
            chip1Week.setTextColor(getColor(R.color.navy_blue_1));
        }
        if (chip2Weeks != null) {
            chip2Weeks.setBackgroundResource(R.drawable.bg_chip);
            chip2Weeks.setTextColor(getColor(R.color.navy_blue_1));
        }
    }

    private void showDateResult() {
        if (departureDate == -1 || returnDate == -1) return;
        long diffMs = returnDate - departureDate;
        int diffDays = (int) (diffMs / (1000 * 60 * 60 * 24));
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        String departure = sdf.format(new Date(departureDate));
        String returnD = sdf.format(new Date(returnDate));
        if (txtSelectedDates != null)
            txtSelectedDates.setText(departure + " – " + returnD);
        if (txtResultDays != null)
            txtResultDays.setText(String.valueOf(diffDays));
        if (txtResultNights != null)
            txtResultNights.setText((diffDays - 1) + " nights");
        if (cardDateResult != null)
            cardDateResult.setVisibility(View.VISIBLE);
        if (txtDateSelection != null)
            txtDateSelection.setText("✓ Dates selected");
        View btnReset = findViewById(R.id.btnResetDates);
        if (btnReset != null)
            btnReset.setVisibility(View.VISIBLE);
    }
}