package com.example.mobile_app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class InactivityWorker extends Worker {

    public InactivityWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        NotificationHelper.sendNotification(getApplicationContext(), 999,
                NotificationHelper.CHANNEL_INACTIVITY,
                "Wanderlust calling? 🌍",
                "There is so much more to explore! Start now!");
        return Result.success();
    }
}