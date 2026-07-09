package com.example.mobile_app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculeaza procentul de "match" intre interesele alese de utilizator la onboarding
 * (beaches, food, cities, mountains, culture, nature) si keywords-urile reale ale
 * fiecarui oras din Firestore (historical, sun&beach, food&wine, cultural, nature,
 * urbanism, art, museums, budget) - cele doua liste nu coincid ca text, de-aia exista
 * maparea de mai jos.
 */
public class RecommendationEngine {

    private static final Map<String, List<String>> INTEREST_TO_KEYWORDS = new HashMap<>();

    static {
        INTEREST_TO_KEYWORDS.put("beaches", Arrays.asList("sun&beach"));
        INTEREST_TO_KEYWORDS.put("food", Arrays.asList("food&wine"));
        INTEREST_TO_KEYWORDS.put("cities", Arrays.asList("urbanism"));
        INTEREST_TO_KEYWORDS.put("mountains", Arrays.asList("nature"));
        INTEREST_TO_KEYWORDS.put("culture", Arrays.asList("cultural", "historical", "art", "museums"));
        INTEREST_TO_KEYWORDS.put("nature", Arrays.asList("nature"));
    }

    /** Un oras + procentul de match calculat pentru interesele unui anumit user. */
    public static class CityMatch {
        public final City city;
        public final int matchPercent;

        public CityMatch(City city, int matchPercent) {
            this.city = city;
            this.matchPercent = matchPercent;
        }
    }

    /**
     * % match = (cate keywords ale orasului se regasesc in interesele mapate ale userului)
     * / (total keywords ale orasului) x 100.
     */
    public static int computeMatchPercent(List<String> userInterests, List<String> cityKeywords) {
        if (cityKeywords == null || cityKeywords.isEmpty()) return 0;

        Set<String> mappedKeywords = new HashSet<>();
        if (userInterests != null) {
            for (String interest : userInterests) {
                List<String> mapped = INTEREST_TO_KEYWORDS.get(interest);
                if (mapped != null) mappedKeywords.addAll(mapped);
            }
        }

        int matched = 0;
        for (String keyword : cityKeywords) {
            if (mappedKeywords.contains(keyword)) matched++;
        }

        return (int) Math.round((matched * 100.0) / cityKeywords.size());
    }

    /**
     * Calculeaza match-ul pentru toate orasele, elimina cele cu 0% (nu au nicio
     * legatura cu interesele userului) si sorteaza descrescator restul dupa procent.
     */
    public static List<CityMatch> rankCities(List<String> userInterests, List<City> cities) {
        List<CityMatch> matches = new ArrayList<>();
        for (City city : cities) {
            int percent = computeMatchPercent(userInterests, city.getKeywords());
            if (percent > 0) {
                matches.add(new CityMatch(city, percent));
            }
        }
        matches.sort((a, b) -> Integer.compare(b.matchPercent, a.matchPercent));
        return matches;
    }
}