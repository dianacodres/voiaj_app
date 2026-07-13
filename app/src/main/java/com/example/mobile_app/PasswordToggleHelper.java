package com.example.mobile_app;

import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.MotionEvent;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

/**
 * Face functionala iconita de ochi (drawableEnd) de pe campurile de parola.
 * Foloseste EditText.setOnTouchListener ca sa detecteze click-ul exact pe iconita
 * (nu pe restul campului), si schimba efectiv iconita intre ic_eye (parola vizibila)
 * si ic_eye_off (parola ascunsa), nu doar tipul de input.
 */
public class PasswordToggleHelper {

    /** Ataseaza comportamentul de afisare/ascundere pe un EditText cu drawableEnd = ic_hidden. */
    public static void attach(EditText editText) {
        // La pornire, parola e ascunsa - iconita porneste pe ic_hidden
        setEndDrawable(editText, R.drawable.ic_hidden);

        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable[] drawables = editText.getCompoundDrawables();
                if (drawables[2] != null) {
                    int drawableWidth = drawables[2].getBounds().width();
                    boolean tappedOnIcon = event.getX() >= (editText.getWidth()
                            - editText.getPaddingEnd() - drawableWidth);
                    if (tappedOnIcon) {
                        togglePasswordVisibility(editText);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static void togglePasswordVisibility(EditText editText) {
        int selection = editText.getSelectionEnd();
        boolean isCurrentlyHidden = editText.getInputType() ==
                (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        if (isCurrentlyHidden) {
            // Devine vizibila -> arata ic_eye
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            setEndDrawable(editText, R.drawable.ic_eye);
        } else {
            // Devine ascunsa -> arata ic_hidden
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            setEndDrawable(editText, R.drawable.ic_hidden);
        }

        editText.setTypeface(editText.getTypeface());
        editText.setSelection(selection);
    }

    /** Schimba DOAR iconita din dreapta (drawableEnd), pastrand start/top/bottom neatinse. */
    private static void setEndDrawable(EditText editText, int drawableResId) {
        Drawable[] drawables = editText.getCompoundDrawables();
        Drawable newEnd = ContextCompat.getDrawable(editText.getContext(), drawableResId);
        editText.setCompoundDrawablesWithIntrinsicBounds(drawables[0], drawables[1], newEnd, drawables[3]);
    }
}