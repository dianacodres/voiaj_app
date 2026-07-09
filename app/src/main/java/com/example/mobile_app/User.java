package com.example.mobile_app;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class User {

    @DocumentId
    private String uid; // populat automat de Firestore cu id-ul documentului

    private String name;
    private String email;
    private String memberSince;      // ex: "2024"
    private String authProvider;     // "email" sau "google"
    private String photoUrl;         // nullable, poza reala din Storage
    private String avatarInitials;   // ex: "AP", derivat din name

    private List<String> interests;  // subset din: beaches, food, cities, mountains, culture, nature

    private int completedTripsCount;
    private int savedTripsCount;
    private boolean pushNotifications;

    @ServerTimestamp
    private Date createdAt; // Firestore completeaza automat cu timpul serverului la creare

    // Constructor gol - OBLIGATORIU pentru Firestore (toObject are nevoie de el)
    public User() {
        this.interests = new ArrayList<>();
        this.pushNotifications = true;
        this.completedTripsCount = 0;
        this.savedTripsCount = 0;
    }

    // Constructor folosit la signup, cand cream documentul prima data
    public User(String name, String email, String authProvider, List<String> interests) {
        this();
        this.name = name;
        this.email = email;
        this.authProvider = authProvider;
        this.interests = interests;
        this.memberSince = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        this.avatarInitials = computeInitials(name);
    }

    // Helper: "Andrei Pop" -> "AP"
    public static String computeInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length && i < 2; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return initials.toString();
    }

    // --- Getters & Setters ---

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.avatarInitials = computeInitials(name);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMemberSince() {
        return memberSince;
    }

    public void setMemberSince(String memberSince) {
        this.memberSince = memberSince;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getAvatarInitials() {
        return avatarInitials;
    }

    public void setAvatarInitials(String avatarInitials) {
        this.avatarInitials = avatarInitials;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public int getCompletedTripsCount() {
        return completedTripsCount;
    }

    public void setCompletedTripsCount(int completedTripsCount) {
        this.completedTripsCount = completedTripsCount;
    }

    public int getSavedTripsCount() {
        return savedTripsCount;
    }

    public void setSavedTripsCount(int savedTripsCount) {
        this.savedTripsCount = savedTripsCount;
    }

    public boolean isPushNotifications() {
        return pushNotifications;
    }

    public void setPushNotifications(boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
