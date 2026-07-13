package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiscoverActivity extends AppCompatActivity {

    private TripRepository tripRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── ACTIVARE TRANSPARENȚĂ STATUS BAR ──
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_discover);

        tripRepository = new TripRepository();

        // ── GESTIONARE INSETS (EDGE-TO-EDGE) ──
        View rootLayout = findViewById(R.id.discoverRoot);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Păstrăm padding-ul tău inițial (doar pentru bottom, sau adaugă top/left/right după preferințe)
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Navigare planificare călătorie
        findViewById(R.id.btnPlanTrip).setOnClickListener(v -> {
            startActivity(new Intent(DiscoverActivity.this, PlanTripActivity.class));
        });

        findViewById(R.id.btnAI).setOnClickListener(v -> {
            startActivity(new Intent(DiscoverActivity.this, CityDetailActivity.class));
        });

        findViewById(R.id.searchBar).setOnClickListener(v -> {
            startActivity(new Intent(DiscoverActivity.this, SearchActivity.class));
        });

        // Navigare setări cont apăsând pe iconița/avatarul de sus
        TextView txtAvatar = findViewById(R.id.txtAvatar);
        if (txtAvatar != null) {
            txtAvatar.setOnClickListener(v -> {
                Intent intent = new Intent(DiscoverActivity.this, AccountSettingsActivity.class);
                startActivity(intent);
            });
        }

        TextView txtSeeAll = findViewById(R.id.txtSeeAll);
        if (txtSeeAll != null) {
            txtSeeAll.setOnClickListener(v ->
                    startActivity(new Intent(DiscoverActivity.this, RecommendedActivity.class)));
        }

        // ── INIȚIALIZARE BOTTOM NAVIGATION MENTALITATE TAB-URI ──
        LinearLayout bottomNav = findViewById(R.id.bottomNav);

        if (bottomNav != null) {
            View btnNavDiscover = bottomNav.getChildAt(0); // Primul element: Discover (Curent)
            View btnNavMap      = bottomNav.getChildAt(1); // Al doilea element: Map (Explore)
            View btnNavWishlist = bottomNav.getChildAt(2); // Al treilea element: Wishlist (Journey)
            View btnNavAi       = bottomNav.getChildAt(3); // Al patrulea element: AI

            // 1. Discover devine activ (Turcoaz `@color/light_turq_1`)
            if (btnNavDiscover instanceof LinearLayout) {
                LinearLayout discoverLayout = (LinearLayout) btnNavDiscover;
                ImageView imgDiscover = (ImageView) discoverLayout.getChildAt(0);
                TextView txtDiscover = (TextView) discoverLayout.getChildAt(1);

                int turqColor = ContextCompat.getColor(this, R.color.light_turq_1);
                if (imgDiscover != null) imgDiscover.setColorFilter(turqColor);
                if (txtDiscover != null) {
                    txtDiscover.setTextColor(turqColor);
                    txtDiscover.setTypeface(null, android.graphics.Typeface.BOLD);
                }
            }

            // 2. Map devine inactiv (Gri) în caz că preia stări reziduale din XML
            if (btnNavMap instanceof LinearLayout) {
                LinearLayout mapLayout = (LinearLayout) btnNavMap;
                ImageView imgMap = (ImageView) mapLayout.getChildAt(0);
                TextView txtMap = (TextView) mapLayout.getChildAt(1);
                if (imgMap != null) imgMap.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
                if (txtMap != null) {
                    txtMap.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    txtMap.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }

            // ── ASCULTĂTORI CLICK PENTRU SCHIMBAREA ECRANELOR ──
            if (btnNavMap != null) {
                btnNavMap.setOnClickListener(v -> {
                    Intent intent = new Intent(DiscoverActivity.this, ExploreActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavWishlist != null) {
                btnNavWishlist.setOnClickListener(v -> {
                    Intent intent = new Intent(DiscoverActivity.this, JourneyActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavAi != null) {
                btnNavAi.setOnClickListener(v -> {
                    Intent intent = new Intent(DiscoverActivity.this, AIHubActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }
        }
    }

    /**
     * onResume, nu onCreate: daca navigarea intre taburi reordoneaza aceeasi
     * instanta in fata, onCreate nu se mai ruleaza, dar onResume da - asa
     * cardul "Your next trip" ramane mereu actualizat.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadNextTrip();
        loadRecommendations();
        loadUserGreeting();
    }

    /**
     * Cauta printre savedTrips cel cu status "planned", cu data reala (nu draft flexibil
     * fara data) si cu countdown-ul cel mai mic (cea mai apropiata calatorie viitoare).
     * Daca nu exista niciuna, ascunde toata sectiunea "Your next trip".
     */
    private void loadNextTrip() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        // Mutam automat trips-urile expirate inainte de a cauta cea mai apropiata calatorie,
        // ca sa nu ramana blocate ca "planned" doar pentru ca userul nu a deschis Wishlist.
        tripRepository.autoCompleteExpiredTrips(firebaseUser.getUid(), new TripRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                fetchAndBindNextTrip(firebaseUser.getUid());
            }

            @Override
            public void onError(String errorMessage) {
                fetchAndBindNextTrip(firebaseUser.getUid());
            }
        });
    }

    private void fetchAndBindNextTrip(String uid) {
        tripRepository.getSavedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                Trip closest = null;
                for (Trip trip : savedTrips) {
                    if (!"planned".equals(trip.getStatus())) continue;
                    if (trip.getStartDate() == null) continue; // draft flexibil, fara data - ignorat
                    if (trip.hasEnded()) continue; // calatoria s-a terminat deja efectiv

                    trip.recalculateCountdown();

                    if (closest == null || trip.getDaysUntilTrip() < closest.getDaysUntilTrip()) {
                        closest = trip;
                    }
                }

                if (closest == null) {
                    showEmptyTripState();
                } else {
                    bindNextTrip(closest);
                }
            }

            @Override
            public void onError(String errorMessage) {
                showEmptyTripState();
            }
        });
    }

    /** Cardul alb ramane vizibil mereu; doar arata mesajul de incurajare in loc de detalii de trip. */
    private void showEmptyTripState() {
        setTripContentVisibility(View.GONE);
        View placeholder = findViewById(R.id.layoutNoTripPlaceholder);
        if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
    }

    private void setTripContentVisibility(int visibility) {
        View imgTrip = findViewById(R.id.imgTrip);
        View txtDestinationLabel = findViewById(R.id.txtDestinationLabel);
        View layoutDays = findViewById(R.id.layoutDays);
        View txtDates = findViewById(R.id.txtDates);
        View progressTrip = findViewById(R.id.progressTrip);

        if (imgTrip != null) imgTrip.setVisibility(visibility);
        if (txtDestinationLabel != null) txtDestinationLabel.setVisibility(visibility);
        if (layoutDays != null) layoutDays.setVisibility(visibility);
        if (txtDates != null) txtDates.setVisibility(visibility);
        if (progressTrip != null) progressTrip.setVisibility(visibility);
    }

    private void bindNextTrip(Trip trip) {
        setTripContentVisibility(View.VISIBLE);
        View placeholder = findViewById(R.id.layoutNoTripPlaceholder);
        if (placeholder != null) placeholder.setVisibility(View.GONE);

        TextView txtDestinationLabel = findViewById(R.id.txtDestinationLabel);
        TextView txtCountdownNumber = findViewById(R.id.txtCountdownNumber);
        TextView txtCountdownLabel = findViewById(R.id.txtCountdownLabel);
        TextView txtDates = findViewById(R.id.txtDates);
        ImageView imgTrip = findViewById(R.id.imgTrip);
        ProgressBar progressTrip = findViewById(R.id.progressTrip);

        if (txtDestinationLabel != null) {
            String label = (trip.getCityName() + ", " + capitalize(trip.getCountryId())).toUpperCase(Locale.ROOT);
            txtDestinationLabel.setText(label);
        }

        if (trip.isOngoing()) {
            if (txtCountdownNumber != null) txtCountdownNumber.setText("");
            if (txtCountdownLabel != null) {
                txtCountdownLabel.setText("Trip in progress!");
                txtCountdownLabel.setTextSize(18f);
            }
        } else {
            if (txtCountdownNumber != null) txtCountdownNumber.setText(String.valueOf(trip.getDaysUntilTrip()));
            if (txtCountdownLabel != null) {
                txtCountdownLabel.setText("days to go");
                txtCountdownLabel.setTextSize(22f);
            }
        }

        if (txtDates != null) {
            txtDates.setText(formatDateRange(trip.getStartDate(), trip.getEndDate()) + " · " + trip.getDaysCount() + " days");
        }

        if (imgTrip != null) {
            int imageResId = getResources().getIdentifier(trip.getCityId(), "drawable", getPackageName());
            if (imageResId == 0) imageResId = R.drawable.rome;
            imgTrip.setImageResource(imageResId);
        }

        if (progressTrip != null) {
            // Aproximare: consideram 30 de zile ca "punct de start" al planificarii tipice.
            // Nu stocam data la care a fost creat trip-ul, deci nu putem calcula exact
            // cat % din asteptare a trecut - e doar o reprezentare vizuala rezonabila.
            int daysUntil = trip.getDaysUntilTrip();
            int progress = (int) Math.max(0, Math.min(100, 100 - (daysUntil * 100 / 30.0)));
            progressTrip.setProgress(progress);
        }
    }

    private String formatDateRange(String startDate, String endDate) {
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d", Locale.ENGLISH);
            String start = displayFormat.format(isoFormat.parse(startDate));
            String end = displayFormat.format(isoFormat.parse(endDate));
            return start + " – " + end;
        } catch (Exception e) {
            return "";
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Ia interesele userului curent + toate orasele, calculeaza % match pentru fiecare
     * (RecommendationEngine) si afiseaza primele 2 in cardurile "Recommended for you".
     */
    private void loadRecommendations() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        AuthRepository authRepository = new AuthRepository();
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                CityRepository cityRepository = new CityRepository();
                cityRepository.getAllCities(new CityRepository.CityListCallback() {
                    @Override
                    public void onSuccess(List<City> cities) {
                        List<RecommendationEngine.CityMatch> ranked =
                                RecommendationEngine.rankCities(user.getInterests(), cities);
                        bindTopRecommendations(ranked);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // Lasam cardurile cum sunt (fallback static din XML), fara sa stricam UI-ul
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                // La fel, fallback silentios
            }
        });
    }

    private void bindTopRecommendations(List<RecommendationEngine.CityMatch> ranked) {
        if (ranked.isEmpty()) return;

        ImageView imgRec1 = findViewById(R.id.imgRec1);
        TextView txtCityName1 = findViewById(R.id.txtCityName1);
        TextView txtRating1 = findViewById(R.id.txtRating1);
        TextView txtMatch1 = findViewById(R.id.txtMatch1);
        bindRecommendationCard(ranked.get(0), imgRec1, txtCityName1, txtRating1, txtMatch1);

        if (ranked.size() > 1) {
            ImageView imgRec2 = findViewById(R.id.imgRec2);
            TextView txtCityName2 = findViewById(R.id.txtCityName2);
            TextView txtRating2 = findViewById(R.id.txtRating2);
            TextView txtMatch2 = findViewById(R.id.txtMatch2);
            bindRecommendationCard(ranked.get(1), imgRec2, txtCityName2, txtRating2, txtMatch2);
        }
    }

    private void bindRecommendationCard(RecommendationEngine.CityMatch match, ImageView img,
                                        TextView name, TextView rating, TextView matchText) {
        City city = match.city;
        if (name != null) name.setText(city.getName());

        double ratingValue = city.getRating() != null ? city.getRating() : 0.0;
        if (rating != null) rating.setText(String.format(Locale.US, "⭐ %.1f", ratingValue));

        if (matchText != null) {
            matchText.setText(capitalize(city.getCountryId()) + " · " + match.matchPercent + "% match");
        }

        if (img != null) {
            int imageResId = getResources().getIdentifier(city.getId(), "drawable", getPackageName());
            if (imageResId == 0) imageResId = R.drawable.rome;
            img.setImageResource(imageResId);
        }
    }

    /** Incarca numele real al userului logat, pune prenumele in "Hi, {prenume}" si initialele in bulina. */
    private void loadUserGreeting() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        AuthRepository authRepository = new AuthRepository();
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                TextView txtHi = findViewById(R.id.txtHi);
                if (txtHi != null && user.getName() != null && !user.getName().isEmpty()) {
                    String firstName = user.getName().trim().split("\\s+")[0];
                    txtHi.setText("Hi, " + firstName + " 👋");
                }

                TextView txtAvatar = findViewById(R.id.txtAvatar);
                if (txtAvatar != null && user.getAvatarInitials() != null) {
                    txtAvatar.setText(user.getAvatarInitials());
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Lasam textul static din XML daca nu putem incarca profilul
            }
        });
    }
}