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
import java.util.List;
import java.util.Locale;

public class JourneyActivity extends AppCompatActivity {

    private LinearLayout containerActiveSection;
    private LinearLayout containerCompletedSection;

    private TripRepository tripRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── 1. ACTIVARE EDGE-TO-EDGE FĂRĂ STRATUL GRI (SCRIM) ──
        // Forțăm sistemul să nu adauge nicio nuanță automată de fundal (scrim) deasupra barei de status
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT) // Primește un singur parametru!
        );
        setContentView(R.layout.activity_journey);

        // ── 2. ASIGURARE TRANSPARENȚĂ ȘI ICONIȚE NEGRE ──
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);

        // Dezactivăm explicit orice protecție de contrast la nivel de window, dacă există
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(true); // Schimbat numele metodei în cel nativ corect
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true); // Iconițe și ceas de culoare neagră
        }

        // ── GESTIONARE INSETS (PADDING) ──
        View rootLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ── INITIALIZARE CONTINEARE TRASURI ──
        containerActiveSection = findViewById(R.id.containerActiveSection);
        containerCompletedSection = findViewById(R.id.containerCompletedSection);

        tripRepository = new TripRepository();

        // ── LOGICĂ SWITCH PENTRU TOGGLE BUTTON ──
        // ── LOGICĂ SWITCH CU COLORARE DINAMICĂ (JAVA OVERRIDE) ──
        MaterialButtonToggleGroup toggleGroupJourney = findViewById(R.id.toggleGroupJourney);
        com.google.android.material.button.MaterialButton btnTabActive = findViewById(R.id.btnTabActive);
        com.google.android.material.button.MaterialButton btnTabCompleted = findViewById(R.id.btnTabCompleted);

// Culorile extrase din resursele tale
        int turqColor = ContextCompat.getColor(this, R.color.light_turq_1);
        int creamColor = ContextCompat.getColor(this, R.color.cream);
        int navyColor = ContextCompat.getColor(this, R.color.navy_blue_1);

// Setăm starea vizuală inițială (Active e selectat la pornire)
        btnTabActive.setBackgroundColor(turqColor);
        btnTabActive.setTextColor(creamColor);
        btnTabCompleted.setBackgroundColor(Color.TRANSPARENT);
        btnTabCompleted.setTextColor(navyColor);

        toggleGroupJourney.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTabActive) {
                    // Schimbă panourile
                    containerActiveSection.setVisibility(View.VISIBLE);
                    containerCompletedSection.setVisibility(View.GONE);

                    // Colorează Active -> Full Turquoise + Text Crem
                    btnTabActive.setBackgroundColor(turqColor);
                    btnTabActive.setTextColor(creamColor);

                    // Resetează Completed -> Doar contur (Transparent interior) + Text Navy
                    btnTabCompleted.setBackgroundColor(Color.TRANSPARENT);
                    btnTabCompleted.setTextColor(navyColor);

                } else if (checkedId == R.id.btnTabCompleted) {
                    // Schimbă panourile
                    containerActiveSection.setVisibility(View.GONE);
                    containerCompletedSection.setVisibility(View.VISIBLE);

                    // Colorează Completed -> Full Turquoise + Text Crem
                    btnTabCompleted.setBackgroundColor(turqColor);
                    btnTabCompleted.setTextColor(creamColor);

                    // Resetează Active -> Doar contur (Transparent interior) + Text Navy
                    btnTabActive.setBackgroundColor(Color.TRANSPARENT);
                    btnTabActive.setTextColor(navyColor);
                }
            }
        });

        // ── INITIALIZARE BOTTOM NAVIGATION ──
        LinearLayout bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            View btnNavDiscover = bottomNav.getChildAt(0);
            View btnNavMap      = bottomNav.getChildAt(1);
            View btnNavWishlist = bottomNav.getChildAt(2);
            View btnNavAi       = bottomNav.getChildAt(3);

            if (btnNavMap instanceof LinearLayout) {
                LinearLayout mapLayout = (LinearLayout) btnNavMap;
                ImageView imgMap = (ImageView) mapLayout.getChildAt(0);
                TextView txtMap = (TextView) mapLayout.getChildAt(1);
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

    /**
     * onResume, nu onCreate: daca navigarea intre taburi reordoneaza aceeasi
     * instanta in fata (FLAG_ACTIVITY_REORDER_TO_FRONT), onCreate NU se mai
     * ruleaza a doua oara, dar onResume da - asa raman datele mereu proaspete.
     */
    @Override
    protected void onResume() {
        super.onResume();

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        // Mai intai mutam automat trips-urile "planned" care s-au terminat efectiv
        // (a trecut ultima zi), abia apoi incarcam listele - ca sa fie mereu corecte.
        tripRepository.autoCompleteExpiredTrips(firebaseUser.getUid(), new TripRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                loadTrips();
            }

            @Override
            public void onError(String errorMessage) {
                loadTrips(); // chiar daca esueaza mutarea automata, tot incarcam ce exista
            }
        });
    }

    /**
     * Incarca savedTrips (impartite in planned/draft dupa camp status) si completedTrips
     * din Firestore, si populeaza cele 3 RecyclerView-uri.
     */
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

        tripRepository.getSavedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                List<Trip> planned = new ArrayList<>();
                List<Trip> drafts = new ArrayList<>();
                for (Trip trip : savedTrips) {
                    if ("draft".equals(trip.getStatus())) {
                        drafts.add(trip);
                    } else {
                        planned.add(trip);
                    }
                }

                recyclerPlanned.setAdapter(new TripAdapter(planned, true));
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
                recyclerCompleted.setAdapter(new TripAdapter(completedTrips, false));
                txtNoCompletedTrips.setVisibility(completedTrips.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                txtNoCompletedTrips.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Bottom sheet stilizat, cu 2 butoane, care apare la long-press pe un card de draft. */
    /**
     * Tap simplu pe un draft (nu long-press) -> deschide PlanTripActivity cu
     * datele deja completate, ca sa poti modifica orice inainte de a salva din nou.
     */
    private void openDraftForEditing(Trip trip) {
        openDraftForEditing(trip, false);
    }

    /**
     * @param forceExactDates daca e true, PlanTripActivity porneste direct pe tab-ul
     *                        "Exact dates" cu calendarul (folosit de butonul "Plan this trip"),
     *                        chiar daca draft-ul era flexibil (fara date).
     */
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
        txtTitle.setText(trip.getCityName());

        view.findViewById(R.id.btnPlanThisTrip).setOnClickListener(v -> {
            dialog.dismiss();
            openDraftForEditing(trip, true); // true = forteaza tab-ul Exact dates, cu calendarul
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
        txtMessage.setText("Are you sure you want to remove \"" + trip.getCityName() + "\" from your drafts?");

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
                            Toast.makeText(JourneyActivity.this,
                                    "Couldn't delete: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }

}