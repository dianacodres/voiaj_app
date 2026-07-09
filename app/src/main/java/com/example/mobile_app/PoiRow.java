package com.example.mobile_app;

/**
 * Model unificat pentru afisarea unui rand in bottom sheet-ul de selectie
 * (fie atractie, fie restaurant) - evita duplicarea adapterului.
 */
public class PoiRow {
    final String id;
    final String name;
    final String imageUrl;
    final String subtitle; // ex: "2-3h · €18" pentru atractii, "Italian · €30" pentru restaurante

    private PoiRow(String id, String name, String imageUrl, String subtitle) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.subtitle = subtitle;
    }

    static PoiRow fromAttraction(Attraction a) {
        String subtitle = (a.getDuration() != null ? a.getDuration() : "")
                + (a.getTicketPrice() > 0 ? " · €" + a.getTicketPrice() : "");
        return new PoiRow(a.getId(), a.getName(), a.getImageUrl(), subtitle.trim());
    }

    static PoiRow fromRestaurant(Restaurant r) {
        String category = r.getCuisine() != null ? r.getCuisine() : "";
        String ratingText = r.getRating() != null ? "⭐ " + r.getRating() : "";
        String subtitle = category + (category.isEmpty() || ratingText.isEmpty() ? "" : " · ") + ratingText;
        return new PoiRow(r.getId(), r.getName(), r.getImageUrl(), subtitle.trim());
    }
}