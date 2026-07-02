package com.example.mobile_app;

import android.os.Bundle;
import android.view.View;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PlanTripActivity extends AppCompatActivity {

    private int currentDays = 4;
    private boolean isFlexibleMode = true;
    private long departureDate = -1;
    private long returnDate = -1;
    private boolean selectingDeparture = true;

    private TextView txtDays, txtNights, txtDateSelection;
    private TextView txtSelectedDates, txtResultDays, txtResultNights;
    private LinearLayout layoutFlexible, layoutExactDates;
    private CardView cardDateResult;
    private TextView btnFlexible, btnExactDates;
    private TextView chipWeekend, chip1Week, chip2Weeks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plan_trip);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.planTripRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                    return insets;
                });

        txtDays = findViewById(R.id.txtDays);
        txtNights = findViewById(R.id.txtNights);
        txtDateSelection = findViewById(R.id.txtDateSelection);
        txtSelectedDates = findViewById(R.id.txtSelectedDates);
        txtResultDays = findViewById(R.id.txtResultDays);
        txtResultNights = findViewById(R.id.txtResultNights);
        layoutFlexible = findViewById(R.id.layoutFlexible);
        layoutExactDates = findViewById(R.id.layoutExactDates);
        cardDateResult = findViewById(R.id.cardDateResult);
        btnFlexible = findViewById(R.id.btnFlexible);
        btnExactDates = findViewById(R.id.btnExactDates);
        chipWeekend = findViewById(R.id.chipWeekend);
        chip1Week = findViewById(R.id.chip1Week);
        chip2Weeks = findViewById(R.id.chip2Weeks);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        btnFlexible.setOnClickListener(v -> switchToFlexible());
        btnExactDates.setOnClickListener(v -> switchToExactDates());

        findViewById(R.id.btnMinus).setOnClickListener(v -> {
            if (currentDays > 1) {
                currentDays--;
                updateDaysDisplay();
                resetChips();
            }
        });

        findViewById(R.id.btnPlus).setOnClickListener(v -> {
            currentDays++;
            updateDaysDisplay();
            resetChips();
        });

        chipWeekend.setOnClickListener(v -> setDays(2, chipWeekend));
        chip1Week.setOnClickListener(v -> setDays(7, chip1Week));
        chip2Weeks.setOnClickListener(v -> setDays(14, chip2Weeks));

        // Calendar nativ
        CalendarView calendarView = findViewById(R.id.calendarView);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth);
            long selectedTime = selected.getTimeInMillis();

            if (selectingDeparture) {
                departureDate = selectedTime;
                selectingDeparture = false;
                txtDateSelection.setText("✓ Departure set — now select return date");
                calendarView.setMinDate(selectedTime + 86400000);
            } else {
                returnDate = selectedTime;
                selectingDeparture = true;
                calendarView.setMinDate(0);
                showDateResult();
            }
        });

        // Reset
        findViewById(R.id.btnResetDates).setOnClickListener(v -> {
            departureDate = -1;
            returnDate = -1;
            selectingDeparture = true;
            txtDateSelection.setText("Select your departure date");
            cardDateResult.setVisibility(View.GONE);
            findViewById(R.id.btnResetDates).setVisibility(View.GONE);
            calendarView.setMinDate(0);
        });

        updateDaysDisplay();
    }

    private void switchToFlexible() {
        isFlexibleMode = true;
        layoutFlexible.setVisibility(View.VISIBLE);
        layoutExactDates.setVisibility(View.GONE);
        btnFlexible.setBackgroundResource(R.drawable.bg_toggle_selected);
        btnFlexible.setTextColor(getColor(R.color.white));
        btnFlexible.setTypeface(null, android.graphics.Typeface.BOLD);
        btnExactDates.setBackgroundResource(android.R.color.transparent);
        btnExactDates.setTextColor(getColor(R.color.navy_blue_1));
        btnExactDates.setTypeface(null, android.graphics.Typeface.NORMAL);
        departureDate = -1;
        returnDate = -1;
        selectingDeparture = true;
    }

    private void switchToExactDates() {
        isFlexibleMode = false;
        layoutFlexible.setVisibility(View.GONE);
        layoutExactDates.setVisibility(View.VISIBLE);
        cardDateResult.setVisibility(View.GONE);
        txtDateSelection.setText("Select your departure date");
        btnExactDates.setBackgroundResource(R.drawable.bg_toggle_selected);
        btnExactDates.setTextColor(getColor(R.color.white));
        btnExactDates.setTypeface(null, android.graphics.Typeface.BOLD);
        btnFlexible.setBackgroundResource(android.R.color.transparent);
        btnFlexible.setTextColor(getColor(R.color.navy_blue_1));
        btnFlexible.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    private void updateDaysDisplay() {
        txtDays.setText(String.valueOf(currentDays));
        txtNights.setText((currentDays - 1) + " nights");
    }

    private void setDays(int days, TextView selectedChip) {
        currentDays = days;
        updateDaysDisplay();
        resetChips();
        selectedChip.setBackgroundResource(R.drawable.bg_chip_selected);
        selectedChip.setTextColor(getColor(R.color.white));
    }

    private void resetChips() {
        chipWeekend.setBackgroundResource(R.drawable.bg_chip);
        chipWeekend.setTextColor(getColor(R.color.navy_blue_1));
        chip1Week.setBackgroundResource(R.drawable.bg_chip);
        chip1Week.setTextColor(getColor(R.color.navy_blue_1));
        chip2Weeks.setBackgroundResource(R.drawable.bg_chip);
        chip2Weeks.setTextColor(getColor(R.color.navy_blue_1));
    }

    private void showDateResult() {
        if (departureDate == -1 || returnDate == -1) return;
        long diffMs = returnDate - departureDate;
        int diffDays = (int) (diffMs / (1000 * 60 * 60 * 24));
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        String departure = sdf.format(new Date(departureDate));
        String returnD = sdf.format(new Date(returnDate));
        txtSelectedDates.setText(departure + " – " + returnD);
        txtResultDays.setText(String.valueOf(diffDays));
        txtResultNights.setText((diffDays - 1) + " nights");
        cardDateResult.setVisibility(View.VISIBLE);
        txtDateSelection.setText("✓ Dates selected");
        findViewById(R.id.btnResetDates).setVisibility(View.VISIBLE);
    }
}