package com.example.mobile_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CityDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    private TextView txtCityName, txtCountryLabel, txtCityRating, txtCityTagline;
    private TextView txtCurrency, txtLanguage, txtAvgDay;
    private TextView txtGenerateItineraryTitle, txtGenerateItinerarySub;

    private TextView txtTemp, txtWeatherCondition;

    private String currentCityId;
    private String currentCountryId;

    // Cheia ta de la OpenWeatherMap
    private final String WEATHER_API_KEY = "8b797ca0d5266ca229d83febd665dbc7";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_city_detail);

        db = FirebaseFirestore.getInstance();

        currentCityId = getIntent().getStringExtra("cityId");
        currentCountryId = getIntent().getStringExtra("countryId");

        if (currentCityId == null) currentCityId = "rome";
        if (currentCountryId == null) currentCountryId = "italy";

        txtCityName = findViewById(R.id.txtCityName);
        txtCountryLabel = findViewById(R.id.txtCountryLabel);
        txtCityRating = findViewById(R.id.txtCityRating);
        txtCityTagline = findViewById(R.id.txtCityTagline);
        txtCurrency = findViewById(R.id.txtCurrency);
        txtLanguage = findViewById(R.id.txtLanguage);
        txtAvgDay = findViewById(R.id.txtAvgDay);

        txtGenerateItineraryTitle = findViewById(R.id.txtGenerateItineraryTitle);
        txtGenerateItinerarySub = findViewById(R.id.txtGenerateItinerarySub);

        txtTemp = findViewById(R.id.txtTemp);
        txtWeatherCondition = findViewById(R.id.txtWeatherCondition);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.cityDetailRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    findViewById(R.id.appBarLayout).setPadding(0, systemBars.top, 0, 0);
                    v.setPadding(0, 0, 0, systemBars.bottom);
                    return insets;
                });

        loadCityDetails();
        loadTopAttractions();
        loadTopRestaurants();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPlanTrip).setOnClickListener(v -> {
            startActivity(new Intent(CityDetailActivity.this, PlanTripActivity.class));
        });

        findViewById(R.id.tvViewAllAttractions).setOnClickListener(v -> {
            Intent intent = new Intent(CityDetailActivity.this, AllItemsActivity.class);
            intent.putExtra("CATEGORY_TYPE", "attractions");
            intent.putExtra("countryId", currentCountryId);
            intent.putExtra("cityId", currentCityId);
            startActivity(intent);
        });

        findViewById(R.id.tvViewAllRestaurants).setOnClickListener(v -> {
            Intent intent = new Intent(CityDetailActivity.this, AllItemsActivity.class);
            intent.putExtra("CATEGORY_TYPE", "restaurants");
            intent.putExtra("countryId", currentCountryId);
            intent.putExtra("cityId", currentCityId);
            startActivity(intent);
        });
    }

    private void loadCityDetails() {
        db.collection("countries").document(currentCountryId)
                .collection("cities").document(currentCityId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        City city = task.getResult().toObject(City.class);

                        if (city != null) {
                            if (city.getName() != null && txtCityName != null) {
                                txtCityName.setText(city.getName());

                                if (txtGenerateItineraryTitle != null) {
                                    txtGenerateItineraryTitle.setText("Generate a " + city.getName() + " itinerary");
                                }

                                if (txtGenerateItinerarySub != null) {
                                    txtGenerateItinerarySub.setText("Let the AI build your perfect days in " + city.getName());
                                }
                            }

                            if (city.getCountryId() != null && txtCountryLabel != null) {
                                String countryFormatted = city.getCountryId().substring(0, 1).toUpperCase()
                                        + city.getCountryId().substring(1);
                                txtCountryLabel.setText(countryFormatted);
                            }

                            if (city.getRating() != null && txtCityRating != null) {
                                txtCityRating.setText("☆ " + city.getRating());
                            }

                            if (city.getTagline() != null && txtCityTagline != null) {
                                txtCityTagline.setText("· " + city.getTagline());
                            }

                            if (city.getCurrency() != null && txtCurrency != null) {
                                txtCurrency.setText(city.getCurrency());
                            }

                            if (city.getLanguage() != null && txtLanguage != null) {
                                txtLanguage.setText(city.getLanguage());
                            }

                            if (city.getAvgDayBudget() != null && txtAvgDay != null) {
                                txtAvgDay.setText("~€" + city.getAvgDayBudget());
                            }

                            if (city.getLat() != null && city.getLon() != null) {
                                fetchWeatherData(city.getLat(), city.getLon());
                            } else if (txtWeatherCondition != null) {
                                txtWeatherCondition.setText("No location data");
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error loading city data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Incarca primele 2 atractii si le pune in cardurile Colosseum/Vatican Museums. */
    private void loadTopAttractions() {
        PoiRepository poiRepository = new PoiRepository();
        poiRepository.getAttractions(currentCountryId, currentCityId, new PoiRepository.AttractionListCallback() {
            @Override
            public void onSuccess(List<Attraction> attractions) {
                ImageView img1 = findViewById(R.id.imgColosseum);
                TextView name1 = findViewById(R.id.txtAttraction1Name);
                TextView sub1 = findViewById(R.id.txtAttraction1Subtitle);
                ImageView btn1 = findViewById(R.id.btnTicket1);

                ImageView img2 = findViewById(R.id.imgVatican);
                TextView name2 = findViewById(R.id.txtAttraction2Name);
                TextView sub2 = findViewById(R.id.txtAttraction2Subtitle);
                ImageView btn2 = findViewById(R.id.btnTicket2);

                if (attractions.size() > 0) bindAttraction(attractions.get(0), img1, name1, sub1, btn1);
                if (attractions.size() > 1) bindAttraction(attractions.get(1), img2, name2, sub2, btn2);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(CityDetailActivity.this,
                        "Couldn't load attractions: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Incarca primele 2 restaurante si le pune in cardurile Roscioli/Da Enzo. */
    private void loadTopRestaurants() {
        PoiRepository poiRepository = new PoiRepository();
        poiRepository.getRestaurants(currentCountryId, currentCityId, new PoiRepository.RestaurantListCallback() {
            @Override
            public void onSuccess(List<Restaurant> restaurants) {
                ImageView img1 = findViewById(R.id.imgRoscioli);
                TextView name1 = findViewById(R.id.txtRestaurant1Name);
                TextView sub1 = findViewById(R.id.txtRestaurant1Subtitle);
                TextView price1 = findViewById(R.id.priceRoscioli);

                ImageView img2 = findViewById(R.id.imgDaEnzo);
                TextView name2 = findViewById(R.id.txtRestaurant2Name);
                TextView sub2 = findViewById(R.id.txtRestaurant2Subtitle);
                TextView price2 = findViewById(R.id.priceDaEnzo);

                if (restaurants.size() > 0) bindRestaurant(restaurants.get(0), img1, name1, sub1, price1);
                if (restaurants.size() > 1) bindRestaurant(restaurants.get(1), img2, name2, sub2, price2);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(CityDetailActivity.this,
                        "Couldn't load restaurants: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Pune datele reale ale unei atractii pe card, si leaga iconita de bilet/info la link. */
    private void bindAttraction(Attraction a, ImageView img, TextView name, TextView subtitle, ImageView ticketBtn) {
        if (name != null) name.setText(a.getName());
        if (subtitle != null) subtitle.setText(PoiRow.fromAttraction(a).subtitle);
        if (img != null) {
            int resId = 0;
            if (a.getId() != null) resId = getResources().getIdentifier(a.getId(), "drawable", getPackageName());
            img.setImageResource(resId != 0 ? resId : R.drawable.rome);
        }

        // DOAR iconita de bilet deschide link-ul, nu tot cardul
        if (ticketBtn != null) {
            if (a.getTicketUrl() != null && !a.getTicketUrl().isEmpty()) {
                ticketBtn.setImageResource(R.drawable.ic_ticket);
                ticketBtn.setOnClickListener(v -> openLink(a.getTicketUrl()));
            } else if (a.getInfoUrl() != null && !a.getInfoUrl().isEmpty()) {
                ticketBtn.setImageResource(R.drawable.ic_info);
                ticketBtn.setOnClickListener(v -> openLink(a.getInfoUrl()));
            } else {
                ticketBtn.setOnClickListener(null);
            }
        }
    }

    private void bindRestaurant(Restaurant r, ImageView img, TextView name, TextView subtitle, TextView price) {
        if (name != null) name.setText(r.getName());
        if (subtitle != null) subtitle.setText(PoiRow.fromRestaurant(r).subtitle);
        if (price != null) price.setText(r.getPriceRange() != null ? r.getPriceRange() : "");
        if (img != null) {
            int resId = 0;
            if (r.getId() != null) resId = getResources().getIdentifier(r.getId(), "drawable", getPackageName());
            img.setImageResource(resId != 0 ? resId : R.drawable.rome);
        }
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    /** Emoji corespunzator conditiei meteo principale returnate de OpenWeatherMap. */
    private String getWeatherEmoji(String condition) {
        if (condition == null) return "🌤️";
        switch (condition) {
            case "Clear": return "☀️";
            case "Clouds": return "☁️";
            case "Rain":
            case "Drizzle": return "🌧️";
            case "Thunderstorm": return "⛈️";
            case "Snow": return "❄️";
            case "Mist":
            case "Fog":
            case "Haze": return "🌫️";
            default: return "🌤️";
        }
    }

    /**
     * Cere vremea pe baza de coordonate exacte (lat/lon), nu nume de oras -
     * elimina complet orice ambiguitate, indiferent cate tari noi adaugi in Firestore.
     */
    private void fetchWeatherData(double lat, double lon) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String urlString = String.format(Locale.US,
                        "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric",
                        lat, lon, WEATHER_API_KEY);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONObject mainObject = jsonResponse.getJSONObject("main");
                    int temperature = (int) Math.round(mainObject.getDouble("temp"));

                    String weatherDescription = jsonResponse.getJSONArray("weather")
                            .getJSONObject(0).getString("main");
                    String emoji = getWeatherEmoji(weatherDescription);

                    handler.post(() -> {
                        if (txtTemp != null) {
                            txtTemp.setText(temperature + "°");
                        }
                        if (txtWeatherCondition != null) {
                            txtWeatherCondition.setText(emoji + " " + weatherDescription);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (txtWeatherCondition != null) {
                        txtWeatherCondition.setText("Offline");
                    }
                });
            }
        });
    }
}