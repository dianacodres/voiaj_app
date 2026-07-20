package com.example.mobile_app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.List;

public class TripNotificationWorker extends Worker {

    public TripNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();

        // Preluăm sincron călătoriile din Firestore
        new TripRepository().getSavedTrips(user.getUid(), new TripRepository.TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                for (Trip trip : savedTrips) {
                    if (!"planned".equals(trip.getStatus()) || trip.hasEnded()) continue;

                    trip.recalculateCountdown();
                    int daysLeft = trip.getDaysUntilTrip();
                    String city = trip.getCityName();

                    if (daysLeft == 0) {
                        NotificationHelper.sendNotification(getApplicationContext(), trip.hashCode(),
                                NotificationHelper.CHANNEL_TRIPS,
                                "Today is the day! 🎉",
                                city + " here we come!!");
                    } else if (daysLeft == 3) {
                        NotificationHelper.sendNotification(getApplicationContext(), trip.hashCode(),
                                NotificationHelper.CHANNEL_TRIPS,
                                "Loading... ⏳",
                                "Only 3 days till your " + city + " trip!");
                    } else if (daysLeft == 7) {
                        NotificationHelper.sendNotification(getApplicationContext(), trip.hashCode(),
                                NotificationHelper.CHANNEL_TRIPS,
                                "One week to go! ✈️",
                                "Time to double-check your bags for " + city + "!");
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Eșuare silențioasă pentru fundal
            }
        });

        return Result.success();
    }
}