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

        android.util.Log.d("PLAN_TRIP", "Activity started successfully");

        // WindowInsets - verifica ca planTripRoot exista in XML
        View root = findViewById(R.id.planTripRoot);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return insets;
            });
        }

        // Buton inchidere - verifica ca exista
        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        // Toggle
        btnFlexible = findViewById(R.id.btnFlexible);
        btnExactDates = findViewById(R.id.btnExactDates);
        layoutFlexible = findViewById(R.id.layoutFlexible);
        layoutExactDates = findViewById(R.id.layoutExactDates);
        cardDateResult = findViewById(R.id.cardDateResult);
        txtDays = findViewById(R.id.txtDays);
        txtNights = findViewById(R.id.txtNights);
        txtDateSelection = findViewById(R.id.txtDateSelection);
        txtSelectedDates = findViewById(R.id.txtSelectedDates);
        txtResultDays = findViewById(R.id.txtResultDays);
        txtResultNights = findViewById(R.id.txtResultNights);
        chipWeekend = findViewById(R.id.chipWeekend);
        chip1Week = findViewById(R.id.chip1Week);
        chip2Weeks = findViewById(R.id.chip2Weeks);

        if (btnFlexible != null)
            btnFlexible.setOnClickListener(v -> switchToFlexible());
        if (btnExactDates != null)
            btnExactDates.setOnClickListener(v -> switchToExactDates());

        View btnMinus = findViewById(R.id.btnMinus);
        View btnPlus = findViewById(R.id.btnPlus);

        if (btnMinus != null) {
            btnMinus.setOnClickListener(v -> {
                if (currentDays > 1) {
                    currentDays--;
                    updateDaysDisplay();
                    resetChips();
                }
            });
        }

        if (btnPlus != null) {
            btnPlus.setOnClickListener(v -> {
                currentDays++;
                updateDaysDisplay();
                resetChips();
            });
        }

        if (chipWeekend != null)
            chipWeekend.setOnClickListener(v -> setDays(2, chipWeekend));
        if (chip1Week != null)
            chip1Week.setOnClickListener(v -> setDays(7, chip1Week));
        if (chip2Weeks != null)
            chip2Weeks.setOnClickListener(v -> setDays(14, chip2Weeks));

        // Calendar
        CalendarView calendarView = findViewById(R.id.calendarView);
        if (calendarView != null) {
            calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                long selectedTime = selected.getTimeInMillis();

                if (selectingDeparture) {
                    departureDate = selectedTime;
                    selectingDeparture = false;
                    if (txtDateSelection != null)
                        txtDateSelection.setText("Now select your return date");
                    calendarView.setMinDate(selectedTime + 86400000);
                } else {
                    returnDate = selectedTime;
                    selectingDeparture = true;
                    calendarView.setMinDate(0);
                    showDateResult();
                }
            });
        }

        // Reset
        View btnReset = findViewById(R.id.btnResetDates);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                departureDate = -1;
                returnDate = -1;
                selectingDeparture = true;
                if (txtDateSelection != null)
                    txtDateSelection.setText("Select your departure date");
                if (cardDateResult != null)
                    cardDateResult.setVisibility(View.GONE);
                btnReset.setVisibility(View.GONE);
                if (calendarView != null)
                    calendarView.setMinDate(0);
            });
        }

        updateDaysDisplay();
    }

    private void switchToFlexible() {
        isFlexibleMode = true;
        if (layoutFlexible != null)
            layoutFlexible.setVisibility(View.VISIBLE);
        if (layoutExactDates != null)
            layoutExactDates.setVisibility(View.GONE);
        if (btnFlexible != null) {
            btnFlexible.setBackgroundResource(R.drawable.bg_toggle_selected);
            btnFlexible.setTextColor(getColor(R.color.white));
            btnFlexible.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (btnExactDates != null) {
            btnExactDates.setBackgroundResource(android.R.color.transparent);
            btnExactDates.setTextColor(getColor(R.color.navy_blue_1));
            btnExactDates.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        departureDate = -1;
        returnDate = -1;
        selectingDeparture = true;
    }

    private void switchToExactDates() {
        isFlexibleMode = false;
        if (layoutFlexible != null)
            layoutFlexible.setVisibility(View.GONE);
        if (layoutExactDates != null)
            layoutExactDates.setVisibility(View.VISIBLE);
        if (cardDateResult != null)
            cardDateResult.setVisibility(View.GONE);
        if (txtDateSelection != null)
            txtDateSelection.setText("Select your departure date");
        if (btnExactDates != null) {
            btnExactDates.setBackgroundResource(R.drawable.bg_toggle_selected);
            btnExactDates.setTextColor(getColor(R.color.white));
            btnExactDates.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (btnFlexible != null) {
            btnFlexible.setBackgroundResource(android.R.color.transparent);
            btnFlexible.setTextColor(getColor(R.color.navy_blue_1));
            btnFlexible.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void updateDaysDisplay() {
        if (txtDays != null)
            txtDays.setText(String.valueOf(currentDays));
        if (txtNights != null)
            txtNights.setText((currentDays - 1) + " nights");
    }

    private void setDays(int days, TextView selectedChip) {
        currentDays = days;
        updateDaysDisplay();
        resetChips();
        if (selectedChip != null) {
            selectedChip.setBackgroundResource(R.drawable.bg_chip_selected);
            selectedChip.setTextColor(getColor(R.color.white));
        }
    }

    private void resetChips() {
        if (chipWeekend != null) {
            chipWeekend.setBackgroundResource(R.drawable.bg_chip);
            chipWeekend.setTextColor(getColor(R.color.navy_blue_1));
        }
        if (chip1Week != null) {
            chip1Week.setBackgroundResource(R.drawable.bg_chip);
            chip1Week.setTextColor(getColor(R.color.navy_blue_1));
        }
        if (chip2Weeks != null) {
            chip2Weeks.setBackgroundResource(R.drawable.bg_chip);
            chip2Weeks.setTextColor(getColor(R.color.navy_blue_1));
        }
    }

    private void showDateResult() {
        if (departureDate == -1 || returnDate == -1) return;
        long diffMs = returnDate - departureDate;
        int diffDays = (int) (diffMs / (1000 * 60 * 60 * 24));
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        String departure = sdf.format(new Date(departureDate));
        String returnD = sdf.format(new Date(returnDate));
        if (txtSelectedDates != null)
            txtSelectedDates.setText(departure + " – " + returnD);
        if (txtResultDays != null)
            txtResultDays.setText(String.valueOf(diffDays));
        if (txtResultNights != null)
            txtResultNights.setText((diffDays - 1) + " nights");
        if (cardDateResult != null)
            cardDateResult.setVisibility(View.VISIBLE);
        if (txtDateSelection != null)
            txtDateSelection.setText("✓ Dates selected");
        View btnReset = findViewById(R.id.btnResetDates);
        if (btnReset != null)
            btnReset.setVisibility(View.VISIBLE);
    }
}