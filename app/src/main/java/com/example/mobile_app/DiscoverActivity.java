package com.example.mobile_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DiscoverActivity extends AppCompatActivity {

    private static final String TAG = "DETALII_INCARCARE";
    private TripRepository tripRepository;
    private LottieAnimationView splashOverlay;
    private View overlayContainer;
    private View mainContentContainer;

    private boolean tripDataReady = false;
    private boolean recommendationsReady = false;
    private boolean greetingReady = false;
    private boolean imagesReady = false;

    private Trip pendingNextTrip = null;
    private boolean pendingNoTrip = false;
    private List<RecommendationEngine.CityMatch> pendingRanked = null;
    private User pendingUser = null;

    private final List<String> imagesToPreload = new ArrayList<>();

    private long splashStartTime;
    private static final long MIN_SPLASH_DURATION_MS = 2000;
    private boolean isUnifiedSignupLoading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activitatea DiscoverActivity a pornit.");

        // ── 1. EDGE TO EDGE NATIV ──
        EdgeToEdge.enable(this);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            String lastScreen = AuthScreenPreference.getLastScreen(this);
            Intent intent;

            switch (lastScreen) {
                case AuthScreenPreference.SCREEN_LOGIN:
                    intent = new Intent(this, LoginActivity.class);
                    break;
                case AuthScreenPreference.SCREEN_SIGNUP:
                    intent = new Intent(this, SignUpActivity.class);
                    break;
                default:
                    intent = new Intent(this, MainActivity.class);
                    break;
            }

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_discover);

        Window window = getWindow();

        // Eliminăm explicit flag-ul de Fullscreen care ascundea bara de status
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        // Setăm culorile de transparență curată
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Configurăm iconițele (ceas, baterie) să fie ALBE (luminoase) peste gradientul închis
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            // Curățăm flag-urile vechi de ascundere (SYSTEM_UI_FLAG_FULLSCREEN) și lăsăm doar layout-ul stabil sub bară
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        isUnifiedSignupLoading = getIntent().getBooleanExtra("IS_UNIFIED_SIGNUP_LOADING", false);
        if (isUnifiedSignupLoading) {
            window.setWindowAnimations(0);
            overridePendingTransition(0, 0);
        }

        NotificationHelper.createNotificationChannels(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
            }
        }

// 3. Programează verificarea zilnică a vacanțelor (rulat o singură dată)
// ── CALCULARE DELAY INIȚIAL PENTRU ORA FIXĂ 12:08 AM ──
        java.util.Calendar calendarCurent = java.util.Calendar.getInstance();
        java.util.Calendar calendarTinta = java.util.Calendar.getInstance();

// Setăm ora țintă la 12:08 AM (Ora 0, minutul 8)
        calendarTinta.set(java.util.Calendar.HOUR_OF_DAY,10 );
        calendarTinta.set(java.util.Calendar.MINUTE, 0);
        calendarTinta.set(java.util.Calendar.SECOND, 0);
        calendarTinta.set(java.util.Calendar.MILLISECOND, 0);

// Dacă ora 12:08 AM a trecut deja pentru ziua de azi, programăm pentru mâine
        if (calendarCurent.after(calendarTinta)) {
            calendarTinta.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }

// Calculăm diferența în milisecunde dintre acum și ora 12:08 AM
        long delayInitialMs = calendarTinta.getTimeInMillis() - calendarCurent.getTimeInMillis();

// Programează verificarea periodică (la fiecare 24 de ore) cu delay-ul calculat
        androidx.work.PeriodicWorkRequest tripCheckRequest =
                new androidx.work.PeriodicWorkRequest.Builder(TripNotificationWorker.class, 24, java.util.concurrent.TimeUnit.HOURS)
                        .setInitialDelay(delayInitialMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build();

// Schimbat în REPLACE pentru a recalcula secundele și a preveni decalajele în timp
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "TripNotificationWork",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                tripCheckRequest
        );

