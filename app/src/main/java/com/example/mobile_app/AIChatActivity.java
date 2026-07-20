package com.example.mobile_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.example.mobile_app.ai.IntentClassifier;
import com.example.mobile_app.ai.WeatherHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ecran de chat cu Voiaj AI. Recunoaste intentia mesajului userului (on-device,
 * cu modelul SetFit convertit la ONNX) si ruteaza spre actiunea potrivita.
 * Pentru vreme/buget, foloseste calatoriile salvate ale userului ca context.
 */
public class AIChatActivity extends AppCompatActivity {

    private LinearLayout messagesContainer;
    private NestedScrollView scrollMessages;

    private IntentClassifier intentClassifier;
    private com.example.mobile_app.ai.EmbeddingModel embeddingModel;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Tine minte ultima cautare de destinatie, ca userul sa poata cere "arata-mi alta varianta"
    private List<OrasCuScor> ultimeleRecomandari = new java.util.ArrayList<>();
    private int indexRecomandareCurenta = 0;

    // Itinerariul curent afisat in chat - tinut minte, ca sa poata fi editat prin conversatie
    // ("add X", "remove Y", "swap X for Y"), fara sa fie nevoie de o categorie noua de intentie.
    private Trip tripCurent;
    private Map<String, Attraction> hartaAtractiiCurenta;
    private Map<String, Restaurant> hartaRestauranteCurenta;
    private boolean modificariNesalvate = false; // true dupa o editare, pana la urmatoarea salvare

    // Cand userul a ales "I'll describe it", urmatorul mesaj e tratat DIRECT ca descriere
    // a excursiei (oras + perioada), nu mai trece prin clasificarea normala de intentie.
    private boolean asteptaDescriereItinerar = false;
    private final Map<String, Trip> cacheDrafturi = new HashMap<>();

    /** Pereche oras + scorul lui, folosita doar pentru sortare interna. */
    private static class OrasCuScor {
        final City oras;
        final double scor;

        OrasCuScor(City oras, double scor) {
            this.oras = oras;
            this.scor = scor;
        }
    }
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final TripRepository tripRepository = new TripRepository();
    private final CityRepository cityRepository = new CityRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();



