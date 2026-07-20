package com.example.mobile_app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JourneyActivity extends AppCompatActivity {

    private LinearLayout containerActiveSection;
    private LinearLayout containerCompletedSection;

    private TripRepository tripRepository;
    private CityRepository cityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_journey);

        Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        View rootLayout = findViewById(R.id.wishlistRoot);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return insets;
            });
        }

        containerActiveSection = findViewById(R.id.containerActiveSection);
        containerCompletedSection = findViewById(R.id.containerCompletedSection);

        tripRepository = new TripRepository();
        cityRepository = new CityRepository();

        MaterialButtonToggleGroup toggleGroupJourney = findViewById(R.id.toggleGroupJourney);
        com.google.android.material.button.MaterialButton btnTabActive = findViewById(R.id.btnTabActive);
        com.google.android.material.button.MaterialButton btnTabCompleted = findViewById(R.id.btnTabCompleted);

        int turqColor = ContextCompat.getColor(this, R.color.light_turq_1);
        int creamColor = ContextCompat.getColor(this, R.color.cream);
        int navyColor = ContextCompat.getColor(this, R.color.navy_blue_1);

        btnTabActive.setBackgroundColor(turqColor);
        btnTabActive.setTextColor(creamColor);
        btnTabCompleted.setBackgroundColor(Color.TRANSPARENT);
        btnTabCompleted.setTextColor(navyColor);

        toggleGroupJourney.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTabActive) {
                    containerActiveSection.setVisibility(View.VISIBLE);
                    containerCompletedSection.setVisibility(View.GONE);

                    btnTabActive.setBackgroundColor(turqColor);
                    btnTabActive.setTextColor(creamColor);
                    btnTabCompleted.setBackgroundColor(Color.TRANSPARENT);
                    btnTabCompleted.setTextColor(navyColor);

                } else if (checkedId == R.id.btnTabCompleted) {
                    containerActiveSection.setVisibility(View.GONE);
                    containerCompletedSection.setVisibility(View.VISIBLE);

                    btnTabCompleted.setBackgroundColor(turqColor);
                    btnTabCompleted.setTextColor(creamColor);
                    btnTabActive.setBackgroundColor(Color.TRANSPARENT);
                    btnTabActive.setTextColor(navyColor);
                }
            }
        });

        LinearLayout bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            View btnNavDiscover = bottomNav.getChildAt(0);
            View btnNavMap      = bottomNav.getChildAt(1);
            View btnNavWishlist = bottomNav.getChildAt(2);
            View btnNavAi       = bottomNav.getChildAt(3);

            if (btnNavMap instanceof LinearLayout) {
                LinearLayout mapLayout = (LinearLayout) btnNavMap;
                ImageView imgMap = (ImageView) mapLayout.getChildAt(0);
                TextView txtMap = mapLayout.getChildAt(1) instanceof TextView ? (TextView) mapLayout.getChildAt(1) : null;
                if (imgMap != null) imgMap.setImageTintList(null);
                if (imgMap != null) imgMap.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
                if (txtMap != null) {
                    txtMap.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    txtMap.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }

            if (btnNavWishlist instanceof LinearLayout) {
                LinearLayout wishlistLayout = (LinearLayout) btnNavWishlist;
                ImageView imgWish = (ImageView) wishlistLayout.getChildAt(0);
                TextView txtWish = (TextView) wishlistLayout.getChildAt(1);

                if (imgWish != null) imgWish.setColorFilter(turqColor);
                if (txtWish != null) {
                    txtWish.setTextColor(turqColor);
                    txtWish.setTypeface(null, android.graphics.Typeface.BOLD);
                }
            }

            if (btnNavDiscover != null) {
                btnNavDiscover.setOnClickListener(v -> {
                    Intent intent = new Intent(JourneyActivity.this, DiscoverActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavMap != null) {
                btnNavMap.setOnClickListener(v -> {
                    Intent intent = new Intent(JourneyActivity.this, ExploreActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavAi != null) {
                btnNavAi.setOnClickListener(v -> {
                    Intent intent = new Intent(JourneyActivity.this, AIHubActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        tripRepository.autoCompleteExpiredTrips(firebaseUser.getUid(), new TripRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                loadTrips();
            }

            @Override
            public void onError(String errorMessage) {
                loadTrips();
            }
        });
    }

    private void loadTrips() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();

        RecyclerView recyclerPlanned = findViewById(R.id.recyclerPlanned);
        RecyclerView recyclerDrafts = findViewById(R.id.recyclerDrafts);
        RecyclerView recyclerCompleted = findViewById(R.id.recyclerCompleted);
        View txtNoPlannedTrips = findViewById(R.id.txtNoPlannedTrips);
        View txtNoDrafts = findViewById(R.id.txtNoDrafts);
        View txtNoCompletedTrips = findViewById(R.id.txtNoCompletedTrips);

        recyclerPlanned.setLayoutManager(new LinearLayoutManager(this));
        recyclerDrafts.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerCompleted.setLayoutManager(new LinearLayoutManager(this));

        cityRepository.getAllCities(new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                Map<String, String> cityImageMap = new HashMap<>();
                for (City c : cities) {
                    if (c.getId() != null && c.getImageUrl() != null) {
                        cityImageMap.put(c.getId().toLowerCase().trim(), c.getImageUrl());
                    }
                }
                bindTripsWithMapping(uid, cityImageMap, recyclerPlanned, recyclerDrafts, recyclerCompleted,
                        txtNoPlannedTrips, txtNoDrafts, txtNoCompletedTrips);
            }

            @Override
            public void onError(String errorMessage) {
                loadTripsWithoutMapping(uid, recyclerPlanned, recyclerDrafts, recyclerCompleted,
                        txtNoPlannedTrips, txtNoDrafts, txtNoCompletedTrips);
            }
        });
    }

    private void bindTripsWithMapping(String uid, Map<String, String> cityImageMap,
                                      RecyclerView recyclerPlanned, RecyclerView recyclerDrafts, RecyclerView recyclerCompleted,
                                      View txtNoPlannedTrips, View txtNoDrafts, View txtNoCompletedTrips) {

        tripRepository.getSavedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                List<Trip> planned = new ArrayList<>();
                List<Trip> drafts = new ArrayList<>();
                for (Trip trip : savedTrips) {
                    if (trip.getCityId() != null) {
                        String officialUrl = cityImageMap.get(trip.getCityId().toLowerCase().trim());
                        if (officialUrl != null) {
                            trip.setImageUrl(officialUrl);
                        }
                    }

                    if ("draft".equals(trip.getStatus())) {
                        // 💡 CONDIȚIA SALVATOARE: Dacă are startDate, înseamnă că este un itinerariu AI vizualizat pe hartă,
                        // nu o schiță simplă creată manual, așa că NU o adăugăm în lista vizibilă de drafts!
                        if (trip.getStartDate() == null) {
                            drafts.add(trip);
                        }
                    } else {
                        planned.add(trip);
                    }
                }

                recyclerPlanned.setAdapter(new TripAdapter(planned, true, trip -> {

                }));
                txtNoPlannedTrips.setVisibility(planned.isEmpty() ? View.VISIBLE : View.GONE);

                recyclerDrafts.setAdapter(new TripDraftAdapter(drafts,
                        trip -> openDraftForEditing(trip),
                        trip -> showDraftOptionsDialog(trip)));
                txtNoDrafts.setVisibility(drafts.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                txtNoPlannedTrips.setVisibility(View.VISIBLE);
                txtNoDrafts.setVisibility(View.VISIBLE);
            }
        });

        tripRepository.getCompletedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> completedTrips) {
                for (Trip trip : completedTrips) {
                    if (trip.getCityId() != null) {
                        String officialUrl = cityImageMap.get(trip.getCityId().toLowerCase().trim());
                        if (officialUrl != null) {
                            trip.setImageUrl(officialUrl);
                        }
                    }
                }
                recyclerCompleted.setAdapter(new TripAdapter(completedTrips, false, trip -> openTripRoute(trip, false)));
                txtNoCompletedTrips.setVisibility(completedTrips.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                txtNoCompletedTrips.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadTripsWithoutMapping(String uid, RecyclerView recyclerPlanned, RecyclerView recyclerDrafts, RecyclerView recyclerCompleted, View txtNoPlannedTrips, View txtNoDrafts, View txtNoCompletedTrips) {
        tripRepository.getSavedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                List<Trip> planned = new ArrayList<>();
                List<Trip> drafts = new ArrayList<>();

                for (Trip trip : savedTrips) {
                    if ("draft".equals(trip.getStatus())) {
                        // 💡 Filtrare inteligentă:
                        if (trip.getStartDate() == null) {
                            drafts.add(trip);
                        }
                    } else {
                        planned.add(trip);
                    }
                } // <--- Închide bucla for

                // Setezi adaptoarele și vizibilitatea textelor exact cum aveai înainte
                recyclerPlanned.setAdapter(new TripAdapter(planned, true, trip -> openTripRoute(trip, false)));
                txtNoPlannedTrips.setVisibility(planned.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerDrafts.setAdapter(new TripDraftAdapter(drafts, trip -> openDraftForEditing(trip), trip -> showDraftOptionsDialog(trip)));
                txtNoDrafts.setVisibility(drafts.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                txtNoPlannedTrips.setVisibility(View.VISIBLE);
                txtNoDrafts.setVisibility(View.VISIBLE);
            }
        });

        // Rămâne neatinsă și încărcarea pentru completed trips mai jos...
        tripRepository.getCompletedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> completedTrips) {
                recyclerCompleted.setAdapter(new TripAdapter(completedTrips, false, trip -> openTripRoute(trip, false)));
                txtNoCompletedTrips.setVisibility(completedTrips.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onError(String errorMessage) {
                txtNoCompletedTrips.setVisibility(View.VISIBLE);
            }
        });
    }
    /**
     * Deschide ExploreActivity în modul "traseu pentru un oraș", cu datele trip-ului curent.
     */
    private void openTripRoute(Trip trip, boolean isDraft) {
        Intent intent = new Intent(JourneyActivity.this, ExploreActivity.class);
        intent.putExtra("cityId", trip.getCityId());
        intent.putExtra("cityName", trip.getCityName());
        intent.putExtra("isDraft", isDraft);
        if (trip.getTripId() != null) {
            intent.putExtra("tripId", trip.getTripId());
        }
        startActivity(intent);
    }

    private void openDraftForEditing(Trip trip) {
        openDraftForEditing(trip, true);
    }

    private void openDraftForEditing(Trip trip, boolean forceExactDates) {
        Intent intent = new Intent(this, PlanTripActivity.class);
        intent.putExtra("cityId", trip.getCityId());
        intent.putExtra("cityName", trip.getCityName());
        intent.putExtra("countryId", trip.getCountryId());
        intent.putExtra("editingTripId", trip.getTripId());
        intent.putExtra("existingDaysCount", trip.getDaysCount());
        if (trip.getStartDate() != null) intent.putExtra("existingStartDate", trip.getStartDate());
        if (trip.getEndDate() != null) intent.putExtra("existingEndDate", trip.getEndDate());
        if (forceExactDates) intent.putExtra("forceExactDates", true);
        startActivity(intent);
    }

    private void showDraftOptionsDialog(Trip trip) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_draft_options, null);
        dialog.setContentView(view);

        TextView txtTitle = view.findViewById(R.id.txtDraftOptionsTitle);
        if (txtTitle != null) txtTitle.setText(trip.getCityName());

        view.findViewById(R.id.btnPlanThisTrip).setOnClickListener(v -> {
            dialog.dismiss();
            openDraftForEditing(trip, true);
        });

        view.findViewById(R.id.btnDeleteDraft).setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteDraft(trip);
        });

        dialog.show();
    }

    private void confirmDeleteDraft(Trip trip) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_confirm_delete, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setAttributes(params);
        }

        TextView txtMessage = view.findViewById(R.id.txtDeleteDialogMessage);
        if (txtMessage != null) txtMessage.setText("Are you sure you want to remove \"" + trip.getCityName() + "\" from your drafts?");

        view.findViewById(R.id.btnCancelDelete).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            dialog.dismiss();
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser == null) return;
            tripRepository.deleteSavedTrip(firebaseUser.getUid(), trip.getTripId(),
                    new TripRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(JourneyActivity.this, "Draft deleted", Toast.LENGTH_SHORT).show();
                            loadTrips();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(JourneyActivity.this, "Couldn't delete: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }
}