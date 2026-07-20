package com.example.mobile_app;

import android.content.Context;
import android.content.SharedPreferences;

public class AuthScreenPreference {

    private static final String PREFS_NAME = "auth_prefs";
    private static final String KEY_LAST_SCREEN = "last_auth_screen";

    public static final String SCREEN_MAIN = "main";
    public static final String SCREEN_LOGIN = "login";
    public static final String SCREEN_SIGNUP = "signup";

    public static void saveLastScreen(Context context, String screen) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_SCREEN, screen).apply();
    }

    public static String getLastScreen(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SCREEN, SCREEN_MAIN);
    }
}