    // VARIABILE NOI DECLARATE PENTRU REMEDIEREA ERORILOR:
    private String modCurent = "ASK_ANYTHING"; // Ține minte starea curentă a chat-ului
    private java.util.Set<String> poiUtilizate = new java.util.HashSet<>(); // Evită POI-urile duplicate


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_aichat);

        Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Forțăm pictogramele de sistem să fie albe/închise
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

        // 2. PADDING PENTRU CONȚINUT (Fără bandă albă)
        View aiChatRoot = findViewById(R.id.aiChatRoot);
        if (aiChatRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(aiChatRoot, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets tastatura = insets.getInsets(WindowInsetsCompat.Type.ime());

                int paddingJos = Math.max(systemBars.bottom, tastatura.bottom);
                v.setPadding(0, 0, 0, paddingJos);

                if (tastatura.bottom > 0 && scrollMessages != null) {
                    scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
                }
                return insets;
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.aiChatRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets tastatura = insets.getInsets(WindowInsetsCompat.Type.ime());

            // 1. Padding header-ul sus (pentru ceas)
            View header = findViewById(R.id.header);
            if (header != null) {
                header.setPadding(header.getPaddingLeft(), systemBars.top + 16, header.getPaddingRight(), header.getPaddingBottom());
            }

            // 2. Padding inputBar jos (pentru navigație/tastatură)
            View inputBar = findViewById(R.id.inputBar);
            if (inputBar != null) {
                int paddingJos = Math.max(systemBars.bottom, tastatura.bottom);
                inputBar.setPadding(inputBar.getPaddingLeft(), inputBar.getPaddingTop(), inputBar.getPaddingRight(), paddingJos + 12);
            }

            // 3. Scroll la ultimul mesaj
            if (tastatura.bottom > 0 && scrollMessages != null) {
                scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
            }

            return WindowInsetsCompat.CONSUMED;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View btnNewChat = findViewById(R.id.btnNewChat);
        if (btnNewChat != null) {
            btnNewChat.setOnClickListener(v -> {
                messagesContainer.removeAllViews();
                modCurent = "ASK_ANYTHING";

                if (ultimeleRecomandari != null) {
                    ultimeleRecomandari.clear();
                }
                indexRecomandareCurenta = 0;

                if (poiUtilizate != null) {
                    poiUtilizate.clear();
                }

                addAiBubble("Hi! I've started a new clean chat. Ask me anything about your next destination, weather, or budgets!");
                scrollMessages.post(() -> scrollMessages.fullScroll(androidx.core.widget.NestedScrollView.FOCUS_DOWN));
                android.widget.Toast.makeText(AIChatActivity.this, "New conversation started", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        messagesContainer = findViewById(R.id.messagesContainer);
        scrollMessages = findViewById(R.id.scrollMessages);
        EditText edtChatInput = findViewById(R.id.edtChatInput);

        executor.execute(() -> {
            try {
                intentClassifier = new IntentClassifier(this);
                embeddingModel = new com.example.mobile_app.ai.EmbeddingModel(this);
            } catch (Exception e) {
                Log.e("AIChat", "Nu s-a putut incarca modelele AI", e);
            }
        });

        findViewById(R.id.btnSendChat).setOnClickListener(v -> {
            String text = edtChatInput.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                addUserBubble(text);
                edtChatInput.setText("");
                proceseazaMesaj(text);
            }
        });

        // ── VERIFICĂRI PARAMETRI INTENT (Optimizat pentru Scurtătura Directă) ──
        boolean directDisplay = getIntent().getBooleanExtra("SHORTCUT_DIRECT_DISPLAY", false);
        String initialPrompt = getIntent().getStringExtra("initialPrompt");
        boolean askAnythingMode = getIntent().getBooleanExtra("askAnythingMode", false);
        String previewCityId = getIntent().getStringExtra("previewCityId");
        String viewSavedTripId = getIntent().getStringExtra("viewSavedTripId");

        // ── BLOC MODIFICAT: Sărim peste întrebări dacă venim din noul flux ──
        if (directDisplay) {
            // Afișăm vizual mesajul în chat ca și cum utilizatorul l-ar fi tastat
            if (initialPrompt != null) {
                addUserBubble(initialPrompt);
            }
            // Lansăm direct funcția ta care desenează itinerariul complet (folosind datele deja stocate în Intent)
            aratatItinerariulPreview();
        } else if (viewSavedTripId != null) {
            aratatItinerariulSalvat(viewSavedTripId);
        } else if (previewCityId != null) {
            aratatItinerariulPreview();
        } else if (initialPrompt != null) {
            addUserBubble(initialPrompt);
            proceseazaMesaj(initialPrompt);
        } else if (askAnythingMode) {
            addAiBubble("Ask me anything about your destination — history, culture, local tips, or anything else!");
        } else {
            addWelcomeMessage();
        }
    }
    /** Mesaj de intampinare cu optiuni apasabile - folosit doar daca se ajunge aici fara niciun mod specific. */
    private void addWelcomeMessage() {
        addAiBubble("Hi! I'm Voiaj AI 👋 You can ask me to:");
        addQuickOption("🧭 Plan an itinerary", "Plan an itinerary for me");
        addQuickOption("☁️ Check the weather", "What's the weather like?");
        addQuickOption("💰 Estimate my budget", "Estimate my trip budget");
        addQuickOption("📍 Recommend a destination", "Where should I go on vacation?");
        addQuickOption("💬 Ask something else", "Tell me something interesting");
    }

    /** O optiune mica, apasabila - la apasare, se comporta exact ca si cum userul ar fi scris acel text. */
    private void addQuickOption(String textAfisat, String promptReal) {
        TextView optiune = new TextView(this);
        optiune.setText(textAfisat);
        optiune.setTextColor(Color.parseColor("#011C40"));
        optiune.setTextSize(13f);
        optiune.setClickable(true);
        optiune.setFocusable(true);

        GradientDrawable fundal = new GradientDrawable();
        fundal.setColor(Color.WHITE);
        fundal.setStroke(2, Color.parseColor("#DDDDDD"));
        fundal.setCornerRadius(18f);
        optiune.setBackground(fundal);

        int paddingH = (int) (14 * getResources().getDisplayMetrics().density);
        int paddingV = (int) (8 * getResources().getDisplayMetrics().density);
        optiune.setPadding(paddingH, paddingV, paddingH, paddingV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START;
        int marginPx = (int) (8 * getResources().getDisplayMetrics().density);
        params.bottomMargin = marginPx;
        optiune.setLayoutParams(params);

        optiune.setOnClickListener(v -> gestioneazaOptiuneSpeciala(promptReal));

        messagesContainer.addView(optiune);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    private void proceseazaMesaj(String text) {
        arataTypingIndicator();

        // Daca tocmai am intrebat "unde si cand", urmatorul mesaj e chiar raspunsul -
        // nu mai trece prin clasificarea normala de intentie.
        if (asteptaDescriereItinerar) {
            asteptaDescriereItinerar = false;
            proceseazaDescriereItinerar(text);
            return;
        }

        // Daca avem un itinerariu activ pe ecran si mesajul pare o comanda de editare,
        // il tratam direct ca editare - nu mai trecem prin clasificarea de intentie normala.
        if (tripCurent != null && esteComandaDeEditare(text)) {
            proceseazaEditareItinerar(text);
            return;
        }

        executor.execute(() -> {
            if (intentClassifier == null) {
                mainHandler.post(() -> {
                    ascundeTypingIndicator();
                    addAiBubble("Sorry, I'm still starting up - try again in a second.");
                });
                return;
            }
            try {
                IntentClassifier.Rezultat rezultat = intentClassifier.clasifica(text);
                Log.d("AIChat", "Intentie: " + rezultat.intentie + " (" + rezultat.increderea + ")");

                if (rezultat != null && rezultat.intentie != null) {
                    mainHandler.post(() -> ruteazaDupaIntentie(rezultat.intentie, text));
                } else {
                    mainHandler.post(() -> {
                        ascundeTypingIndicator();
                        addAiBubble("I couldn't process that message properly.");
                    });
                }
            } catch (Throwable t) {
                Log.e("AIChat", "Error classifying message", t);
                mainHandler.post(() -> {
                    ascundeTypingIndicator();
                    addAiBubble("Voiaj AI encountered a temporary issue processing your request.");
                    android.widget.Toast.makeText(AIChatActivity.this, "AI Info: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void ruteazaDupaIntentie(String intentie, String textMesaj) {
        String t = textMesaj.toLowerCase();

        // Fallback inteligent: Dacă a clasificat ca recomandare, dar userul a zis deja un oraș,
        // înseamnă că vrea detalii/comparații despre acel oraș (deci Ask Anything)
        if ("recommend_destination".equals(intentie) && (t.contains("rome") || t.contains("barcelona") || t.contains("restaurant"))) {
            intentie = "ask_anything";
        }

        switch (intentie) {
            case "plan_itinerary":
                intrebaCumVreaSaPlanuiasca();
                break;
            case "weather_season":
                raspundeCuVremea(textMesaj);
                break;
            case "budget_estimate":
                raspundeCuBugetul(textMesaj);
                break;
            case "recommend_destination":
                raspundeCuRecomandare(textMesaj);
                break;
            case "ask_anything":
                raspundeCuAskAnything(textMesaj);
                break;
            default:
                addAiBubble("That's a great question - I'll be able to answer this in more detail soon.");
                break;
        }
    }

    /** Ia toate calatoriile salvate ale userului. */
    private void getCalatorieRecenta(TripRepository.TripListCallback callback) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) {
            callback.onError("Not logged in");
            return;
        }

        tripRepository.getSavedTrips(uid, callback);
    }

    /** Cauta in text daca userul a mentionat explicit numele unui oras cunoscut din baza de date. */
    private void gasesteOrasInText(String text, OrasGasitCallback callback) {
        cityRepository.getAllCities(new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                String textLowerCase = text.toLowerCase();
                for (City city : cities) {
                    if (city.getName() != null && textLowerCase.contains(city.getName().toLowerCase())) {
                        callback.gasit(city);
                        return;
                    }
                }
                callback.negasit();
            }

            @Override
            public void onError(String errorMessage) {
                callback.negasit();
            }
        });
    }

    private interface OrasGasitCallback {
        void gasit(City oras);
        void negasit();
    }

    /** Ia toate calatoriile salvate, apoi alege cea mai apropiata calatorie VIITOARE (status "planned", neterminata). */
    private void getCalatorieCeaMaiApropiata(TripListSauNullCallback callback) {
        getCalatorieRecenta(new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> trips) {
                Trip ceaMaiApropiata = null;
                for (Trip trip : trips) {
                    if (!"planned".equals(trip.getStatus()) || trip.hasEnded()) continue;
                    if (ceaMaiApropiata == null || trip.getDaysUntilTrip() < ceaMaiApropiata.getDaysUntilTrip()) {
                        ceaMaiApropiata = trip;
                    }
                }
                callback.rezultat(ceaMaiApropiata);
            }

            @Override
            public void onError(String errorMessage) {
                callback.rezultat(null);
            }
        });
    }

    private interface TripListSauNullCallback {
        void rezultat(Trip trip);
    }

    /** Verifica daca mesajul pare sa intrebe despre TOATE calatoriile viitoare, nu una singura. */
    private boolean intrebaDeToateCalatoriile(String text) {
        String t = text.toLowerCase();
        return t.contains("trips") || t.contains("all my") || t.contains("each of my")
                || t.contains("every trip") || t.contains("upcoming") || t.contains("future");
    }

    private void raspundeCuVremea(String textMesaj) {
        gasesteOrasInText(textMesaj, new OrasGasitCallback() {
            @Override
            public void gasit(City oras) {
                if (oras.getLat() == null || oras.getLon() == null) {
                    addAiBubble("I don't have location data for " + oras.getName() + " yet.");
                    return;
                }
                WeatherHelper.fetchWeatherSmart(oras.getLat(), oras.getLon(), 0, new WeatherHelper.SmartWeatherCallback() {
                    @Override
                    public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil, boolean approximativ) {
                        String mesaj = "Right now in " + oras.getName() + ": " + temperatura + "°C, " + conditieCuEmoji;
                        if (plouaProbabil) mesaj += "\n☔ It might rain - maybe plan indoor activities like museums.";
                        addAiBubble(mesaj);
                    }

                    @Override
                    public void onError(String mesajEroare) {
                        addAiBubble("Sorry, I couldn't fetch the weather for " + oras.getName() + " right now.");
                    }
                });
            }

            @Override
            public void negasit() {
                if (intrebaDeToateCalatoriile(textMesaj)) {
                    raspundeCuVremeaTuturorCalatoriilor();
                } else {
                    raspundeCuVremeaCeleiMaiApropiateCalatorii();
                }
            }
        });
    }

    private void raspundeCuVremeaTuturorCalatoriilor() {
        getCalatorieRecenta(new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> trips) {
                List<Trip> viitoare = new java.util.ArrayList<>();
                for (Trip trip : trips) {
                    if ("planned".equals(trip.getStatus()) && !trip.hasEnded()) viitoare.add(trip);
                }

                if (viitoare.isEmpty()) {
                    addAiBubble("You don't have any upcoming trips planned yet.");
                    return;
                }

                viitoare.sort((a, b) -> Integer.compare(a.getDaysUntilTrip(), b.getDaysUntilTrip()));
                calculeazaVremePeRand(viitoare, 0, new java.util.ArrayList<>());
            }

            @Override
            public void onError(String errorMessage) {
                addAiBubble("Sorry, something went wrong checking your trips.");
            }
        });
    }

    private void calculeazaVremePeRand(List<Trip> trips, int index, List<String> randuriAcumulate) {
        if (index >= trips.size()) {
            addAiBubble("Here's the weather for your upcoming trips:\n" + String.join("\n", randuriAcumulate));
            return;
        }

        Trip trip = trips.get(index);
        db.collection("countries").document(trip.getCountryId())
                .collection("cities").document(trip.getCityId())
                .get()
                .addOnSuccessListener(doc -> {
                    City city = doc.toObject(City.class);
                    if (city == null || city.getLat() == null || city.getLon() == null) {
                        randuriAcumulate.add("• " + trip.getCityName() + ": location data not available");
                        calculeazaVremePeRand(trips, index + 1, randuriAcumulate);
                        return;
                    }

                    WeatherHelper.fetchWeatherSmart(city.getLat(), city.getLon(), trip.getDaysUntilTrip(),
                            new WeatherHelper.SmartWeatherCallback() {
                                @Override
                                public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil, boolean approximativ) {
                                    String nota = approximativ ? " (current, too far out for exact forecast)" : "";
                                    randuriAcumulate.add("• " + trip.getCityName() + " (in " + trip.getDaysUntilTrip() + " days): "
                                            + temperatura + "°C, " + conditieCuEmoji + nota);
                                    calculeazaVremePeRand(trips, index + 1, randuriAcumulate);
                                }

                                @Override
                                public void onError(String mesajEroare) {
                                    randuriAcumulate.add("• " + trip.getCityName() + ": couldn't load weather");
                                    calculeazaVremePeRand(trips, index + 1, randuriAcumulate);
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    randuriAcumulate.add("• " + trip.getCityName() + ": couldn't load weather");
                    calculeazaVremePeRand(trips, index + 1, randuriAcumulate);
                });
    }

    private void raspundeCuVremeaCeleiMaiApropiateCalatorii() {
        getCalatorieCeaMaiApropiata(trip -> {
            if (trip == null) {
                addAiBubble("You can ask about a specific city (e.g. \"weather in Rome\"), or plan a trip first so I can use that as reference.");
                return;
            }

            db.collection("countries").document(trip.getCountryId())
                    .collection("cities").document(trip.getCityId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        City city = doc.toObject(City.class);
                        if (city == null || city.getLat() == null || city.getLon() == null) {
                            addAiBubble("I couldn't find location data for " + trip.getCityName() + ".");
                            return;
                        }

                        WeatherHelper.fetchWeatherSmart(city.getLat(), city.getLon(), trip.getDaysUntilTrip(),
                                new WeatherHelper.SmartWeatherCallback() {
                                    @Override
                                    public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil, boolean approximativ) {
                                        String prefix = "You didn't mention a city, so here's the weather for your closest upcoming trip to "
                                                + trip.getCityName() + " (in " + trip.getDaysUntilTrip() + " days): ";
                                        String mesaj = prefix + temperatura + "°C, " + conditieCuEmoji;
                                        if (approximativ) {
                                            mesaj += "\n(That's too far out for an exact forecast - this is today's weather as a general reference.)";
                                        }
                                        if (plouaProbabil) {
                                            mesaj += "\n☔ It might rain - maybe plan indoor activities like museums for that day.";
                                        }
                                        addAiBubble(mesaj);
                                    }

                                    @Override
                                    public void onError(String mesajEroare) {
                                        addAiBubble("Sorry, I couldn't fetch the weather right now.");
                                    }
                                });
                    })
                    .addOnFailureListener(e -> addAiBubble("Sorry, something went wrong checking the weather."));
        });
    }

    private void gasesteOraseInText(String text, DouaOraseCallback callback) {
        cityRepository.getAllCities(new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                String textLowerCase = text.toLowerCase();
                List<City> gasite = new java.util.ArrayList<>();
                for (City city : cities) {
                    if (city.getName() != null && textLowerCase.contains(city.getName().toLowerCase())) {
                        gasite.add(city);
                        if (gasite.size() == 2) break;
                    }
                }
                callback.rezultat(gasite);
            }

            @Override
            public void onError(String errorMessage) {
                callback.rezultat(new java.util.ArrayList<>());
            }
        });
    }

    private interface DouaOraseCallback {
        void rezultat(List<City> orase);
    }

    private String notaMoneda(City oras) {
        if (oras.getCurrency() == null || oras.getCurrency().startsWith("EUR")) return "";
        return " (local currency: " + oras.getCurrency() + ")";
    }

    private void raspundeCuBugetul(String textMesaj) {
        gasesteOraseInText(textMesaj, orase -> {
            if (orase.size() == 2) {
                City oras1 = orase.get(0);
                City oras2 = orase.get(1);
                if (oras1.getAvgDayBudget() == null || oras2.getAvgDayBudget() == null) {
                    addAiBubble("I don't have complete budget data to compare " + oras1.getName() + " and " + oras2.getName() + ".");
                    return;
                }
                City maiIeftin = oras1.getAvgDayBudget() <= oras2.getAvgDayBudget() ? oras1 : oras2;
                City maiScump = maiIeftin == oras1 ? oras2 : oras1;
                double diferenta = maiScump.getAvgDayBudget() - maiIeftin.getAvgDayBudget();

                addAiBubble(maiIeftin.getName() + " (€" + Math.round(maiIeftin.getAvgDayBudget()) + "/day"
                        + notaMoneda(maiIeftin) + ") is cheaper than " + maiScump.getName() + " (€"
                        + Math.round(maiScump.getAvgDayBudget()) + "/day" + notaMoneda(maiScump)
                        + ") by about €" + Math.round(diferenta) + "/day.");
                return;
            }

            if (orase.size() == 1) {
                City oras = orase.get(0);
                if (oras.getAvgDayBudget() == null) {
                    addAiBubble("I don't have budget data for " + oras.getName() + " yet.");
                    return;
                }
                addAiBubble("In " + oras.getName() + ", expect around €" + Math.round(oras.getAvgDayBudget())
                        + " per day on average" + notaMoneda(oras) + ".");
                return;
            }

            if (intrebaDeToateCalatoriile(textMesaj)) {
                raspundeCuBugetulTuturorCalatoriilor();
            } else {
                raspundeCuBugetulCeleiMaiApropiateCalatorii();
            }
        });
    }

    private void raspundeCuBugetulTuturorCalatoriilor() {
        getCalatorieRecenta(new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> trips) {
                List<Trip> viitoare = new java.util.ArrayList<>();
                for (Trip trip : trips) {
                    if ("planned".equals(trip.getStatus()) && !trip.hasEnded()) viitoare.add(trip);
                }

                if (viitoare.isEmpty()) {
                    addAiBubble("You don't have any upcoming trips planned yet.");
                    return;
                }

                viitoare.sort((a, b) -> Integer.compare(a.getDaysUntilTrip(), b.getDaysUntilTrip()));
                calculeazaBugetPeRand(viitoare, 0, new java.util.ArrayList<>());
            }

            @Override
            public void onError(String errorMessage) {
                addAiBubble("Sorry, something went wrong checking your trips.");
            }
        });
    }

    private void calculeazaBugetPeRand(List<Trip> trips, int index, List<String> randuriAcumulate) {
        if (index >= trips.size()) {
            addAiBubble("Here's the budget for your upcoming trips:\n" + String.join("\n", randuriAcumulate));
            return;
        }

        Trip trip = trips.get(index);
        db.collection("countries").document(trip.getCountryId())
                .collection("cities").document(trip.getCityId())
                .get()
                .addOnSuccessListener(doc -> {
                    City city = doc.toObject(City.class);
                    if (city != null && city.getAvgDayBudget() != null) {
                        double buget = city.getAvgDayBudget() * trip.getDaysCount();
                        randuriAcumulate.add("• " + trip.getCityName() + " (" + trip.getDaysCount() + " days, in "
                                + trip.getDaysUntilTrip() + " days): ~€" + Math.round(buget));
                    } else {
                        randuriAcumulate.add("• " + trip.getCityName() + ": budget data not available");
                    }
                    calculeazaBugetPeRand(trips, index + 1, randuriAcumulate);
                })
                .addOnFailureListener(e -> {
                    randuriAcumulate.add("• " + trip.getCityName() + ": couldn't load budget");
                    calculeazaBugetPeRand(trips, index + 1, randuriAcumulate);
                });
    }

    private void raspundeCuBugetulCeleiMaiApropiateCalatorii() {
        getCalatorieCeaMaiApropiata(trip -> {
            if (trip == null) {
                addAiBubble("You can ask about a specific city (e.g. \"budget for Rome\"), or plan a trip first so I can use that as reference.");
                return;
            }

            db.collection("countries").document(trip.getCountryId())
                    .collection("cities").document(trip.getCityId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        City city = doc.toObject(City.class);
                        if (city == null || city.getAvgDayBudget() == null) {
                            addAiBubble("I don't have budget data for " + trip.getCityName() + " yet.");
                            return;
                        }

                        double bugetEstimat = city.getAvgDayBudget() * trip.getDaysCount();
                        addAiBubble("You didn't mention a city, so here's the estimate for your closest upcoming trip - "
                                + trip.getDaysCount() + " days in " + trip.getCityName() + " (in " + trip.getDaysUntilTrip()
                                + " days): around €" + Math.round(bugetEstimat) + " total (€" + Math.round(city.getAvgDayBudget())
                                + "/day average)" + notaMoneda(city) + ".");
                    })
                    .addOnFailureListener(e -> addAiBubble("Sorry, something went wrong estimating the budget."));
        });
    }

    /**
     * Cauta cel mai potrivit oras pentru mesajul userului, folosind cautare semantica
     * (acelasi model, dar comparand vectori de sens, nu doar clasificand intentia).
     * Tine cont si de interesele alese de user la inregistrare, ca sa fie personalizat.
     */
    private void raspundeCuRecomandare(String textMesaj) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) {
            gasesteOrasulPotrivit(textMesaj, null);
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    List<String> interese = (List<String>) doc.get("interests");
                    gasesteOrasulPotrivit(textMesaj, interese);
                })
                .addOnFailureListener(e -> gasesteOrasulPotrivit(textMesaj, null));
    }

    /** Verifica daca userul cere explicit o recomandare bazata pe interesele lui, fara sa descrie nimic concret. */
    private boolean cereRecomandareDupaInterese(String text) {
        String t = text.toLowerCase();
        return t.contains("my interest") || t.contains("based on interest") || t.contains("chosen interest")
                || t.contains("selected interest");
    }

    private void gasesteOrasulPotrivit(String textMesaj, List<String> intereseUser) {
        boolean doarDupaInterese = cereRecomandareDupaInterese(textMesaj);

        if (doarDupaInterese && (intereseUser == null || intereseUser.isEmpty())) {
            addAiBubble("You don't have any interests set yet - add some in your profile, or just describe what you're looking for!");
            return;
        }

        cityRepository.getAllCities(new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                executor.execute(() -> {
                    try {
                        double[] queryEmbedding = doarDupaInterese ? null : embeddingModel.getEmbedding(textMesaj);
                        List<OrasCuScor> orasesCuScoruri = new java.util.ArrayList<>();

                        for (City oras : cities) {
                            double scorFinal;

                            if (doarDupaInterese) {
                                scorFinal = calculeazaBonusInterese(oras, intereseUser);
                            } else {
                                String profil = construiesteProfilOras(oras);
                                if (profil.isEmpty()) continue;

                                double[] orasEmbedding = embeddingModel.getEmbedding(profil);
                                double scorSemantic = produsScalar(queryEmbedding, orasEmbedding);

                                // ── FILTRU DE SIGURANȚĂ (THRESHOLD) ──
                                // Dacă scorul semantic este mai mic de 0.25, destinația este complet pe lângă subiect.
                                // Nu îi mai permitem să urce artificial în top doar din bonusul de interese.
                                if (scorSemantic < 0.25) {
                                    scorFinal = scorSemantic; // Păstrăm scorul pur, mic, fără bonus
                                } else {
                                    scorFinal = scorSemantic + calculeazaBonusInterese(oras, intereseUser);
                                }
                            }

                            orasesCuScoruri.add(new OrasCuScor(oras, scorFinal));
                        }

                        orasesCuScoruri.sort((a, b) -> Double.compare(b.scor, a.scor));

                        int limita = Math.min(3, orasesCuScoruri.size());
                        List<OrasCuScor> topRecomandari = orasesCuScoruri.subList(0, limita);

                        mainHandler.post(() -> {
                            if (topRecomandari.isEmpty()) {
                                addAiBubble("I couldn't find a good match yet - try describing what you're looking for!");
                                return;
                            }

                            ultimeleRecomandari = topRecomandari;
                            indexRecomandareCurenta = 0;

                            String mesajIntro = doarDupaInterese
                                    ? "Based on your interests (" + String.join(", ", intereseUser) + "), I think you'd love this:"
                                    : (intereseUser != null && !intereseUser.isEmpty()
                                       ? "Based on what you wrote and your interests (" + String.join(", ", intereseUser) + "), I think you'd love this:"
                                       : "Based on what you wrote, I think you'd love this:");
                            addAiBubble(mesajIntro);
                            addDestinationCard(ultimeleRecomandari.get(0).oras);
                            arataOptiuneaDeAlteVariante();
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> addAiBubble("Sorry, something went wrong finding a recommendation."));
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                addAiBubble("Sorry, something went wrong loading destinations.");
            }
        });
    }

    /**
     * Bonus mic (0 - 0.15) daca interesele userului se suprapun cu keywords-urile orasului -
     * influenteaza usor rezultatul, dar NU domina scorul semantic principal (bazat pe mesaj).
     */
    private double calculeazaBonusInterese(City oras, List<String> intereseUser) {
        if (intereseUser == null || intereseUser.isEmpty() || oras.getKeywords() == null || oras.getKeywords().isEmpty()) {
            return 0;
        }

        int potriviri = 0;
        for (String interes : intereseUser) {
            String interesLower = interes.toLowerCase().trim();
            for (String keyword : oras.getKeywords()) {
                if (keyword == null) continue;
                String keywordNormalizat = normalizeazaKeyword(keyword).toLowerCase();
                // Potrivire permisiva - "beaches" se potriveste cu "sun and beach" (normalizat),
                // nu doar potrivire exacta (keywords si interesele nu folosesc mereu exact acelasi cuvant).
                if (keywordNormalizat.contains(interesLower) || interesLower.contains(keywordNormalizat)) {
                    potriviri++;
                    break;
                }
            }
        }

        double procentPotrivire = (double) potriviri / intereseUser.size();
        return procentPotrivire * 0.15;
    }

    /** Textul folosit pentru comparatia semantica - DOAR nume + keywords, fara tagline/descriere.
     *  Descrierile sunt lungi si scrise similar (stil de ghid turistic) la toate orasele, iar media
     *  vectorilor (mean pooling) pe un text lung dilueaza complet semnalul util din keywords.
     *  Un text scurt, concentrat, lasa keywords sa conteze cu adevarat. */
    private String construiesteProfilOras(City oras) {
        StringBuilder sb = new StringBuilder();
        if (oras.getName() != null) sb.append(oras.getName()).append(": ");
        if (oras.getSemanticTags() != null && !oras.getSemanticTags().isEmpty()) {
            sb.append(String.join(", ", oras.getSemanticTags())).append(". ");
        }
        if (oras.getKeywords() != null && !oras.getKeywords().isEmpty()) {
            List<String> keywordsNormalizate = new java.util.ArrayList<>();
            for (String keyword : oras.getKeywords()) {
                keywordsNormalizate.add(normalizeazaKeyword(keyword));
            }
            sb.append(String.join(", ", keywordsNormalizate));
        }
        return sb.toString().trim();
    }

    /** Transforma "sun&beach" in "sun and beach" - cuvinte normale, pe care modelul le intelege corect. */
    private String normalizeazaKeyword(String keyword) {
        if (keyword == null) return "";
        return keyword.replace("&", " and ").trim();
    }

    /** Produsul scalar a doi vectori normalizati = similaritatea cosinus intre ei. */
    private double produsScalar(double[] a, double[] b) {
        double suma = 0;
        for (int i = 0; i < a.length; i++) suma += a[i] * b[i];
        return suma;
    }

    /** Adauga un card vizual cu orasul recomandat - poza, nume, tagline, buton "View city". */
    private void addDestinationCard(City oras) {
        android.view.View card = android.view.LayoutInflater.from(this)
                .inflate(R.layout.item_ai_destination_card, messagesContainer, false);

        android.widget.ImageView img = card.findViewById(R.id.imgDestinationCard);
        TextView name = card.findViewById(R.id.txtDestinationName);
        TextView tagline = card.findViewById(R.id.txtDestinationTagline);
        com.google.android.material.button.MaterialButton btnView = card.findViewById(R.id.btnViewDestination);

        name.setText(oras.getName());
        tagline.setText(oras.getTagline() != null ? oras.getTagline() : "");

        int resId = 0;
        if (oras.getId() != null) resId = getResources().getIdentifier(oras.getId(), "drawable", getPackageName());
        img.setImageResource(resId != 0 ? resId : R.drawable.rome);

        btnView.setOnClickListener(v -> {
            Intent intent = new Intent(AIChatActivity.this, CityDetailActivity.class);
            intent.putExtra("cityId", oras.getId());
            intent.putExtra("countryId", oras.getCountryId());
            startActivity(intent);
        });

        messagesContainer.addView(card);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    /** Arata o optiune mica, apasabila, "Show me another option" - doar daca mai exista variante. */
    private void arataOptiuneaDeAlteVariante() {
        if (indexRecomandareCurenta + 1 >= ultimeleRecomandari.size()) return; // nu mai sunt altele
        addQuickOption("🔄 Show me another option", "__ALTA_VARIANTA__");
    }

    /** Afiseaza urmatorul oras din ultima cautare (nu recalculeaza nimic). */
    private void arataUrmatoareaVarianta() {
        indexRecomandareCurenta++;
        if (indexRecomandareCurenta >= ultimeleRecomandari.size()) {
            addAiBubble("That's all the good matches I found for that!");
            return;
        }
        addAiBubble("Here's another option:");
        addDestinationCard(ultimeleRecomandari.get(indexRecomandareCurenta).oras);
        arataOptiuneaDeAlteVariante();
    }

    /** Primul pas cand userul vrea sa planuiasca un itinerariu - alege intre descriere libera sau un draft existent. */
    private void intrebaCumVreaSaPlanuiasca() {
        addAiBubble("Great! How would you like to plan this?");
        addPlanChoiceCard("✍️", "Describe it myself", "e.g. \"4 days in Rome in August\"", "__DESCRIE_LIBER__");
        addPlanChoiceCard("📋", "Choose from my drafts", "Pick a trip you've already saved", "__ALEGE_DIN_DRAFTS__");
    }

    /** Card frumos, in stilul cardurilor din AI Hub - icon, titlu bold, subtitlu gri, fundal alb rotunjit. */
    private void addPlanChoiceCard(String emoji, String titlu, String subtitlu, String promptReal) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setRadius(16 * getResources().getDisplayMetrics().density);
        card.setCardElevation(2 * getResources().getDisplayMetrics().density);
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                (int) (260 * getResources().getDisplayMetrics().density), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.START;
        int marginPx = (int) (10 * getResources().getDisplayMetrics().density);
        cardParams.bottomMargin = marginPx;
        card.setLayoutParams(cardParams);

        LinearLayout continut = new LinearLayout(this);
        continut.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (14 * getResources().getDisplayMetrics().density);
        continut.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        TextView txtEmoji = new TextView(this);
        txtEmoji.setText(emoji);
        txtEmoji.setTextSize(22f);

        TextView txtTitlu = new TextView(this);
        txtTitlu.setText(titlu);
        txtTitlu.setTextColor(Color.parseColor("#011C40")); // navy_blue_1
        txtTitlu.setTextSize(15f);
        txtTitlu.setTypeface(txtTitlu.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titluParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titluParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        txtTitlu.setLayoutParams(titluParams);

        TextView txtSubtitlu = new TextView(this);
        txtSubtitlu.setText(subtitlu);
        txtSubtitlu.setTextColor(Color.parseColor("#999999"));
        txtSubtitlu.setTextSize(12f);
        LinearLayout.LayoutParams subtitluParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitluParams.topMargin = (int) (2 * getResources().getDisplayMetrics().density);
        txtSubtitlu.setLayoutParams(subtitluParams);

        continut.addView(txtEmoji);
        continut.addView(txtTitlu);
        continut.addView(txtSubtitlu);
        card.addView(continut);

        card.setOnClickListener(v -> gestioneazaOptiuneSpeciala(promptReal));

        messagesContainer.addView(card);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    /** Cand userul apasa "I'll describe it" - il pregatim sa primim urmatorul mesaj ca descriere. */
    private void pregatesteDescriereLibera() {
        addAiBubble("Great — tell me where and when you'd like to go!");
        asteptaDescriereItinerar = true;
    }

    /** Cand userul apasa "Choose from my drafts" - ii aratam draft-urile lui reale, apasabile. */
    private void arataListaDrafturi() {
        getCalatorieRecenta(new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> trips) {
                List<Trip> drafturi = new java.util.ArrayList<>();
                for (Trip trip : trips) {
                    if ("draft".equals(trip.getStatus())) drafturi.add(trip);
                }

                if (drafturi.isEmpty()) {
                    addAiBubble("You don't have any drafts saved yet - tell me where and when you'd like to go instead!");
                    asteptaDescriereItinerar = true;
                    return;
                }

                addAiBubble("Here are your saved drafts - pick one:");
                for (Trip draft : drafturi) {
                    cacheDrafturi.put(draft.getTripId(), draft);
                    addDraftCard(draft);
                }
            }

            @Override
            public void onError(String errorMessage) {
                addAiBubble("Sorry, something went wrong loading your drafts.");
            }
        });
    }

    /**
     * Userul a scris liber ce vrea (ex: "4 days in Rome in August") - cautam orasul mentionat
     * si deschidem PlanTripActivity, FORTAT pe Exact Dates (un itinerariu generat de AI
     * se poate salva doar ca "planned", niciodata ca draft).
     */
    private void proceseazaDescriereItinerar(String text) {
        Long lunaDetectata = gasesteLunaInText(text);

        gasesteOrasInText(text, new OrasGasitCallback() {
            @Override
            public void gasit(City oras) {
                addAiBubble("Perfect! Let's set the exact dates for your trip to " + oras.getName() + ".");
                deschidePlanTripExactDates(oras.getId(), oras.getName(), oras.getCountryId(), null, lunaDetectata);
            }

            @Override
            public void negasit() {
                addAiBubble("I couldn't quite catch which city you meant - let's pick it together in the planner.");
                deschidePlanTripExactDates(null, null, null, null, lunaDetectata);
            }
        });
    }

    /** Cauta numele unei luni in text (ex: "August") si intoarce data de 1 a acelei luni,
     *  in anul curent daca luna nu a trecut inca, altfel anul urmator. Null daca nu gaseste nimic. */
    private Long gasesteLunaInText(String text) {
        String[] luni = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"};
        String textLower = text.toLowerCase();

        for (int i = 0; i < luni.length; i++) {
            if (textLower.contains(luni[i])) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int anCurent = cal.get(java.util.Calendar.YEAR);
                int lunaCurenta = cal.get(java.util.Calendar.MONTH);

                cal.set(java.util.Calendar.MONTH, i);
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);

                if (i < lunaCurenta) {
                    cal.set(java.util.Calendar.YEAR, anCurent + 1); // luna a trecut deja - presupunem anul urmator
                }

                return cal.getTimeInMillis();
            }
        }
        return null;
    }

    /** Deschide un draft existent - la fel, fortat pe Exact Dates, ca sa devina "planned" dupa generare. */
    private void deschideDraftInPlanTrip(String tripId) {
        Trip draft = cacheDrafturi.get(tripId);
        if (draft == null) {
            addAiBubble("Sorry, I couldn't find that draft anymore.");
            return;
        }
        addAiBubble("Let's set the exact dates for this trip.");

        Intent intent = new Intent(AIChatActivity.this, PlanTripActivity.class);
        intent.putExtra("forceExactDates", true);
        intent.putExtra("editingTripId", tripId);
        intent.putExtra("cityId", draft.getCityId());
        intent.putExtra("cityName", draft.getCityName());
        intent.putExtra("countryId", draft.getCountryId());
        intent.putExtra("existingDaysCount", draft.getDaysCount());
        startActivity(intent);
    }

    private void deschidePlanTripExactDates(String cityId, String cityName, String countryId, String editingTripId, Long targetDateMillis) {
        Intent intent = new Intent(AIChatActivity.this, PlanTripActivity.class);
        intent.putExtra("forceExactDates", true);
        if (cityId != null) intent.putExtra("cityId", cityId);
        if (cityName != null) intent.putExtra("cityName", cityName);
        if (countryId != null) intent.putExtra("countryId", countryId);
        if (editingTripId != null) intent.putExtra("editingTripId", editingTripId);
        if (targetDateMillis != null) intent.putExtra("targetDateMillis", (long) targetDateMillis);
        startActivity(intent);
    }

    /** Trateaza toate optiunile speciale (nu-s mesaje reale de la user), fie ca vin dintr-o bula mica sau un card. */
    private void gestioneazaOptiuneSpeciala(String promptReal) {
        arataTypingIndicator();
        if ("__ALTA_VARIANTA__".equals(promptReal)) {
            arataUrmatoareaVarianta();
        } else if ("__DESCRIE_LIBER__".equals(promptReal)) {
            pregatesteDescriereLibera();
        } else if ("__ALEGE_DIN_DRAFTS__".equals(promptReal)) {
            arataListaDrafturi();
        } else if (promptReal.startsWith("__DRAFT__")) {
            String tripId = promptReal.substring("__DRAFT__".length());
            deschideDraftInPlanTrip(tripId);
        } else {
            addUserBubble(promptReal);
            proceseazaMesaj(promptReal);
        }
    }

    /** Card frumos pentru un draft salvat - fasie albastra sus cu numele orasului, jos zilele + sageata. */
    private void addDraftCard(Trip draft) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        float density = getResources().getDisplayMetrics().density;
        card.setRadius(16 * density);
        card.setCardElevation(2 * density);
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                (int) (260 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.START;
        cardParams.bottomMargin = (int) (10 * density);
        card.setLayoutParams(cardParams);

        LinearLayout continut = new LinearLayout(this);
        continut.setOrientation(LinearLayout.VERTICAL);

        // Fasia de sus, albastra - numele orasului
        LinearLayout fasieAlbastra = new LinearLayout(this);
        fasieAlbastra.setOrientation(LinearLayout.HORIZONTAL);
        fasieAlbastra.setGravity(Gravity.CENTER_VERTICAL);
        fasieAlbastra.setBackgroundColor(Color.parseColor("#7BBDE8")); // sky_blue_1
        int paddingFasie = (int) (14 * density);
        fasieAlbastra.setPadding(paddingFasie, paddingFasie, paddingFasie, paddingFasie);

        TextView txtEmoji = new TextView(this);
        txtEmoji.setText("📍");
        txtEmoji.setTextSize(16f);

        TextView txtOras = new TextView(this);
        txtOras.setText(draft.getCityName());
        txtOras.setTextColor(Color.WHITE);
        txtOras.setTextSize(15f);
        txtOras.setTypeface(txtOras.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams orasParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        orasParams.leftMargin = (int) (8 * density);
        txtOras.setLayoutParams(orasParams);

        fasieAlbastra.addView(txtEmoji);
        fasieAlbastra.addView(txtOras);

        // Partea de jos, alba - zilele + sageata
        LinearLayout partiaJos = new LinearLayout(this);
        partiaJos.setOrientation(LinearLayout.HORIZONTAL);
        partiaJos.setGravity(Gravity.CENTER_VERTICAL);
        int paddingJos = (int) (14 * density);
        partiaJos.setPadding(paddingJos, paddingJos, paddingJos, paddingJos);

        TextView txtZile = new TextView(this);
        txtZile.setText(draft.getDaysCount() > 0 ? draft.getDaysCount() + " days · draft" : "Draft");
        txtZile.setTextColor(Color.parseColor("#999999"));
        txtZile.setTextSize(13f);
        LinearLayout.LayoutParams zileParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtZile.setLayoutParams(zileParams);

        TextView txtSageata = new TextView(this);
        txtSageata.setText("→");
        txtSageata.setTextColor(Color.parseColor("#7BBDE8"));
        txtSageata.setTextSize(16f);
        txtSageata.setTypeface(txtSageata.getTypeface(), android.graphics.Typeface.BOLD);

        partiaJos.addView(txtZile);
        partiaJos.addView(txtSageata);

        continut.addView(fasieAlbastra);
        continut.addView(partiaJos);
        card.addView(continut);

        card.setOnClickListener(v -> gestioneazaOptiuneSpeciala("__DRAFT__" + draft.getTripId()));

        messagesContainer.addView(card);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    /** Incarca trip-ul generat + toate atractiile/restaurantele orasului, apoi construieste cardul. */
    /** Construieste trip-ul in memorie (INCA NESALVAT) din extra-urile de preview, si arata cardul. */
    @SuppressWarnings("unchecked")
    private void aratatItinerariulPreview() {
        String cityId = getIntent().getStringExtra("previewCityId");
        String cityName = getIntent().getStringExtra("previewCityName");
        String countryId = getIntent().getStringExtra("previewCountryId");
        String startDate = getIntent().getStringExtra("previewStartDate");
        String endDate = getIntent().getStringExtra("previewEndDate");
        int daysCount = getIntent().getIntExtra("previewDaysCount", 1);
        String editingTripId = getIntent().getStringExtra("previewEditingTripId");
        java.io.Serializable itinerarSerializat = getIntent().getSerializableExtra("previewItinerary");

        List<ItineraryItem> itinerariu = itinerarSerializat instanceof List
                ? (List<ItineraryItem>) itinerarSerializat : new ArrayList<>();

        Trip trip = Trip.createSaved(cityId, cityName, countryId, startDate, endDate, daysCount, "planned");
        trip.setItinerary(itinerariu);
        if (editingTripId != null) trip.setTripId(editingTripId); // pastram id-ul, ca sa actualizam draftul, nu sa cream unul nou

        PoiRepository poiRepository = new PoiRepository();
        poiRepository.getAttractions(countryId, cityId, new PoiRepository.AttractionListCallback() {
            @Override
            public void onSuccess(List<Attraction> attractions) {
                Map<String, Attraction> hartaAtractii = new HashMap<>();
                for (Attraction a : attractions) hartaAtractii.put(a.getId(), a);

                poiRepository.getRestaurants(countryId, cityId, new PoiRepository.RestaurantListCallback() {
                    @Override
                    public void onSuccess(List<Restaurant> restaurants) {
                        Map<String, Restaurant> hartaRestaurante = new HashMap<>();
                        for (Restaurant r : restaurants) hartaRestaurante.put(r.getId(), r);

                        tripCurent = trip;
                        hartaAtractiiCurenta = hartaAtractii;
                        hartaRestauranteCurenta = hartaRestaurante;

                        addAiBubble("Here's your itinerary for " + trip.getCityName() + "! Tap Save if you'd like to keep it. You can also ask me to add, remove, or swap things.");
                        addItineraryCard(trip, hartaAtractii, hartaRestaurante, false); // preview - INCA nesalvat, chiar daca vine dintr-un draft existent
                        adaugaCardulDeVreme(trip);
                        verificaVremePeZileSiSugereazaAlternative(trip, hartaAtractii);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        addAiBubble("Loaded the itinerary, but couldn't load restaurant details.");
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                addAiBubble("Couldn't load attraction details.");
            }
        });
    }

    /** Cardul principal - antet colorat, statistici, cate un rand per zi, "View all" daca sunt multe zile. */
    private void addItineraryCard(Trip trip, Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante, boolean esteDejaSalvat) {
        float density = getResources().getDisplayMetrics().density;

        // Grupam itinerariul pe zile
        Map<Integer, List<ItineraryItem>> peZile = new java.util.TreeMap<>();
        if (trip.getItinerary() != null) {
            for (ItineraryItem item : trip.getItinerary()) {
                peZile.computeIfAbsent(item.getDay(), k -> new ArrayList<>()).add(item);
            }
        }

        // Buget: suma ticketPrice-urilor atractiilor + o estimare pentru restaurante dupa priceRange
        double bugetTotal = 0;
        int nrSpoturi = 0;
        for (List<ItineraryItem> itemsZi : peZile.values()) {
            for (ItineraryItem item : itemsZi) {
                nrSpoturi++;
                if ("attraction".equals(item.getType())) {
                    Attraction a = hartaAtractii.get(item.getPoiId());
                    if (a != null) bugetTotal += a.getTicketPrice();
                } else {
                    Restaurant r = hartaRestaurante.get(item.getPoiId());
                    if (r != null) bugetTotal += estimeazaPretRestaurant(r.getPriceRange());
                }
            }
        }

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setRadius(16 * density);
        card.setCardElevation(2 * density);
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int) (14 * density);
        card.setLayoutParams(cardParams);

        LinearLayout continut = new LinearLayout(this);
        continut.setOrientation(LinearLayout.VERTICAL);

        // Antet colorat
        LinearLayout antet = new LinearLayout(this);
        antet.setOrientation(LinearLayout.HORIZONTAL);
        antet.setGravity(Gravity.CENTER_VERTICAL);
        antet.setBackgroundColor(Color.parseColor("#54ACBF")); // light_turq_1
        int paddingAntet = (int) (14 * density);
        antet.setPadding(paddingAntet, paddingAntet, paddingAntet, paddingAntet);

        LinearLayout coloanaTitlu = new LinearLayout(this);
        coloanaTitlu.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams coloanaParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        coloanaTitlu.setLayoutParams(coloanaParams);

        TextView txtTitlu = new TextView(this);
        txtTitlu.setText(trip.getCityName() + " — " + trip.getDaysCount() + " days");
        txtTitlu.setTextColor(Color.WHITE);
        txtTitlu.setTextSize(15f);
        txtTitlu.setTypeface(txtTitlu.getTypeface(), android.graphics.Typeface.BOLD);
        coloanaTitlu.addView(txtTitlu);

        com.google.android.material.button.MaterialButton btnSave = new com.google.android.material.button.MaterialButton(this);
        btnSave.setText("Save");
        btnSave.setTextSize(12f);
        btnSave.setAllCaps(false);
        btnSave.setTextColor(Color.parseColor("#011C40"));
        btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EAD098"))); // beige
        btnSave.setCornerRadius((int) (10 * density));
        btnSave.setInsetTop(0);
        btnSave.setInsetBottom(0);
        if (esteDejaSalvat) {
            btnSave.setText("✓ Saved");
            btnSave.setEnabled(false);
        } else {
            btnSave.setOnClickListener(v -> salveazaTripulGenerat(trip, btnSave));
        }


        // ── 2. INSEREAZĂ ACEST BUTON NOU ("VIEW ON MAP") EXACT AICI ──
        com.google.android.material.button.MaterialButton btnMap = new com.google.android.material.button.MaterialButton(this);
        btnMap.setText("View on Map");
        btnMap.setTextSize(12f);
        btnMap.setAllCaps(false);
        btnMap.setTextColor(Color.WHITE);
        btnMap.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#011C40"))); // navy_blue_1
        btnMap.setCornerRadius((int) (10 * density));
        btnMap.setInsetTop(0);
        btnMap.setInsetBottom(0);


        // Adăugăm o margine mică la stânga butonului ca să nu se lipească de butonul Save
        LinearLayout.LayoutParams mapBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mapBtnParams.leftMargin = (int) (8 * density);
        btnMap.setLayoutParams(mapBtnParams);

        btnMap.setOnClickListener(v -> {
            // 1. Salvare automată în fundal ca draft pentru a nu pierde progresul în "Recente"
            if (trip != null && !esteDejaSalvat) {
                String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid != null) {
                    // Îi setăm temporar statusul de draft ca să știm că nu e salvată definitiv
                    trip.setStatus("draft");

                    TripRepository localRepo = new TripRepository();
                    localRepo.addSavedTrip(uid, trip, new TripRepository.TripCallback() {
                        @Override
                        public void onSuccess(Trip savedTrip) {
                            trip.setTripId(savedTrip.getTripId());
                            // Logăm în istoric pentru ca AI Hub să o afișeze la Recente cu timpul relativ ("now")
                            logheazaIstoricConversatie(trip);
                            deschideExploreInRouteMode(trip);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.e("AIChat", "Autosave draft failed: " + errorMessage);
                            // Chiar daca autosave-ul esueaza, tot deschidem harta - userul nu trebuie blocat
                            deschideExploreInRouteMode(trip);
                        }
                    });
                    return;
                }
            }

            // Trip deja salvat (sau fara user logat) - deschidem direct
            deschideExploreInRouteMode(trip);
        });


        // ── 3. MODIFICĂ MODUL ÎN CARE LE ADAUGI ÎN ANTET ──
        // În loc să adaugi doar btnSave, le adaugi pe amândouă în containerul antetului:
        antet.addView(coloanaTitlu);
        antet.addView(btnSave);
        antet.addView(btnMap); // Adăugat în dreapta butonului de Save

        // Rand de statistici: DAYS / BUDGET / SPOTS
        LinearLayout randStatistici = new LinearLayout(this);
        randStatistici.setOrientation(LinearLayout.HORIZONTAL);
        int paddingStat = (int) (12 * density);
        randStatistici.setPadding(paddingStat, paddingStat, paddingStat, paddingStat);
        randStatistici.addView(construiesteStatistica(String.valueOf(trip.getDaysCount()), "DAYS", "#A7EBF2"));   // light_turq_2
        randStatistici.addView(construiesteStatistica("€" + Math.round(bugetTotal), "BUDGET", "#EAD098"));         // beige
        randStatistici.addView(construiesteStatistica(String.valueOf(nrSpoturi), "SPOTS", "#BDD8E9"));             // sky_blue_2

        continut.addView(antet);
        continut.addView(randStatistici);

        // Zilele - carduri glisabile orizontal (nu inghesuite vertical), fiecare cu zona,
        // atractiile SI restaurantele zilei respective.
        List<Integer> zileOrdonate = new ArrayList<>(peZile.keySet());

        android.widget.HorizontalScrollView scrollZile = new android.widget.HorizontalScrollView(this);
        scrollZile.setHorizontalScrollBarEnabled(false);
        scrollZile.setClipToPadding(false);
        int paddingScroll = (int) (12 * density);
        scrollZile.setPadding(paddingScroll, 0, paddingScroll, paddingScroll);

        LinearLayout containerZile = new LinearLayout(this);
        containerZile.setOrientation(LinearLayout.HORIZONTAL);

        for (int numarZi : zileOrdonate) {
            View cardZi = construiesteCardZi(numarZi, peZile.get(numarZi), hartaAtractii, hartaRestaurante);
            containerZile.addView(cardZi);
        }

        scrollZile.addView(containerZile);
        continut.addView(scrollZile);

        card.addView(continut);
        messagesContainer.addView(card);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    /** Salveaza efectiv trip-ul (apelat DOAR la apasarea butonului Save de pe card). */
    private void salveazaTripulGenerat(Trip trip, com.google.android.material.button.MaterialButton btnSave) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            android.widget.Toast.makeText(this, "You need to be logged in to save a trip.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        TripRepository tripRepositoryLocal = new TripRepository();
        TripRepository.TripCallback callback = new TripRepository.TripCallback() {
            @Override
            public void onSuccess(Trip savedTrip) {
                trip.setTripId(savedTrip.getTripId()); // important - ca o editare + re-salvare ulterioara sa actualizeze, nu sa duplice
                modificariNesalvate = false;
                btnSave.setText("✓ Saved");
                android.widget.Toast.makeText(AIChatActivity.this, "Trip planned!", android.widget.Toast.LENGTH_SHORT).show();
                logheazaIstoricConversatie(trip);
            }

            @Override
            public void onError(String errorMessage) {
                btnSave.setEnabled(true);
                btnSave.setText("Save");
                android.widget.Toast.makeText(AIChatActivity.this, "Couldn't save: " + errorMessage, android.widget.Toast.LENGTH_LONG).show();
            }
        };

        if (trip.getTripId() != null) {
            // Editam un draft existent - actualizam, nu cream un document nou
            tripRepositoryLocal.updateSavedTrip(uid, trip.getTripId(), trip, callback);
        } else {
            tripRepositoryLocal.addSavedTrip(uid, trip, callback);
        }
    }
    private void deschideExploreInRouteMode(Trip trip) {
        try {
            Intent mapIntent = new Intent(AIChatActivity.this, ExploreActivity.class);
            mapIntent.putExtra("cityId", trip.getCityId());
            mapIntent.putExtra("cityName", trip.getCityName());
            mapIntent.putExtra("countryId", trip.getCountryId());
            mapIntent.putExtra("tripId", trip.getTripId()); // ID-ul esential pentru preluarea itinerariului
            mapIntent.putExtra("isRouteModeActive", true);   // Forteaza Route Mode direct la deschidere
            startActivity(mapIntent);
        } catch (Exception e) {
            android.widget.Toast.makeText(AIChatActivity.this, "Map screen is still under construction!", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout construiesteStatistica(String valoare, String eticheta, String culoareHex) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout cutie = new LinearLayout(this);
        cutie.setOrientation(LinearLayout.VERTICAL);
        cutie.setGravity(Gravity.CENTER);

        GradientDrawable fundal = new GradientDrawable();
        fundal.setColor(Color.WHITE);
        fundal.setStroke((int) (1.5f * density), Color.parseColor("#011C40"));
        fundal.setCornerRadius(12 * density);
        cutie.setBackground(fundal);

        int paddingV = (int) (10 * density);
        int paddingH = (int) (6 * density);
        cutie.setPadding(paddingH, paddingV, paddingH, paddingV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int margine = (int) (5 * density);
        params.leftMargin = margine;
        params.rightMargin = margine;
        cutie.setLayoutParams(params);

        TextView txtValoare = new TextView(this);
        txtValoare.setText(valoare);
        txtValoare.setTextColor(Color.parseColor("#011C40"));
        txtValoare.setTextSize(17f);
        txtValoare.setTypeface(txtValoare.getTypeface(), android.graphics.Typeface.BOLD);
        txtValoare.setGravity(Gravity.CENTER);

        TextView txtEticheta = new TextView(this);
        txtEticheta.setText(eticheta);
        txtEticheta.setTextColor(Color.parseColor("#999999"));
        txtEticheta.setTextSize(10f);
        txtEticheta.setGravity(Gravity.CENTER);

        cutie.addView(txtValoare);
        cutie.addView(txtEticheta);
        return cutie;
    }

    /** Card individual pentru o zi - zona, atractiile si restaurantele acelei zile. */
    private View construiesteCardZi(int numarZi, List<ItineraryItem> itemsZi,
                                    Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante) {
        float density = getResources().getDisplayMetrics().density;

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setRadius(14 * density);
        card.setCardElevation(2 * density);
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                (int) (200 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.rightMargin = (int) (10 * density);
        card.setLayoutParams(cardParams);

        LinearLayout continut = new LinearLayout(this);
        continut.setOrientation(LinearLayout.VERTICAL);

        // Zona zilei (daca exista, o luam de la prima atractie)
        String zona = null;
        for (ItineraryItem item : itemsZi) {
            if ("attraction".equals(item.getType())) {
                Attraction a = hartaAtractii.get(item.getPoiId());
                if (a != null && a.getZone() != null) {
                    zona = a.getZone().replace("_", " ");
                    break;
                }
            }
        }

        LinearLayout antetZi = new LinearLayout(this);
        antetZi.setOrientation(LinearLayout.VERTICAL);
        antetZi.setBackgroundColor(Color.parseColor("#54ACBF")); // light_turq_1
        int paddingAntet = (int) (10 * density);
        antetZi.setPadding(paddingAntet, paddingAntet, paddingAntet, paddingAntet);

        TextView txtZi = new TextView(this);
        txtZi.setText("Day " + numarZi);
        txtZi.setTextColor(Color.WHITE);
        txtZi.setTextSize(14f);
        txtZi.setTypeface(txtZi.getTypeface(), android.graphics.Typeface.BOLD);
        antetZi.addView(txtZi);

        if (zona != null) {
            TextView txtZona = new TextView(this);
            txtZona.setText(capitalizeazaCuvinte(zona));
            txtZona.setTextColor(Color.parseColor("#E0FFFFFF"));
            txtZona.setTextSize(11f);
            antetZi.addView(txtZona);
        }

        continut.addView(antetZi);

        // Ordonam CRONOLOGIC (mic dejun -> atractie -> pranz -> atractie -> cina), nu grupat pe tip
        List<ItineraryItem> itemsOrdonate = new ArrayList<>(itemsZi);
        itemsOrdonate.sort(Comparator.comparingDouble(item -> calculeazaSlotOrar(item, hartaAtractii, hartaRestaurante)));

        LinearLayout listaOrdonata = new LinearLayout(this);
        listaOrdonata.setOrientation(LinearLayout.VERTICAL);
        int paddingLista = (int) (10 * density);
        listaOrdonata.setPadding(paddingLista, paddingLista, paddingLista, paddingLista);

        for (ItineraryItem item : itemsOrdonate) {
            String nume = null;
            String emoji = "";
            if ("attraction".equals(item.getType())) {
                Attraction a = hartaAtractii.get(item.getPoiId());
                if (a != null) { nume = a.getName(); emoji = "🏛"; }
            } else {
                Restaurant r = hartaRestaurante.get(item.getPoiId());
                if (r != null) { nume = r.getName(); emoji = "🍽"; }
            }
            if (nume == null) continue;

            TextView txt = new TextView(this);
            txt.setText(emoji + " " + nume);
            txt.setTextColor(Color.parseColor("#011C40"));
            txt.setTextSize(12f);
            txt.setPadding(0, (int) (2 * density), 0, (int) (2 * density));
            listaOrdonata.addView(txt);
        }

        continut.addView(listaOrdonata);
        card.addView(continut);
        return card;
    }

    /**
     * Calculeaza o "ora aproximativa" pentru un item, ca sa poata fi sortate cronologic:
     * breakfast/brunch (0) -> atractii de dimineata (1) -> lunch (2) -> atractii de seara (3) -> dinner (4).
     */
    private double calculeazaSlotOrar(ItineraryItem item, Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante) {
        if ("restaurant".equals(item.getType())) {
            Restaurant r = hartaRestaurante.get(item.getPoiId());
            if (r != null && r.getMealTime() != null) {
                for (String mt : r.getMealTime()) {
                    if (mt == null) continue;
                    if (mt.equalsIgnoreCase("breakfast") || mt.equalsIgnoreCase("brunch")) return 0;
                    if (mt.equalsIgnoreCase("lunch")) return 2;
                    if (mt.equalsIgnoreCase("dinner")) return 4;
                }
            }
            return 2; // necunoscut - il punem la mijloc, ca pranz
        } else {
            Attraction a = hartaAtractii.get(item.getPoiId());
            if (a != null && a.getBestTimeOfDay() != null) {
                String bt = a.getBestTimeOfDay().toLowerCase();
                if (bt.equals("evening") || bt.equals("sunset") || bt.equals("night")) {
                    return 3 + (a.getRouteOrder() / 1000.0);
                }
            }
            int routeOrder = a != null ? a.getRouteOrder() : 0;
            return 1 + (routeOrder / 1000.0); // dimineata/oricand, ordonate intre ele dupa routeOrder
        }
    }

    private String capitalizeazaCuvinte(String text) {
        String[] cuvinte = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String cuvant : cuvinte) {
            if (cuvant.isEmpty()) continue;
            sb.append(Character.toUpperCase(cuvant.charAt(0))).append(cuvant.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private double estimeazaPretRestaurant(String priceRange) {
        if (priceRange == null) return 0;
        switch (priceRange.trim()) {
            case "€": return 15;
            case "€€": return 30;
            case "€€€": return 50;
            case "€€€€": return 80;
            default: return 25;
        }
    }

    /** Cardul de vreme - idee generala de sezon (luna calatoriei) + vremea reala, daca data e suficient de aproape. */
    /**
     * Verifica vremea pentru fiecare zi (DOAR daca e in urmatoarele 5 zile - limita prognozei reale;
     * pentru zile mai indepartate, pur si simplu nu stim, deci nu aratam nimic, ca sa nu inducem in eroare).
     * Daca plouă probabil intr-o zi, sugereaza alternative indoor, nefolosite inca in itinerariu.
     */
    private void verificaVremePeZileSiSugereazaAlternative(Trip trip, Map<String, Attraction> hartaAtractii) {
        if (trip.getStartDate() == null || trip.getItinerary() == null) return; // fara data exacta, nu stim ce zi calendaristica e fiecare

        db.collection("countries").document(trip.getCountryId())
                .collection("cities").document(trip.getCityId())
                .get()
                .addOnSuccessListener(doc -> {
                    City city = doc.toObject(City.class);
                    if (city == null || city.getLat() == null || city.getLon() == null) return;

                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                        java.util.Date dataStart = sdf.parse(trip.getStartDate());

                        java.util.Calendar azi = java.util.Calendar.getInstance();
                        azi.set(java.util.Calendar.HOUR_OF_DAY, 0);
                        azi.set(java.util.Calendar.MINUTE, 0);
                        azi.set(java.util.Calendar.SECOND, 0);
                        azi.set(java.util.Calendar.MILLISECOND, 0);

                        Map<Integer, List<ItineraryItem>> peZile = new java.util.TreeMap<>();
                        for (ItineraryItem item : trip.getItinerary()) {
                            peZile.computeIfAbsent(item.getDay(), k -> new ArrayList<>()).add(item);
                        }

                        Set<String> poiUtilizate = new java.util.HashSet<>();
                        for (ItineraryItem item : trip.getItinerary()) poiUtilizate.add(item.getPoiId());

                        // Container comun, glisabil - creat o singura data, la prima alerta gasita
                        LinearLayout[] containerAlerteVreme = new LinearLayout[1];

                        for (int zi : peZile.keySet()) {
                            java.util.Calendar dataZi = java.util.Calendar.getInstance();
                            dataZi.setTime(dataStart);
                            dataZi.add(java.util.Calendar.DAY_OF_YEAR, zi - 1);

                            long zileDiferenta = (dataZi.getTimeInMillis() - azi.getTimeInMillis()) / (1000L * 60 * 60 * 24);
                            if (zileDiferenta < 0) continue; // zi din trecut, nu are sens

                            int ziFinal = zi;

                            if (zileDiferenta <= 5) {
                                // In intervalul de prognoza reala - aratam cardul DOAR daca chiar plouă
                                WeatherHelper.fetchForecast(city.getLat(), city.getLon(), (int) zileDiferenta, new WeatherHelper.WeatherCallback() {
                                    @Override
                                    public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil) {
                                        if (!plouaProbabil) return;
                                        List<String> alternative = gasesteAlternativeIndoor(hartaAtractii, poiUtilizate);
                                        if (!alternative.isEmpty()) adaugaCardDeVremeInCarusel(containerAlerteVreme, ziFinal, alternative, false);
                                    }

                                    @Override
                                    public void onError(String mesajEroare) {
                                        // fara prognoza pentru acea zi - nu aratam nimic, mai bine tacem decat sa gresim
                                    }
                                });
                            } else {
                                // Prea departe ca sa stim sigur - aratam lista MEREU, ca rezerva pregatita,
                                // NU ca predictie de ploaie (n-avem de unde sa stim cu-atat inainte).
                                List<String> alternative = gasesteAlternativeIndoor(hartaAtractii, poiUtilizate);
                                if (!alternative.isEmpty()) adaugaCardDeVremeInCarusel(containerAlerteVreme, ziFinal, alternative, true);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
    }

    /** Adauga un card de alerta vreme in containerul comun glisabil - il creeaza la prima alerta gasita. */
    private void adaugaCardDeVremeInCarusel(LinearLayout[] containerRef, int numarZi, List<String> alternative, boolean esteOrientativ) {
        if (containerRef[0] == null) {
            float density = getResources().getDisplayMetrics().density;

            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            int paddingScroll = (int) (12 * density);
            scroll.setPadding(paddingScroll, 0, paddingScroll, paddingScroll);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(container);

            containerRef[0] = container;
            messagesContainer.addView(scroll);
        }

        View card = construiesteCardAlertaVreme(numarZi, alternative, esteOrientativ);
        card.setTag(numarZi);

        // Inseram in ordine cronologica (zilele pot ajunge in orice ordine, fiind async)
        int indexInserare = 0;
        for (int i = 0; i < containerRef[0].getChildCount(); i++) {
            Object tag = containerRef[0].getChildAt(i).getTag();
            if (tag instanceof Integer && (Integer) tag < numarZi) indexInserare = i + 1;
        }
        containerRef[0].addView(card, indexInserare);

        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    /** Cauta pana la 3 atractii indoor/both, nefolosite inca in itinerariu. */
    private List<String> gasesteAlternativeIndoor(Map<String, Attraction> hartaAtractii, Set<String> poiUtilizate) {
        List<String> alternative = new ArrayList<>();
        for (Attraction a : hartaAtractii.values()) {
            if (poiUtilizate.contains(a.getId())) continue;
            if (a.getIndoorOutdoor() == null) continue;
            String io = a.getIndoorOutdoor().toLowerCase();
            if (io.equals("indoor") || io.equals("both")) {
                alternative.add(a.getName());
            }
            if (alternative.size() >= 3) break;
        }
        return alternative;
    }

    /** Construieste (fara sa adauge) cardul de alerta vreme - lista de alternative indoor pentru o zi anume. */
    private View construiesteCardAlertaVreme(int numarZi, List<String> alternative, boolean esteOrientativ) {
        float density = getResources().getDisplayMetrics().density;

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setRadius(14 * density);
        card.setCardElevation(2 * density);
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                (int) (220 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.rightMargin = (int) (10 * density);
        card.setLayoutParams(cardParams);

        LinearLayout continut = new LinearLayout(this);
        continut.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (14 * density);
        continut.setPadding(padding, padding, padding, padding);

        TextView txtTitlu = new TextView(this);
        txtTitlu.setText(esteOrientativ
                ? "📋 Day " + numarZi + " - too far out to know for sure. Indoor backups, just in case:"
                : "☔ Rain expected on Day " + numarZi + " - consider swapping to:");
        txtTitlu.setTextColor(Color.parseColor("#011C40"));
        txtTitlu.setTextSize(12f);
        txtTitlu.setTypeface(txtTitlu.getTypeface(), android.graphics.Typeface.BOLD);
        continut.addView(txtTitlu);

        for (String nume : alternative) {
            TextView txt = new TextView(this);
            txt.setText("🏛 " + nume);
            txt.setTextColor(Color.parseColor("#011C40"));
            txt.setTextSize(12f);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemParams.topMargin = (int) (6 * density);
            txt.setLayoutParams(itemParams);
            continut.addView(txt);
        }

        TextView txtNota = new TextView(this);
        txtNota.setText("Say \"swap [attraction] for [one of these]\" to change it.");
        txtNota.setTextColor(Color.parseColor("#999999"));
        txtNota.setTextSize(11f);
        LinearLayout.LayoutParams notaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        notaParams.topMargin = (int) (8 * density);
        txtNota.setLayoutParams(notaParams);
        continut.addView(txtNota);

        card.addView(continut);
        return card;
    }

    private void adaugaCardulDeVreme(Trip trip) {
        db.collection("countries").document(trip.getCountryId())
                .collection("cities").document(trip.getCityId())
                .get()
                .addOnSuccessListener(doc -> {
                    City city = doc.toObject(City.class);
                    if (city == null || city.getLat() == null || city.getLon() == null) return;

                    trip.recalculateCountdown();
                    int zileRamase = trip.getDaysUntilTrip();

                    WeatherHelper.fetchWeatherSmart(city.getLat(), city.getLon(), zileRamase, new WeatherHelper.SmartWeatherCallback() {
                        @Override
                        public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil, boolean approximativ) {
                            addWeatherCard(temperatura, conditieCuEmoji, approximativ, city.getAvgDayBudget());
                        }

                        @Override
                        public void onError(String mesajEroare) {
                            // vremea e un bonus, nu esential - nu aratam eroare daca pica
                        }
                    });
                });
    }

    /** Card elegant de vreme - emoji mare, temperatura, conditie, plus o nota daca e aproximativ. */
    private void addWeatherCard(int temperatura, String conditieCuEmoji, boolean approximativ, Long avgDayBudget) {
        float density = getResources().getDisplayMetrics().density;

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setRadius(14 * density);
        card.setCardElevation(2 * density);
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.START;
        cardParams.bottomMargin = (int) (14 * density);
        card.setLayoutParams(cardParams);

        LinearLayout continut = new LinearLayout(this);
        continut.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (14 * density);
        continut.setPadding(padding, padding, padding, padding);

        TextView txtTitlu = new TextView(this);
        txtTitlu.setText(approximativ ? "General idea for that time of year:" : "Real forecast for your trip:");
        txtTitlu.setTextColor(Color.parseColor("#011C40"));
        txtTitlu.setTextSize(13f);
        txtTitlu.setTypeface(txtTitlu.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titluParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titluParams.bottomMargin = (int) (10 * density);
        txtTitlu.setLayoutParams(titluParams);
        continut.addView(txtTitlu);

        LinearLayout randColoane = new LinearLayout(this);
        randColoane.setOrientation(LinearLayout.HORIZONTAL);

        randColoane.addView(construiesteColoanaVreme(conditieCuEmoji.split(" ")[0], temperatura + "°", density));
        String textCondtie = conditieCuEmoji.contains(" ") ? conditieCuEmoji.substring(conditieCuEmoji.indexOf(" ") + 1) : "";
        randColoane.addView(construiesteColoanaVreme("📋", textCondtie, density));
        if (avgDayBudget != null) {
            randColoane.addView(construiesteColoanaVreme("💶", "€" + avgDayBudget + "/d", density));
        }

        continut.addView(randColoane);

        if (approximativ) {
            TextView txtNota = new TextView(this);
            txtNota.setText("Exact forecast unavailable this far out - shown as a general reference.");
            txtNota.setTextColor(Color.parseColor("#999999"));
            txtNota.setTextSize(11f);
            LinearLayout.LayoutParams notaParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            notaParams.topMargin = (int) (8 * density);
            txtNota.setLayoutParams(notaParams);
            continut.addView(txtNota);
        }

        card.addView(continut);
        messagesContainer.addView(card);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    private LinearLayout construiesteColoanaVreme(String emoji, String valoare, float density) {
        LinearLayout coloana = new LinearLayout(this);
        coloana.setOrientation(LinearLayout.VERTICAL);
        coloana.setGravity(Gravity.CENTER);
        int marginDreapta = (int) (16 * density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = marginDreapta;
        coloana.setLayoutParams(params);

        TextView txtEmoji = new TextView(this);
        txtEmoji.setText(emoji);
        txtEmoji.setTextSize(18f);
        txtEmoji.setGravity(Gravity.CENTER);

        TextView txtValoare = new TextView(this);
        txtValoare.setText(valoare);
        txtValoare.setTextColor(Color.parseColor("#011C40"));
        txtValoare.setTextSize(13f);
        txtValoare.setTypeface(txtValoare.getTypeface(), android.graphics.Typeface.BOLD);
        txtValoare.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valoareParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valoareParams.topMargin = (int) (4 * density);
        txtValoare.setLayoutParams(valoareParams);

        coloana.addView(txtEmoji);
        coloana.addView(txtValoare);
        return coloana;
    }

    /** Verifica daca mesajul pare o comanda de editare a itinerariului curent. */
    private boolean esteComandaDeEditare(String text) {
        String t = text.toLowerCase();
        return t.contains("add ") || t.contains("remove ") || t.contains("delete ")
                || t.contains("swap ") || t.contains("replace ") || t.contains("instead of");
    }

    /** Aplica o comanda de editare (add/remove/swap) pe tripCurent, apoi re-afiseaza cardul actualizat.
     *  Foloseste hartile deja incarcate (hartaAtractiiCurenta/hartaRestauranteCurenta) - contin deja
     *  toate atractiile/restaurantele orasului, nu doar cele din itinerariu, deci gasim si POI-uri noi. */
    private void proceseazaEditareItinerar(String text) {
        String t = text.toLowerCase();
        modificariNesalvate = true;

        if (t.contains("remove ") || t.contains("delete ")) {
            aplicaStergere(t, hartaAtractiiCurenta, hartaRestauranteCurenta);
        } else if (t.contains("swap ") || t.contains("replace ") || t.contains("instead of")) {
            aplicaInlocuire(t, hartaAtractiiCurenta, hartaRestauranteCurenta);
        } else if (t.contains("add ")) {
            aplicaAdaugare(t, hartaAtractiiCurenta, hartaRestauranteCurenta);
        }
    }

    /** Cauta un poiId (atractie sau restaurant) al carui NUME apare in text. Intoarce null daca nu gaseste. */
    private String gasestePoiIdInText(String text, Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante, boolean[] esteAtractieOut) {
        for (Map.Entry<String, Attraction> e : hartaAtractii.entrySet()) {
            if (e.getValue().getName() != null && text.contains(e.getValue().getName().toLowerCase())) {
                esteAtractieOut[0] = true;
                return e.getKey();
            }
        }
        for (Map.Entry<String, Restaurant> e : hartaRestaurante.entrySet()) {
            if (e.getValue().getName() != null && text.contains(e.getValue().getName().toLowerCase())) {
                esteAtractieOut[0] = false;
                return e.getKey();
            }
        }
        return null;
    }

    private void aplicaStergere(String text, Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante) {
        boolean[] esteAtractie = new boolean[1];
        String poiId = gasestePoiIdInText(text, hartaAtractii, hartaRestaurante, esteAtractie);

        if (poiId == null) {
            addAiBubble("I couldn't find that in your itinerary - could you name it exactly as shown on the card?");
            return;
        }

        ItineraryItem deSters = null;
        for (ItineraryItem item : tripCurent.getItinerary()) {
            if (item.getPoiId().equals(poiId)) { deSters = item; break; }
        }

        if (deSters == null) {
            addAiBubble("That's not currently in your itinerary.");
            return;
        }

        tripCurent.getItinerary().remove(deSters);
        String nume = esteAtractie[0] ? hartaAtractii.get(poiId).getName() : hartaRestaurante.get(poiId).getName();
        addAiBubble("Removed " + nume + " from your itinerary. Here's the updated plan:");
        reafiseazaCardulActualizat();
    }

    /** Cauta un tipar "day 2", "day2", "on day 3" etc. in text. Intoarce -1 daca nu gaseste, sau numarul e in afara intervalului valid. */
    private int extrageZiuaDinText(String text, int maxZile) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("day\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(text.toLowerCase());
        if (matcher.find()) {
            try {
                int zi = Integer.parseInt(matcher.group(1));
                if (zi >= 1 && zi <= maxZile) return zi;
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private void aplicaAdaugare(String text, Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante) {
        boolean[] esteAtractie = new boolean[1];
        String poiId = gasestePoiIdInText(text, hartaAtractii, hartaRestaurante, esteAtractie);

        if (poiId == null) {
            addAiBubble("I couldn't find a place with that name in " + tripCurent.getCityName() + ".");
            return;
        }

        boolean dejaInItinerar = false;
        for (ItineraryItem item : tripCurent.getItinerary()) {
            if (item.getPoiId().equals(poiId)) { dejaInItinerar = true; break; }
        }
        if (dejaInItinerar) {
            addAiBubble("That's already in your itinerary!");
            return;
        }

        // Daca userul a specificat o zi (ex: "add Pantheon to day 2"), o folosim; altfel, ultima zi.
        int ziSpecificata = extrageZiuaDinText(text, tripCurent.getDaysCount());
        int ziTinta = ziSpecificata != -1 ? ziSpecificata : tripCurent.getDaysCount();
        tripCurent.getItinerary().add(new ItineraryItem(poiId, esteAtractie[0] ? "attraction" : "restaurant", ziTinta));

        String nume = esteAtractie[0] ? hartaAtractii.get(poiId).getName() : hartaRestaurante.get(poiId).getName();
        addAiBubble("Added " + nume + " to Day " + ziTinta + ". Here's the updated plan:");
        reafiseazaCardulActualizat();
    }

    private void aplicaInlocuire(String text, Map<String, Attraction> hartaAtractii, Map<String, Restaurant> hartaRestaurante) {
        // Cautam printre elementele DEJA din itinerar, care nume apare in text (de scos)
        String poiIdVechi = null;
        for (ItineraryItem item : tripCurent.getItinerary()) {
            String nume = "attraction".equals(item.getType())
                    ? (hartaAtractii.containsKey(item.getPoiId()) ? hartaAtractii.get(item.getPoiId()).getName() : null)
                    : (hartaRestaurante.containsKey(item.getPoiId()) ? hartaRestaurante.get(item.getPoiId()).getName() : null);
            if (nume != null && text.contains(nume.toLowerCase())) { poiIdVechi = item.getPoiId(); break; }
        }

        if (poiIdVechi == null) {
            addAiBubble("I couldn't tell which item to replace - could you name it exactly as shown on the card?");
            return;
        }

        ItineraryItem itemVechi = null;
        for (ItineraryItem item : tripCurent.getItinerary()) {
            if (item.getPoiId().equals(poiIdVechi)) { itemVechi = item; break; }
        }

        // Cautam un POI NOU (care nu-i deja in itinerar) mentionat in text
        String finalPoiIdVechi = poiIdVechi;
        boolean[] esteAtractieNou = new boolean[1];
        String poiIdNou = null;
        for (Map.Entry<String, Attraction> e : hartaAtractii.entrySet()) {
            if (e.getKey().equals(finalPoiIdVechi)) continue;
            if (e.getValue().getName() != null && text.contains(e.getValue().getName().toLowerCase())) {
                poiIdNou = e.getKey();
                esteAtractieNou[0] = true;
                break;
            }
        }
        if (poiIdNou == null) {
            for (Map.Entry<String, Restaurant> e : hartaRestaurante.entrySet()) {
                if (e.getKey().equals(finalPoiIdVechi)) continue;
                if (e.getValue().getName() != null && text.contains(e.getValue().getName().toLowerCase())) {
                    poiIdNou = e.getKey();
                    esteAtractieNou[0] = false;
                    break;
                }
            }
        }

        if (poiIdNou == null) {
            addAiBubble("I found what to remove, but not what to replace it with - could you name the new place exactly?");
            return;
        }

        int ziItemVechi = itemVechi.getDay();
        tripCurent.getItinerary().remove(itemVechi);
        tripCurent.getItinerary().add(new ItineraryItem(poiIdNou, esteAtractieNou[0] ? "attraction" : "restaurant", ziItemVechi));

        String numeVechi = "attraction".equals(itemVechi.getType()) ? hartaAtractii.get(poiIdVechi).getName() : hartaRestaurante.get(poiIdVechi).getName();
        String numeNou = esteAtractieNou[0] ? hartaAtractii.get(poiIdNou).getName() : hartaRestaurante.get(poiIdNou).getName();
        addAiBubble("Swapped " + numeVechi + " for " + numeNou + ". Here's the updated plan:");
        reafiseazaCardulActualizat();
    }

    /** Re-afiseaza cardul de itinerariu, cu datele actualizate dupa o editare. */
    private void reafiseazaCardulActualizat() {
        addItineraryCard(tripCurent, hartaAtractiiCurenta, hartaRestauranteCurenta, false);
    }

    /** Loghezaza o intrare in istoric (DOAR pentru itinerarii, nu vreme/buget), la salvarea efectiva. */
    private void logheazaIstoricConversatie(Trip trip) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        new TripRepository().logChatHistoryEntry(uid, trip.getCityName(), trip.getTripId(), trip.getDaysCount(),
                new TripRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d("AIChat", "Istoric logat cu succes pentru: " + trip.getCityName());
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e("AIChat", "Nu s-a putut loga istoricul: " + errorMessage);
                    }
                });
    }

    /** Reafiseaza un itinerariu DEJA SALVAT (accesat din "Recent" in AI Hub) - butonul Save apare deja bifat. */
    @SuppressWarnings("unchecked")
    private void aratatItinerariulSalvat(String tripId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("users").document(uid).collection("savedTrips").document(tripId).get()
                .addOnSuccessListener(doc -> {
                    Trip trip = doc.toObject(Trip.class);
                    if (trip == null) {
                        addAiBubble("I couldn't find that trip anymore - it might have been deleted.");
                        return;
                    }
                    if (trip.getTripId() == null) trip.setTripId(tripId);

                    PoiRepository poiRepository = new PoiRepository();
                    poiRepository.getAttractions(trip.getCountryId(), trip.getCityId(), new PoiRepository.AttractionListCallback() {
                        @Override
                        public void onSuccess(List<Attraction> attractions) {
                            Map<String, Attraction> hartaAtractii = new HashMap<>();
                            for (Attraction a : attractions) hartaAtractii.put(a.getId(), a);

                            poiRepository.getRestaurants(trip.getCountryId(), trip.getCityId(), new PoiRepository.RestaurantListCallback() {
                                @Override
                                public void onSuccess(List<Restaurant> restaurants) {
                                    Map<String, Restaurant> hartaRestaurante = new HashMap<>();
                                    for (Restaurant r : restaurants) hartaRestaurante.put(r.getId(), r);

                                    tripCurent = trip;
                                    hartaAtractiiCurenta = hartaAtractii;
                                    hartaRestauranteCurenta = hartaRestaurante;
                                    modificariNesalvate = false;

                                    addAiBubble("Here's your saved itinerary for " + trip.getCityName() + "! You can still ask me to add, remove, or swap things.");
                                    boolean esteSalvatDefinitiv = "planned".equals(trip.getStatus());
                                    addItineraryCard(trip, hartaAtractii, hartaRestaurante, esteSalvatDefinitiv);
                                    adaugaCardulDeVreme(trip);
                                    verificaVremePeZileSiSugereazaAlternative(trip, hartaAtractii);
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    addAiBubble("Loaded the trip, but couldn't load restaurant details.");
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            addAiBubble("Couldn't load attraction details.");
                        }
                    });
                })
                .addOnFailureListener(e -> addAiBubble("Sorry, I couldn't load that trip."));
    }

    /** Porneste indicatorul "Typing..." cu punctul verde animat (pulsare), cat timp AI-ul proceseaza. */
    private void arataTypingIndicator() {
        TextView txtStatus = findViewById(R.id.txtTypingStatus);
        if (txtStatus != null) txtStatus.setText("Typing...");

        View dot = findViewById(R.id.dotTyping);
        if (dot != null) {
            android.view.animation.AlphaAnimation animatie = new android.view.animation.AlphaAnimation(1f, 0.3f);
            animatie.setDuration(600);
            animatie.setRepeatMode(android.view.animation.Animation.REVERSE);
            animatie.setRepeatCount(android.view.animation.Animation.INFINITE);
            dot.startAnimation(animatie);
        }
    }

    /** Opreste animatia si revine la starea normala ("Active"), cand raspunsul e gata. */
    private void ascundeTypingIndicator() {
        TextView txtStatus = findViewById(R.id.txtTypingStatus);
        if (txtStatus != null) txtStatus.setText("Active");

        View dot = findViewById(R.id.dotTyping);
        if (dot != null) {
            dot.clearAnimation();
            dot.setAlpha(1f);
        }
    }

    /**
     * "Ask anything" - cautare semantica pe atractii SI restaurante (nu doar orase, ca la recommend_destination).
     * Foloseste contextul itinerariului curent daca exista unul activ (cauta printre POI-urile nefolosite
     * din acel oras); altfel, cauta un nume de oras in text.
     */
    private void raspundeCuAskAnything(String textMesaj) {
        String t = textMesaj.toLowerCase();

        gasesteOraseInText(textMesaj, orase -> {
            if (orase.size() == 2) {
                City oras1 = orase.get(0);
                City oras2 = orase.get(1);

                if (t.contains("rating") || t.contains("rated") || t.contains("better reviewed")) {
                    raspundeCuComparatieRating(oras1, oras2);
                } else if (t.contains("budget") || t.contains("cheap") || t.contains("expensive") || t.contains("cost")) {
                    raspundeCuComparatieBuget(oras1, oras2);
                } else {
                    // Orice alta comparatie descriptiva ("more romantic", "better for families" etc.) -
                    // folosim modelul semantic, la fel ca la recomandarea de destinatie.
                    raspundeCuComparatieSemantica(textMesaj, oras1, oras2);
                }
            } else {
                continuaCuCautareaDePoi(textMesaj);
            }
        });
    }

    private void raspundeCuComparatieRating(City oras1, City oras2) {
        if (oras1.getRating() == null || oras2.getRating() == null) {
            addAiBubble("I don't have complete rating data to compare " + oras1.getName() + " and " + oras2.getName() + ".");
            return;
        }

        City maiBun = oras1.getRating() >= oras2.getRating() ? oras1 : oras2;
        City celalalt = maiBun == oras1 ? oras2 : oras1;

        if (oras1.getRating().equals(oras2.getRating())) {
            addAiBubble(oras1.getName() + " and " + oras2.getName() + " have the same rating: ☆ " + oras1.getRating());
        } else {
            addAiBubble(maiBun.getName() + " (☆ " + maiBun.getRating() + ") is rated higher than "
                    + celalalt.getName() + " (☆ " + celalalt.getRating() + ").");
        }
    }

    private void raspundeCuComparatieBuget(City oras1, City oras2) {
        if (oras1.getAvgDayBudget() == null || oras2.getAvgDayBudget() == null) {
            addAiBubble("I don't have complete budget data to compare " + oras1.getName() + " and " + oras2.getName() + ".");
            return;
        }
        City maiIeftin = oras1.getAvgDayBudget() <= oras2.getAvgDayBudget() ? oras1 : oras2;
        City maiScump = maiIeftin == oras1 ? oras2 : oras1;
        double diferenta = maiScump.getAvgDayBudget() - maiIeftin.getAvgDayBudget();
        addAiBubble(maiIeftin.getName() + " (€" + Math.round(maiIeftin.getAvgDayBudget()) + "/day) is cheaper than "
                + maiScump.getName() + " (€" + Math.round(maiScump.getAvgDayBudget()) + "/day) by about €"
                + Math.round(diferenta) + "/day.");
    }

    /** Comparatie generala, pe orice trasatura descrisa in text ("romantic", "historical", "family-friendly" etc.),
     *  folosind acelasi model semantic de la recomandarea de destinatie - fara date noi, doar reutilizare. */
    private void raspundeCuComparatieSemantica(String textMesaj, City oras1, City oras2) {
        executor.execute(() -> {
            try {
                double[] queryEmb = embeddingModel.getEmbedding(textMesaj);

                String profil1 = construiesteProfilOras(oras1);
                String profil2 = construiesteProfilOras(oras2);

                double scor1 = profil1.isEmpty() ? -2 : produsScalar(queryEmb, embeddingModel.getEmbedding(profil1));
                double scor2 = profil2.isEmpty() ? -2 : produsScalar(queryEmb, embeddingModel.getEmbedding(profil2));

                mainHandler.post(() -> {
                    City castigator = scor1 >= scor2 ? oras1 : oras2;
                    addAiBubble("Based on what you're asking, " + castigator.getName() + " seems like the better match!");
                });
            } catch (Exception e) {
                mainHandler.post(() -> addAiBubble("Sorry, something went wrong comparing those."));
            }
        });
    }

    private void continuaCuCautareaDePoi(String textMesaj) {
        if (tripCurent != null && hartaAtractiiCurenta != null && hartaRestauranteCurenta != null) {
            Set<String> poiUtilizate = new java.util.HashSet<>();
            if (tripCurent.getItinerary() != null) {
                for (ItineraryItem item : tripCurent.getItinerary()) poiUtilizate.add(item.getPoiId());
            }
            cautaSiAratePotriviri(textMesaj, hartaAtractiiCurenta, hartaRestauranteCurenta, poiUtilizate, tripCurent.getCityName());
            return;
        }

        gasesteOrasInText(textMesaj, new OrasGasitCallback() {
            @Override
            public void gasit(City oras) {
                PoiRepository poiRepository = new PoiRepository();
                poiRepository.getAttractions(oras.getCountryId(), oras.getId(), new PoiRepository.AttractionListCallback() {
                    @Override
                    public void onSuccess(List<Attraction> attractions) {
                        Map<String, Attraction> hartaA = new HashMap<>();
                        for (Attraction a : attractions) hartaA.put(a.getId(), a);

                        poiRepository.getRestaurants(oras.getCountryId(), oras.getId(), new PoiRepository.RestaurantListCallback() {
                            @Override
                            public void onSuccess(List<Restaurant> restaurants) {
                                Map<String, Restaurant> hartaR = new HashMap<>();
                                for (Restaurant r : restaurants) hartaR.put(r.getId(), r);
                                cautaSiAratePotriviri(textMesaj, hartaA, hartaR, new java.util.HashSet<>(), oras.getName());
                            }

                            @Override
                            public void onError(String errorMessage) {
                                addAiBubble("Sorry, something went wrong searching in " + oras.getName() + ".");
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        addAiBubble("Sorry, something went wrong searching in " + oras.getName() + ".");
                    }
                });
            }

            @Override
            public void negasit() {
                addAiBubble("I don't have detailed historical or trivia info yet - but I can help you find attractions or restaurants that match what you're looking for! Try mentioning a city (e.g. \"something artsy in Rome\"), or open an itinerary first and I'll search within that trip.");
            }
        });
    }

    /** Cauta semantic printre atractiile/restaurantele date, exclude cele deja folosite, arata top 3. */
    private void cautaSiAratePotriviri(String textMesaj, Map<String, Attraction> hartaAtractii,
                                       Map<String, Restaurant> hartaRestaurante, Set<String> poiUtilizate, String numeOras) {
        executor.execute(() -> {
            try {
                double[] queryEmbedding = embeddingModel.getEmbedding(textMesaj);
                List<PotrivirePoi> potriviri = new java.util.ArrayList<>();

                for (Attraction a : hartaAtractii.values()) {
                    if (poiUtilizate.contains(a.getId())) continue;
                    String profil = construiesteProfilPoi(a.getName(), a.getCategory(), a.getTags());
                    // Chiar dacă profilul e gol, lăsăm măcar numele pentru comparație ca să nu dea skip
                    if (profil.isEmpty()) profil = a.getName() != null ? a.getName() : "";

                    double[] emb = embeddingModel.getEmbedding(profil);
                    double scor = produsScalar(queryEmbedding, emb);
                    potriviri.add(new PotrivirePoi(a.getId(), a.getName(), a.getCategory(), scor, true));
                }

                for (Restaurant r : hartaRestaurante.values()) {
                    if (poiUtilizate.contains(r.getId())) continue;
                    String profil = construiesteProfilRestaurant(r);
                    if (profil.isEmpty()) profil = r.getName() != null ? r.getName() : "";

                    double[] emb = embeddingModel.getEmbedding(profil);
                    double scor = produsScalar(queryEmbedding, emb);
                    potriviri.add(new PotrivirePoi(r.getId(), r.getName(), r.getCategory(), scor, false));
                }

                potriviri.sort((a, b) -> Double.compare(b.scor, a.scor));

                // Siguranță în cazul în care lista totală e goală
                if (potriviri.isEmpty()) {
                    mainHandler.post(() -> addAiBubble("I couldn't find any locations registered in " + numeOras + "."));
                    return;
                }

                List<PotrivirePoi> top = potriviri.subList(0, Math.min(3, potriviri.size()));

                mainHandler.post(() -> {
                    addAiBubble("Here is what I found matching your request in " + numeOras + ":");

                    float density = getResources().getDisplayMetrics().density;
                    android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(AIChatActivity.this);
                    scroll.setHorizontalScrollBarEnabled(false);
                    scroll.setClipToPadding(false);
                    scroll.setPadding((int)(12 * density), 0, (int)(12 * density), (int)(14 * density));

                    LinearLayout container = new LinearLayout(AIChatActivity.this);
                    container.setOrientation(LinearLayout.HORIZONTAL);

                    for (PotrivirePoi p : top) {
                        View cardPoi = android.view.LayoutInflater.from(AIChatActivity.this)
                                .inflate(R.layout.item_ai_destination_card, container, false);

                        android.widget.ImageView img = cardPoi.findViewById(R.id.imgDestinationCard);
                        TextView name = cardPoi.findViewById(R.id.txtDestinationName);
                        TextView tagline = cardPoi.findViewById(R.id.txtDestinationTagline);
                        com.google.android.material.button.MaterialButton btnView = cardPoi.findViewById(R.id.btnViewDestination);

                        name.setText(p.nume);
                        tagline.setText(p.categorie != null ? p.categorie : (p.esteAtractie ? "Attraction" : "Restaurant"));

                        String imageUrl = null;
                        String officialLink = null;

                        // ── INSEREAZĂ ACEST BLOC ÎN INTERIORUL BUCLEI FOR (PotrivirePoi p : top) ──

                        if (p.esteAtractie) {
                            Attraction a = hartaAtractii.get(p.id);
                            if (a != null) {
                                imageUrl = a.getImageUrl();
                                if (a.getInfoUrl() != null && !a.getInfoUrl().trim().isEmpty()) {
                                    officialLink = a.getInfoUrl();
                                } else {
                                    officialLink = a.getTicketUrl();
                                }
                            }

                            // ── CONFIGURARE TEXT ȘI VIZIBILITATE BUTON ──
                            btnView.setVisibility(android.view.View.VISIBLE);
                            btnView.setText("View Details");
                            btnView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f); // Text o idee mai mic ca să respire
                            btnView.setGravity(android.view.Gravity.CENTER);
                            btnView.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);

                            // Resetăm padding-ul și marginile interne invizibile ale MaterialButton care decalează textul
                            btnView.setPadding(0, 0, 0, 0);
                            btnView.setInsetTop(0);
                            btnView.setInsetBottom(0);

                            // ── FORȚARE BUTON MAI ÎNGUST ȘI ALINIAT PE CENTRUL CARDULUI ──
                            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                                    (int)(160 * density), // Lățime fixă compactă de 140dp (nu mai atinge marginile)
                                    (int)(36 * density)   // Înălțime fixă elegantă
                            );
                            btnParams.gravity = android.view.Gravity.CENTER_HORIZONTAL; // Îl forțează să stea pe mijlocul axei orizontale a cardului
                            btnParams.topMargin = (int)(10 * density);
                            btnParams.bottomMargin = (int)(6 * density);
                            btnView.setLayoutParams(btnParams);
                        } else {
                            Restaurant r = hartaRestaurante.get(p.id);
                            if (r != null) {
                                imageUrl = r.getImageUrl();
                            }
                            // Ascundem butonul pentru restaurante
                            btnView.setVisibility(android.view.View.GONE);
                        }

                        // Încărcare imagine din cloud
                        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                            String finalImageUrl = imageUrl;
                            java.util.concurrent.ForkJoinPool.commonPool().execute(() -> {
                                try {
                                    java.io.InputStream in = new java.net.URL(finalImageUrl).openStream();
                                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
                                    mainHandler.post(() -> img.setImageBitmap(bitmap));
                                } catch (Exception e) {
                                    mainHandler.post(() -> img.setImageResource(R.drawable.rome));
                                }
                            });
                        } else {
                            img.setImageResource(R.drawable.rome);
                        }

                        // Acțiune buton detalii
                        if (p.esteAtractie) {
                            String finalOfficialLink = officialLink;
                            btnView.setOnClickListener(v -> {
                                if (finalOfficialLink != null && !finalOfficialLink.trim().isEmpty()) {
                                    try {
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(finalOfficialLink));
                                        startActivity(browserIntent);
                                    } catch (Exception e) {
                                        android.widget.Toast.makeText(AIChatActivity.this, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    android.widget.Toast.makeText(AIChatActivity.this, "No website link available in database.", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        // Card lărgit la 220dp pentru lizibilitate maximă
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(220 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.rightMargin = (int)(12 * density);
                        cardPoi.setLayoutParams(lp);

                        container.addView(cardPoi);
                    }

                    scroll.addView(container);
                    messagesContainer.addView(scroll);
                    scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
                });
            } catch (Exception e) {
                Log.e("AIChat", "Error in semantic search", e);
            }
        });
    }

    /** Profil mai bogat, specific restaurantelor - include si mealTime si bestFor, nu doar categorie/tags. */
    private String construiesteProfilRestaurant(Restaurant r) {
        StringBuilder sb = new StringBuilder();
        if (r.getName() != null) sb.append(r.getName()).append(": ");
        if (r.getCategory() != null) sb.append(r.getCategory()).append(". ");
        if (r.getMealTime() != null && !r.getMealTime().isEmpty()) {
            sb.append(String.join(", ", r.getMealTime())).append(". ");
        }
        if (r.getBestFor() != null && !r.getBestFor().isEmpty()) {
            sb.append("good for ").append(String.join(", ", r.getBestFor())).append(". ");
        }
        if (r.getTags() != null && !r.getTags().isEmpty()) {
            sb.append(String.join(", ", r.getTags()));
        }
        return sb.toString().trim();
    }

    /** Textul folosit pentru comparatia semantica a unui POI (atractie sau restaurant) - nume + categorie + tags. */
    private String construiesteProfilPoi(String nume, String categorie, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        if (nume != null) sb.append(nume).append(": ");
        if (categorie != null) sb.append(categorie).append(". ");
        if (tags != null && !tags.isEmpty()) sb.append(String.join(", ", tags));
        return sb.toString().trim();
    }

    /** Rezultatul unei potriviri semantice de POI - doar pentru afisare, nu se salveaza nicaieri. */
    private static class PotrivirePoi {
        final String id;
        final String nume;
        final String categorie;
        final double scor;
        final boolean esteAtractie;

        PotrivirePoi(String id, String nume, String categorie, double scor, boolean esteAtractie) {
            this.id = id;
            this.nume = nume;
            this.categorie = categorie;
            this.scor = scor;
            this.esteAtractie = esteAtractie;
        }
    }
    private void addUserBubble(String text) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(14f);
        bubble.setBackgroundResource(R.drawable.bg_square_button_dark);
        int paddingPx = (int) (12 * getResources().getDisplayMetrics().density);
        bubble.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        int marginPx = (int) (14 * getResources().getDisplayMetrics().density);
        params.bottomMargin = marginPx;
        bubble.setLayoutParams(params);

        messagesContainer.addView(bubble);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    private void addAiBubble(String text) {
        ascundeTypingIndicator();

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.parseColor("#011C40"));
        bubble.setTextSize(14f);

        GradientDrawable fundal = new GradientDrawable();
        fundal.setColor(Color.parseColor("#F0EEE8"));
        fundal.setCornerRadius(24f);
        bubble.setBackground(fundal);

        int paddingPx = (int) (12 * getResources().getDisplayMetrics().density);
        bubble.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START;
        int marginPx = (int) (14 * getResources().getDisplayMetrics().density);
        params.bottomMargin = marginPx;
        bubble.setLayoutParams(params);

        messagesContainer.addView(bubble);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (intentClassifier != null) intentClassifier.elibereaza();
        if (embeddingModel != null) embeddingModel.elibereaza();
        executor.shutdown();
    }
}