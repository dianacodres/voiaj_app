package com.example.mobile_app;

/**
 * Model unificat pentru afisarea unui rand in bottom sheet-ul de selectie
 * (fie atractie, fie restaurant) - evita duplicarea adapterului.
 */
public class PoiRow {
    final String id;
    final String name;
    final String imageUrl; // Păstrat final și integrat asincron în constructor
    final String subtitle; // ex: "2-3h · €18" pentru atractii, "Italian · €30" pentru restaurante
    final String url;      // ticketUrl (platit) sau infoUrl (gratuit) - null daca nu exista niciunul
    final boolean isTicket; // true = "Buy tickets", false = "More info" (pentru afisarea corecta a iconitei)

    private PoiRow(String id, String name, String imageUrl, String subtitle, String url, boolean isTicket) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.subtitle = subtitle;
        this.url = url;
        this.isTicket = isTicket;
    }

    static PoiRow fromAttraction(Attraction a) {
        String subtitle = (a.getDuration() != null ? a.getDuration() : "")
                + (a.getTicketPrice() > 0 ? " · €" + a.getTicketPrice() : "");

        // Daca e platita si are link de bilete, il folosim; altfel, daca e gratuita,
        // folosim link-ul informativ (daca exista) - niciodata amandoua deodata.
        String url = null;
        boolean isTicket = false;
        if (a.getTicketUrl() != null && !a.getTicketUrl().isEmpty()) {
            url = a.getTicketUrl();
            isTicket = true;
        } else if (a.getInfoUrl() != null && !a.getInfoUrl().isEmpty()) {
            url = a.getInfoUrl();
            isTicket = false;
        }

        // Preluăm corect a.getImageUrl() din baza de date
        return new PoiRow(a.getId(), a.getName(), a.getImageUrl(), subtitle.trim(), url, isTicket);
    }

    static PoiRow fromRestaurant(Restaurant r) {
        String neighborhood = r.getNeighborhood() != null ? r.getNeighborhood() : "";
        String category = "";
        if (r.getCategory() != null && !r.getCategory().isEmpty()) {
            category = Character.toUpperCase(r.getCategory().charAt(0)) + r.getCategory().substring(1);
        }
        String subtitle = neighborhood + (neighborhood.isEmpty() || category.isEmpty() ? "" : " · ") + category;

        // Preluăm corect r.getImageUrl() din baza de date
        return new PoiRow(r.getId(), r.getName(), r.getImageUrl(), subtitle.trim(), null, false);
    }
}