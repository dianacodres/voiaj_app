package com.example.mobile_app.ai;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cere vremea de la OpenWeatherMap, pe baza de lat/lon (acelasi API ca in CityDetailActivity,
 * extras aici ca sa fie reutilizabil si din chat-ul AI, fara sa duplicam codul).
 */
public class WeatherHelper {

    private static final String WEATHER_API_KEY = "8b797ca0d5266ca229d83febd665dbc7";

    public interface WeatherCallback {
        void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil);
        void onError(String mesajEroare);
    }

    /** Vremea de ACUM (folosita cand nu avem o data tinta specifica). */
    public static void fetchWeather(double lat, double lon, WeatherCallback callback) {
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

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String linie;
                    while ((linie = in.readLine()) != null) response.append(linie);
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    int temperatura = (int) Math.round(json.getJSONObject("main").getDouble("temp"));
                    String conditie = json.getJSONArray("weather").getJSONObject(0).getString("main");
                    String emoji = getEmoji(conditie);
                    boolean ploua = esteConditieDePloaie(conditie);

                    handler.post(() -> callback.onSuccess(temperatura, emoji + " " + conditie, ploua));
                } else {
                    handler.post(() -> callback.onError("Weather service unavailable"));
                }
            } catch (Exception e) {
                handler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Prognoza pentru o zi VIITOARE specifica (pana la 5 zile inainte - limita API-ului gratuit).
     * zileInainte = 0 inseamna azi, 1 = maine, etc.
     */
    public static void fetchForecast(double lat, double lon, int zileInainte, WeatherCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String urlString = String.format(Locale.US,
                        "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric",
                        lat, lon, WEATHER_API_KEY);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String linie;
                    while ((linie = in.readLine()) != null) response.append(linie);
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    org.json.JSONArray listaOre = json.getJSONArray("list");

                    // Data tinta (azi + zileInainte), formatata la fel ca timestamp-urile OpenWeatherMap
                    java.util.Calendar tinta = java.util.Calendar.getInstance();
                    tinta.add(java.util.Calendar.DAY_OF_YEAR, zileInainte);
                    java.text.SimpleDateFormat sdfZi = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    String ziTinta = sdfZi.format(tinta.getTime());

                    // Cautam intrarea cea mai apropiata de ora 12:00 din ziua tinta (reprezentativa pentru "ziua")
                    JSONObject celMaiPotrivit = null;
                    int celMaiMicDiff = Integer.MAX_VALUE;

                    for (int i = 0; i < listaOre.length(); i++) {
                        JSONObject intrare = listaOre.getJSONObject(i);
                        String dtTxt = intrare.getString("dt_txt"); // "2026-07-20 12:00:00"
                        if (!dtTxt.startsWith(ziTinta)) continue;

                        int ora = Integer.parseInt(dtTxt.substring(11, 13));
                        int diff = Math.abs(ora - 12);
                        if (diff < celMaiMicDiff) {
                            celMaiMicDiff = diff;
                            celMaiPotrivit = intrare;
                        }
                    }

                    if (celMaiPotrivit == null) {
                        handler.post(() -> callback.onError("No forecast available for that date"));
                        return;
                    }

                    int temperatura = (int) Math.round(celMaiPotrivit.getJSONObject("main").getDouble("temp"));
                    String conditie = celMaiPotrivit.getJSONArray("weather").getJSONObject(0).getString("main");
                    String emoji = getEmoji(conditie);
                    boolean ploua = esteConditieDePloaie(conditie);

                    JSONObject finalCelMaiPotrivit = celMaiPotrivit;
                    handler.post(() -> callback.onSuccess(temperatura, emoji + " " + conditie, ploua));
                } else {
                    handler.post(() -> callback.onError("Forecast service unavailable"));
                }
            } catch (Exception e) {
                handler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private static boolean esteConditieDePloaie(String conditie) {
        return "Rain".equals(conditie) || "Drizzle".equals(conditie) || "Thunderstorm".equals(conditie);
    }

    /**
     * Metoda "smart": daca zileInainte e in intervalul 0-5 (limita API-ului gratuit),
     * foloseste prognoza reala pentru acea zi; altfel, foloseste vremea de ACUM,
     * ca referinta generala (si semnaleaza asta prin approximativ=true).
     */
    public interface SmartWeatherCallback {
        void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil, boolean approximativ);
        void onError(String mesajEroare);
    }

    public static void fetchWeatherSmart(double lat, double lon, int zileInainte, SmartWeatherCallback callback) {
        if (zileInainte >= 0 && zileInainte <= 5) {
            fetchForecast(lat, lon, zileInainte, new WeatherCallback() {
                @Override
                public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil) {
                    callback.onSuccess(temperatura, conditieCuEmoji, plouaProbabil, false);
                }

                @Override
                public void onError(String mesajEroare) {
                    callback.onError(mesajEroare);
                }
            });
        } else {
            fetchWeather(lat, lon, new WeatherCallback() {
                @Override
                public void onSuccess(int temperatura, String conditieCuEmoji, boolean plouaProbabil) {
                    callback.onSuccess(temperatura, conditieCuEmoji, plouaProbabil, true);
                }

                @Override
                public void onError(String mesajEroare) {
                    callback.onError(mesajEroare);
                }
            });
        }
    }

    private static String getEmoji(String conditie) {
        if (conditie == null) return "🌤️";
        switch (conditie) {
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
}