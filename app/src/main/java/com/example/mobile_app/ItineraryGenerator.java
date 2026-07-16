package com.example.mobile_app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Genereaza un itinerariu pe zile, folosind reguli Java simple (fara AI):
 *   - grupare pe zone (proximitate), o zi = o zona (sau doua apropiate)
 *   - ordonare in zi dupa routeOrder + bestTimeOfDay
 *   - limita de ~6-7 ore de vizitat/zi (visitDurationMinutes), nu numar fix
 *   - target 3 atractii + 3 opriri de mancare/zi
 *   - restaurante alese pe mealTime/mealOrder, din aceeasi zona
 *   - completare cu ce ramane (parcuri, cafenele) daca o zona n-are destule
 *   - ce a ales userul manual (din picker) e garantat inclus
 */
public class ItineraryGenerator {

    private static final int TINTA_ATRACTII_PE_ZI = 3;
    private static final int TINTA_MESE_PE_ZI = 3;
    private static final int LIMITA_MINUTE_VIZITAT_PE_ZI = 420; // ~7 ore

    public interface ItineraryCallback {
        void onSuccess(List<ItineraryItem> itinerariu);
        void onError(String errorMessage);
    }

    /**
     * @param selectedAttractionIds atractiile alese manual de user (poate fi gol - atunci alegem noi tot)
     * @param selectedRestaurantIds la fel, pentru restaurante
     */
    public static void genereaza(String countryId, String cityId, int daysCount,
                                 Set<String> selectedAttractionIds, Set<String> selectedRestaurantIds,
                                 ItineraryCallback callback) {

        PoiRepository poiRepository = new PoiRepository();

        poiRepository.getAttractions(countryId, cityId, new PoiRepository.AttractionListCallback() {
            @Override
            public void onSuccess(List<Attraction> toateAtractiile) {
                poiRepository.getRestaurants(countryId, cityId, new PoiRepository.RestaurantListCallback() {
                    @Override
                    public void onSuccess(List<Restaurant> toateRestaurantele) {
                        try {
                            List<ItineraryItem> rezultat = construieste(
                                    toateAtractiile, toateRestaurantele,
                                    daysCount, selectedAttractionIds, selectedRestaurantIds);
                            callback.onSuccess(rezultat);
                        } catch (Exception e) {
                            callback.onError("Couldn't generate itinerary: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        callback.onError(errorMessage);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    private static List<ItineraryItem> construieste(
            List<Attraction> toateAtractiile, List<Restaurant> toateRestaurantele,
            int daysCount, Set<String> selectedAttractionIds, Set<String> selectedRestaurantIds) {

        List<ItineraryItem> itinerariu = new ArrayList<>();

        // Daca userul a ales manual ceva, folosim DOAR alea ca "pool" de unde alegem;
        // altfel, alegem noi din toate atractiile/restaurantele orasului.
        List<Attraction> poolAtractii = filtreazaAtractiiDupaSelectie(toateAtractiile, selectedAttractionIds);
        List<Restaurant> poolRestaurante = filtreazaRestauranteDupaSelectie(toateRestaurantele, selectedRestaurantIds);

        // Grupam atractiile pe zona
        Map<String, List<Attraction>> atractiiPeZona = new HashMap<>();
        for (Attraction a : poolAtractii) {
            String zona = a.getZone() != null ? a.getZone() : "unknown";
            atractiiPeZona.computeIfAbsent(zona, k -> new ArrayList<>()).add(a);
        }

        // Ordonam zonele dupa "bogatie" (cate atractii are) - zonele cele mai bogate devin primele zile
        List<String> zoneOrdonate = new ArrayList<>(atractiiPeZona.keySet());
        zoneOrdonate.sort((z1, z2) -> Integer.compare(atractiiPeZona.get(z2).size(), atractiiPeZona.get(z1).size()));

        Set<String> atractiiFolosite = new HashSet<>();
        Set<String> restauranteFolosite = new HashSet<>();

        for (int zi = 1; zi <= daysCount; zi++) {
            String zonaZilei = (zi - 1 < zoneOrdonate.size()) ? zoneOrdonate.get(zi - 1) : null;

            List<Attraction> candidatiZi = new ArrayList<>();
            if (zonaZilei != null) {
                candidatiZi.addAll(atractiiPeZona.get(zonaZilei));
            }

            // Sortam dupa routeOrder, dar mutam "evening/sunset" spre finalul zilei
            candidatiZi.sort(comparatorAtractii());

            List<Attraction> alesePentruZi = new ArrayList<>();
            int minuteAcumulate = 0;
            for (Attraction a : candidatiZi) {
                if (atractiiFolosite.contains(a.getId())) continue;
                if (alesePentruZi.size() >= TINTA_ATRACTII_PE_ZI) break;
                int durata = a.getVisitDurationMinutes() > 0 ? a.getVisitDurationMinutes() : 60;
                if (!alesePentruZi.isEmpty() && minuteAcumulate + durata > LIMITA_MINUTE_VIZITAT_PE_ZI) break;
                alesePentruZi.add(a);
                minuteAcumulate += durata;
                atractiiFolosite.add(a.getId());
            }

            // Daca zona n-a avut destule, completam cu ce ramane - preferam parcuri/aer liber
            if (alesePentruZi.size() < TINTA_ATRACTII_PE_ZI) {
                List<Attraction> rezerva = new ArrayList<>();
                for (Attraction a : poolAtractii) {
                    if (!atractiiFolosite.contains(a.getId())) rezerva.add(a);
                }
                rezerva.sort((a1, a2) -> Boolean.compare(!estePotrivitCaRezerva(a2), !estePotrivitCaRezerva(a1)));

                for (Attraction a : rezerva) {
                    if (alesePentruZi.size() >= TINTA_ATRACTII_PE_ZI) break;
                    alesePentruZi.add(a);
                    atractiiFolosite.add(a.getId());
                }
            }

            // Reordonam pe TRASEU real (cea mai apropiata urmatoare, dupa lat/lon),
            // nu doar dupa routeOrder - un traseu de mers pe jos logic, nu sarituri aiurea.
            alesePentruZi = ordoneazaDupaProximitate(alesePentruZi);

            for (Attraction a : alesePentruZi) {
                itinerariu.add(new ItineraryItem(a.getId(), "attraction", zi));
            }

            // Restaurante - alese dupa DISTANTA reala fata de atractia din apropiere
            // (cea vizitata inainte, sau urmatoarea), nu doar din aceeasi zona generica.
            List<Restaurant> restauranteZona = new ArrayList<>();
            for (Restaurant r : poolRestaurante) {
                String zonaR = r.getZone() != null ? r.getZone() : "unknown";
                if (zonaZilei == null || zonaR.equals(zonaZilei)) restauranteZona.add(r);
            }

            String[] antici = {"breakfast", "brunch"};
            String[] amiaza = {"lunch"};
            String[] seara = {"dinner"};

            // Ancore geografice: mic dejun langa prima atractie, pranz langa a doua
            // (sau prima, daca nu-s destule), cina langa ultima atractie a zilei.
            Attraction ancoraDimineata = !alesePentruZi.isEmpty() ? alesePentruZi.get(0) : null;
            Attraction ancoraPranz = alesePentruZi.size() > 1 ? alesePentruZi.get(1) : ancoraDimineata;
            Attraction ancoraSeara = !alesePentruZi.isEmpty() ? alesePentruZi.get(alesePentruZi.size() - 1) : null;

            int meseAlese = 0;
            meseAlese += adaugaMasaProximitate(itinerariu, restauranteZona, restauranteFolosite, antici, zi, ancoraDimineata);
            if (meseAlese < TINTA_MESE_PE_ZI)
                meseAlese += adaugaMasaProximitate(itinerariu, restauranteZona, restauranteFolosite, amiaza, zi, ancoraPranz);
            if (meseAlese < TINTA_MESE_PE_ZI)
                meseAlese += adaugaMasaProximitate(itinerariu, restauranteZona, restauranteFolosite, seara, zi, ancoraSeara);

            // Daca tot n-avem 3 mese (zona saraca), completam cu orice restaurant neutilizat din tot orasul
            if (meseAlese < TINTA_MESE_PE_ZI) {
                for (Restaurant r : poolRestaurante) {
                    if (meseAlese >= TINTA_MESE_PE_ZI) break;
                    if (restauranteFolosite.contains(r.getId())) continue;
                    itinerariu.add(new ItineraryItem(r.getId(), "restaurant", zi));
                    restauranteFolosite.add(r.getId());
                    meseAlese++;
                }
            }
        }

        return itinerariu;
    }

    /**
     * Reordoneaza atractiile ca un traseu real: pornim de la cea cu routeOrder cel mai mic,
     * apoi alegem mereu cea mai APROPIATA geografic ramasa (nearest-neighbor).
     * Atractiile "de seara" raman garantat la final, indiferent de distanta.
     */
    private static List<Attraction> ordoneazaDupaProximitate(List<Attraction> atractii) {
        if (atractii.size() <= 1) return atractii;

        List<Attraction> deSeara = new ArrayList<>();
        List<Attraction> restul = new ArrayList<>();
        for (Attraction a : atractii) {
            if (esteDeSeara(a)) deSeara.add(a); else restul.add(a);
        }

        List<Attraction> traseu = new ArrayList<>();
        if (!restul.isEmpty()) {
            restul.sort(Comparator.comparingInt(Attraction::getRouteOrder));
            Attraction curent = restul.remove(0);
            traseu.add(curent);
            while (!restul.isEmpty()) {
                Attraction ceaMaiApropiata = gasesteCeaMaiApropiata(curent, restul);
                traseu.add(ceaMaiApropiata);
                restul.remove(ceaMaiApropiata);
                curent = ceaMaiApropiata;
            }
        }
        traseu.addAll(deSeara);
        return traseu;
    }

    private static Attraction gasesteCeaMaiApropiata(Attraction dela, List<Attraction> candidati) {
        Attraction celMaiApropiat = candidati.get(0);
        double distantaMinima = distantaIntre(dela.getLat(), dela.getLon(), celMaiApropiat.getLat(), celMaiApropiat.getLon());
        for (Attraction c : candidati) {
            double d = distantaIntre(dela.getLat(), dela.getLon(), c.getLat(), c.getLon());
            if (d < distantaMinima) {
                distantaMinima = d;
                celMaiApropiat = c;
            }
        }
        return celMaiApropiat;
    }

    /** Alege restaurantul cel mai apropiat (dupa lat/lon) de o "ancora" (atractia vizitata inainte/dupa), din cei cu mealTime potrivit. */
    private static int adaugaMasaProximitate(List<ItineraryItem> itinerariu, List<Restaurant> restauranteZona,
                                             Set<String> restauranteFolosite, String[] mealTimeCautat, int zi, Attraction ancora) {
        Restaurant celMaiApropiat = null;
        double distantaMinima = Double.MAX_VALUE;

        for (Restaurant r : restauranteZona) {
            if (restauranteFolosite.contains(r.getId())) continue;
            if (r.getMealTime() == null) continue;

            boolean sePotriveste = false;
            for (String mt : mealTimeCautat) {
                if (r.getMealTime().contains(mt)) { sePotriveste = true; break; }
            }
            if (!sePotriveste) continue;

            double distanta = (ancora != null)
                    ? distantaIntre(ancora.getLat(), ancora.getLon(), r.getLat(), r.getLon())
                    : r.getMealOrder(); // fara ancora (nicio atractie in zi) - cadem pe mealOrder

            if (distanta < distantaMinima) {
                distantaMinima = distanta;
                celMaiApropiat = r;
            }
        }

        if (celMaiApropiat != null) {
            itinerariu.add(new ItineraryItem(celMaiApropiat.getId(), "restaurant", zi));
            restauranteFolosite.add(celMaiApropiat.getId());
            return 1;
        }
        return 0;
    }

    /** Distanta reala (Haversine, in km) intre 2 puncte. Daca lipsesc coordonate, intoarce o valoare mare (nu blocheaza algoritmul). */
    private static double distantaIntre(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return 999999;
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /** Daca userul a ales manual ceva, folosim DOAR alea; altfel, tot poolul (alegem noi). */
    private static List<Attraction> filtreazaAtractiiDupaSelectie(List<Attraction> toate, Set<String> selectate) {
        if (selectate == null || selectate.isEmpty()) return new ArrayList<>(toate);
        List<Attraction> rezultat = new ArrayList<>();
        for (Attraction a : toate) if (selectate.contains(a.getId())) rezultat.add(a);
        return rezultat;
    }


    private static List<Restaurant> filtreazaRestauranteDupaSelectie(List<Restaurant> toate, Set<String> selectate) {
        if (selectate == null || selectate.isEmpty()) return new ArrayList<>(toate);
        List<Restaurant> rezultat = new ArrayList<>();
        for (Restaurant r : toate) if (selectate.contains(r.getId())) rezultat.add(r);
        return rezultat;
    }


    /** Ordoneaza dupa routeOrder, dar muta atractiile "de seara/apus" spre finalul zilei. */
    private static Comparator<Attraction> comparatorAtractii() {
        return (a1, a2) -> {
            boolean seara1 = esteDeSeara(a1);
            boolean seara2 = esteDeSeara(a2);
            if (seara1 != seara2) return Boolean.compare(seara1, seara2); // cele de seara, la coada
            return Integer.compare(a1.getRouteOrder(), a2.getRouteOrder());
        };
    }


    private static boolean esteDeSeara(Attraction a) {
        String bt = a.getBestTimeOfDay();
        return bt != null && (bt.equalsIgnoreCase("evening") || bt.equalsIgnoreCase("sunset") || bt.equalsIgnoreCase("night"));
    }


    /** Pentru completare de rezerva - preferam parcuri/natura/aer liber, cand o zona n-are destule atractii. */
    private static boolean estePotrivitCaRezerva(Attraction a) {
        if (a.getCategory() != null) {
            String c = a.getCategory().toLowerCase();
            if (c.contains("park") || c.contains("nature") || c.contains("garden")) return true;
        }
        if (a.getTags() != null) {
            for (String tag : a.getTags()) {
                if (tag != null && tag.toLowerCase().contains("outdoor")) return true;
            }
        }
        return false;
    }
}