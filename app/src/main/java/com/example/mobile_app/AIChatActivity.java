package com.example.mobile_app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

/**
 * Ecran de chat cu Voiaj AI. Deocamdata contine doar UI + o demonstratie statica
 * (itinerariul "Rome - 4 days" din design), fara backend real de generare AI -
 * asta e o functionalitate separata, mai mare, planuita pentru mai tarziu
 * (SetFit / recomandari / itinerariu real).
 */
public class AIChatActivity extends AppCompatActivity {

    private LinearLayout messagesContainer;
    private NestedScrollView scrollMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_aichat);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.aiChatRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                    return insets;
                });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        messagesContainer = findViewById(R.id.messagesContainer);
        scrollMessages = findViewById(R.id.scrollMessages);

        EditText edtChatInput = findViewById(R.id.edtChatInput);

        findViewById(R.id.btnSendChat).setOnClickListener(v -> {
            String text = edtChatInput.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                addUserBubble(text);
                edtChatInput.setText("");
                // Aici, mai tarziu, se va apela un raspuns real de la AI.
            }
        });

        findViewById(R.id.chipAddDay).setOnClickListener(v -> addUserBubble("+ Add a day"));
        findViewById(R.id.chipCheaper).setOnClickListener(v -> addUserBubble("Make it cheaper"));
        findViewById(R.id.chipBestFood).setOnClickListener(v -> addUserBubble("Show me the best food spots"));

        // Daca vii dintr-un quick start ("Plan itinerary" etc.), afisam promptul ca prim mesaj
        String initialPrompt = getIntent().getStringExtra("initialPrompt");
        if (initialPrompt != null) {
            addUserBubble(initialPrompt);
        }
    }

    /** Adauga o bula noua de mesaj, in stilul userului (dreapta, fundal navy, text alb). */
    private void addUserBubble(String text) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(14f);
        bubble.setBackgroundResource(R.drawable.bg_square_button_dark);
        int paddingPx = (int) (12 * getResources().getDisplayMetrics().density);
        bubble.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        int marginPx = (int) (14 * getResources().getDisplayMetrics().density);
        params.bottomMargin = marginPx;
        bubble.setLayoutParams(params);

        messagesContainer.addView(bubble);
        scrollMessages.post(() -> scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN));
    }
}