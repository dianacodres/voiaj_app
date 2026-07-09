package com.example.mobile_app;

import android.text.InputType;
import android.view.MotionEvent;
import android.widget.EditText;

/**
 * Face functionala iconita de ochi (drawableEnd="ic_eye") de pe campurile de parola.
 * Foloseste EditText.setOnTouchListener ca sa detecteze click-ul exact pe iconita
 * (nu pe restul campului), fara sa afecteze introducerea normala a textului.
 */
public class PasswordToggleHelper {

    /** Ataseaza comportamentul de afisare/ascundere pe un EditText cu drawableEnd = ic_eye. */
    public static void attach(EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (editText.getCompoundDrawables()[2] != null) {
                    int drawableWidth = editText.getCompoundDrawables()[2].getBounds().width();
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
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        // Schimbarea inputType reseteaza fontul uneori - il restauram, ca sa nu sara vizual
        editText.setTypeface(editText.getTypeface());
        editText.setSelection(selection);
    }
}