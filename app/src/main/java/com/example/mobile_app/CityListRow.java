package com.example.mobile_app;

/** Un rand din lista de orase: fie un header de grupare (numele tarii), fie un oras selectabil. */
class CityListRow {
    static final int TYPE_HEADER = 0;
    static final int TYPE_CITY = 1;

    final int type;
    final String headerText;
    final City city;

    private CityListRow(int type, String headerText, City city) {
        this.type = type;
        this.headerText = headerText;
        this.city = city;
    }

    static CityListRow header(String text) {
        return new CityListRow(TYPE_HEADER, text, null);
    }

    static CityListRow cityRow(City city) {
        return new CityListRow(TYPE_CITY, null, city);
    }
}