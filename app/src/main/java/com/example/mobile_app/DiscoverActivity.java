package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DiscoverActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_discover);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.discoverRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, 0, 0, systemBars.bottom);
                    return insets;
                });

        TextView txtAvatar = findViewById(R.id.txtAvatar);
        //navigare setari cont apasand pe iconita
        txtAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(DiscoverActivity.this, AccountSettingsActivity.class);
            startActivity(intent);
        });
    }
}