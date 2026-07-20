package com.example.mobile_app;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.mobile_app.R;

public abstract class BaseActivity extends AppCompatActivity {
    protected LottieAnimationView loadingAnimation;
    protected View loadingOverlay;
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupLoadingOverlay();
    }

    private void setupLoadingOverlay() {
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    public void showLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    public void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }
}