// 4. Resetează alarma de inactivitate (se amână cu încă 2 zile de fiecare dată când deschide ecranul)
        androidx.work.OneTimeWorkRequest inactivityRequest =
                new androidx.work.OneTimeWorkRequest.Builder(InactivityWorker.class)
                        .setInitialDelay(2, java.util.concurrent.TimeUnit.DAYS)
                        .build();
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                "InactivityWork",
                androidx.work.ExistingWorkPolicy.REPLACE, // Înlocuiește vechea alarmă cu una proaspătă de 2 zile
                inactivityRequest
        );
        tripRepository = new TripRepository();
        splashOverlay = findViewById(R.id.splashOverlay);
        overlayContainer = findViewById(R.id.splashOverlayContainer);
        mainContentContainer = findViewById(R.id.mainContentContainer);

        splashStartTime = System.currentTimeMillis();

        View rootLayout = findViewById(R.id.discoverRoot);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Punem padding doar jos pentru bara de navigare, lăsând sus 0 ca să treacă sub ceas
                v.setPadding(0, 0, 0, systemBars.bottom);
                return insets;
            });
        }

        View headerView = findViewById(R.id.header);
        if (headerView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int extraPaddingPx = (int) (20 * v.getResources().getDisplayMetrics().density);
                v.setPadding(v.getPaddingLeft(), systemBars.top + extraPaddingPx, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        findViewById(R.id.btnPlanTrip).setOnClickListener(v -> startActivity(new Intent(DiscoverActivity.this, PlanTripActivity.class)));
        findViewById(R.id.btnAI).setOnClickListener(v -> startActivity(new Intent(DiscoverActivity.this, CityDetailActivity.class)));
        findViewById(R.id.searchBar).setOnClickListener(v -> startActivity(new Intent(DiscoverActivity.this, SearchActivity.class)));

        TextView txtAvatar = findViewById(R.id.txtAvatar);
        if (txtAvatar != null) {
            txtAvatar.setOnClickListener(v -> startActivity(new Intent(DiscoverActivity.this, AccountSettingsActivity.class)));
        }

        TextView txtSeeAll = findViewById(R.id.txtSeeAll);
        if (txtSeeAll != null) {
            txtSeeAll.setOnClickListener(v -> startActivity(new Intent(DiscoverActivity.this, RecommendedActivity.class)));
        }

        LinearLayout btnDiscover = findViewById(R.id.btnDiscover);
        LinearLayout btnMap      = findViewById(R.id.btnMap);
        LinearLayout btnWishlist = findViewById(R.id.btnWishlist);
        LinearLayout btnAI       = findViewById(R.id.btnAI);

        int turqColor = ContextCompat.getColor(this, R.color.light_turq_1);
        int inactiveColor = Color.parseColor("#AAAAAA");

        if (btnDiscover != null) {
            ImageView imgDiscover = (ImageView) btnDiscover.getChildAt(0);
            TextView txtDiscover = (TextView) btnDiscover.getChildAt(1);
            if (imgDiscover != null) imgDiscover.setColorFilter(turqColor);
            if (txtDiscover != null) {
                txtDiscover.setTextColor(turqColor);
                txtDiscover.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }

        resetTabVisuals(btnMap, inactiveColor);
        resetTabVisuals(btnWishlist, inactiveColor);
        resetTabVisuals(btnAI, inactiveColor);

        if (btnMap != null) {
            btnMap.setOnClickListener(v -> {
                Intent intent = new Intent(DiscoverActivity.this, ExploreActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        if (btnWishlist != null) {
            btnWishlist.setOnClickListener(v -> {
                Intent intent = new Intent(DiscoverActivity.this, JourneyActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        if (btnAI != null) {
            btnAI.setOnClickListener(v -> {
                Intent intent = new Intent(DiscoverActivity.this, AIHubActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        loadNextTrip();
        loadRecommendations();
        loadUserGreeting();
    }

    private void resetTabVisuals(LinearLayout tabLayout, int color) {
        if (tabLayout != null && tabLayout.getChildCount() >= 2) {
            ImageView img = (ImageView) tabLayout.getChildAt(0);
            TextView txt = (TextView) tabLayout.getChildAt(1);
            if (img != null) img.setColorFilter(color);
            if (txt != null) {
                txt.setTextColor(color);
                txt.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void checkAllReadyAndReveal() {
        if (!tripDataReady || !recommendationsReady || !greetingReady) return;

        if (!imagesReady) {
            preloadRemainingImages();
            return;
        }

        if (pendingNoTrip) {
            showEmptyTripState();
        } else if (pendingNextTrip != null) {
            bindNextTrip(pendingNextTrip);
        }

        if (pendingRanked != null) {
            bindTopRecommendations(pendingRanked);
        }

        if (pendingUser != null) {
            bindGreeting(pendingUser);
        }

        if (mainContentContainer != null) {
            mainContentContainer.setVisibility(View.VISIBLE);
        }

        if (overlayContainer != null) {
            long remainingDelay = 0;

            if (!isUnifiedSignupLoading) {
                long elapsed = System.currentTimeMillis() - splashStartTime;
                remainingDelay = Math.max(0, MIN_SPLASH_DURATION_MS - elapsed);
            }

            overlayContainer.postDelayed(() -> {
                overlayContainer.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction(() -> overlayContainer.setVisibility(View.GONE))
                        .start();
            }, remainingDelay);
        }
    }

    private void preloadRemainingImages() {
        if (imagesToPreload.isEmpty()) {
            imagesReady = true;
            checkAllReadyAndReveal();
            return;
        }

        final int[] remaining = { imagesToPreload.size() };
        for (String url : imagesToPreload) {
            com.bumptech.glide.Glide.with(this)
                    .load(url)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            done();
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            done();
                            return false;
                        }
                        private void done() {
                            remaining[0]--;
                            if (remaining[0] <= 0) {
                                imagesReady = true;
                                checkAllReadyAndReveal();
                            }
                        }
                    })
                    .preload();
        }
    }

    private void loadNextTrip() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "loadNextTrip: FirebaseUser este null!");
            pendingNoTrip = true;
            tripDataReady = true;
            checkAllReadyAndReveal();
            return;
        }

        tripRepository.autoCompleteExpiredTrips(firebaseUser.getUid(), new TripRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                fetchAndBindNextTrip(firebaseUser.getUid());
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "autoCompleteExpiredTrips a dat fail: " + errorMessage);
                fetchAndBindNextTrip(firebaseUser.getUid());
            }
        });
    }

    private void fetchAndBindNextTrip(String uid) {
        tripRepository.getSavedTrips(uid, new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                Trip closestTrip = null;
                for (Trip trip : savedTrips) {
                    if (!"planned".equals(trip.getStatus())) continue;
                    if (trip.getStartDate() == null) continue;
                    if (trip.hasEnded()) continue;

                    trip.recalculateCountdown();

                    if (closestTrip == null || trip.getDaysUntilTrip() < closestTrip.getDaysUntilTrip()) {
                        closestTrip = trip;
                    }
                }

                if (closestTrip == null) {
                    pendingNoTrip = true;
                    tripDataReady = true;
                    checkAllReadyAndReveal();
                } else {
                    final Trip finalClosest = closestTrip;

                    if (finalClosest.getImageUrl() == null || finalClosest.getImageUrl().trim().isEmpty() || "null".equals(finalClosest.getImageUrl())) {
                        CityRepository cityRepository = new CityRepository();
                        cityRepository.getAllCities(new CityRepository.CityListCallback() {
                            @Override
                            public void onSuccess(List<City> cities) {
                                for (City city : cities) {
                                    if (city.getId() != null && city.getId().equalsIgnoreCase(finalClosest.getCityId())) {
                                        finalClosest.setImageUrl(city.getImageUrl());
                                        break;
                                    }
                                }
                                finalizeNextTrip(finalClosest);
                            }

                            @Override
                            public void onError(String errorMessage) {
                                finalizeNextTrip(finalClosest);
                            }
                        });
                    } else {
                        finalizeNextTrip(finalClosest);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "getSavedTrips eroare: " + errorMessage);
                pendingNoTrip = true;
                tripDataReady = true;
                checkAllReadyAndReveal();
            }
        });
    }

    private void finalizeNextTrip(Trip trip) {
        pendingNextTrip = trip;
        if (trip.getImageUrl() != null && trip.getImageUrl().startsWith("http")) {
            imagesToPreload.add(trip.getImageUrl());
        }
        tripDataReady = true;
        checkAllReadyAndReveal();
    }

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
            String rawUrl = trip.getImageUrl();

            if (rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
                com.bumptech.glide.Glide.with(this)
                        .load(rawUrl)
                        .placeholder(R.drawable.rome)
                        .error(R.drawable.rome)
                        .into(imgTrip);
            } else {
                int imageResId = 0;

                if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                    String cleanName = rawUrl.replace(".jpg", "").replace(".png", "").trim().toLowerCase();
                    imageResId = getResources().getIdentifier(cleanName, "drawable", getPackageName());
                }

                if (imageResId == 0 && trip.getCityId() != null) {
                    String fallbackId = trip.getCityId().trim().toLowerCase();
                    imageResId = getResources().getIdentifier(fallbackId, "drawable", getPackageName());
                }

                if (imageResId == 0) {
                    imageResId = R.drawable.rome;
                }

                com.bumptech.glide.Glide.with(this)
                        .load(imageResId)
                        .into(imgTrip);
            }
        }

        if (progressTrip != null) {
            int daysUntil = trip.getDaysUntilTrip();
            int progress = (int) Math.max(0, Math.min(100, 100 - (daysUntil * 100 / 30.0)));
            progressTrip.setProgress(progress);
        }
        CardView cardNextTrip = findViewById(R.id.cardNextTrip);
        if (cardNextTrip != null) {
            cardNextTrip.setOnClickListener(v -> {
                Intent intent = new Intent(DiscoverActivity.this, ExploreActivity.class);
                intent.putExtra("cityId", trip.getCityId());
                intent.putExtra("cityName", trip.getCityName());
                intent.putExtra("isDraft", "draft".equals(trip.getStatus()));
                startActivity(intent);
            });
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

    private void loadRecommendations() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            recommendationsReady = true;
            checkAllReadyAndReveal();
            return;
        }

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
                        pendingRanked = ranked;

                        for (int i = 0; i < Math.min(2, ranked.size()); i++) {
                            String url = ranked.get(i).city.getImageUrl();
                            if (url != null && url.startsWith("http")) {
                                imagesToPreload.add(url);
                            }
                        }

                        recommendationsReady = true;
                        checkAllReadyAndReveal();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "getAllCities eroare: " + errorMessage);
                        recommendationsReady = true;
                        checkAllReadyAndReveal();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "getCurrentUserProfile eroare: " + errorMessage);
                recommendationsReady = true;
                checkAllReadyAndReveal();
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

        View.OnClickListener openDetailsListener = v -> {
            Intent intent = new Intent(DiscoverActivity.this, CityDetailActivity.class);
            intent.putExtra("cityId", city.getId());
            intent.putExtra("countryId", city.getCountryId());
            startActivity(intent);
        };

        if (img != null) img.setOnClickListener(openDetailsListener);
        if (name != null) name.setOnClickListener(openDetailsListener);

        if (img != null) {
            String rawUrl = city.getImageUrl();

            if (rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
                com.bumptech.glide.Glide.with(this)
                        .load(rawUrl)
                        .placeholder(R.drawable.rome)
                        .error(R.drawable.rome)
                        .into(img);
            } else {
                int imageResId = 0;
                if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                    String cleanName = rawUrl.replace(".jpg", "").replace(".png", "").trim().toLowerCase();
                    imageResId = getResources().getIdentifier(cleanName, "drawable", getPackageName());
                }

                if (imageResId == 0 && city.getId() != null) {
                    String fallbackId = city.getId().trim().toLowerCase();
                    imageResId = getResources().getIdentifier(fallbackId, "drawable", getPackageName());
                }

                if (imageResId == 0) {
                    imageResId = R.drawable.rome;
                }

                com.bumptech.glide.Glide.with(this)
                        .load(imageResId)
                        .into(img);
            }
        }
    }

    private void bindGreeting(User user) {
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

    private void loadUserGreeting() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            greetingReady = true;
            checkAllReadyAndReveal();
            return;
        }

        AuthRepository authRepository = new AuthRepository();
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                pendingUser = user;
                greetingReady = true;
                checkAllReadyAndReveal();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "loadUserGreeting eroare: " + errorMessage);
                greetingReady = true;
                checkAllReadyAndReveal();
            }
        });
    }
}