/*
 * Copyright (C) 2019-2022 Jorrit "Chainfire" Jongma
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package eu.chainfire.holeylight.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.graphics.RectF;

import androidx.appcompat.app.ActionBar;
import com.google.android.material.slider.Slider;
import eu.chainfire.holeylight.R;
import eu.chainfire.holeylight.animation.NotificationAnimation;
import eu.chainfire.holeylight.misc.Settings;

public class TuneActivity extends BaseActivity implements Settings.OnSettingsChangedListener {
    private Settings settings = null;
    private NotificationAnimation animation = null;
    private final Handler autoClickHandler = new Handler();
    private boolean isProgrammaticChange = false;
    
    // Exact mathematical boundaries calculated relative to the canvas layout and hole origin
    private float maxShiftLeftDp = -100.0f;
    private float maxShiftRightDp = 100.0f;
    private float maxShiftTopDp = -100.0f;
    private float maxShiftBottomDp = 100.0f;
    private float minScaleBaseDp = -50.0f; // Dynamic fallback targeting common pixel specs
    private float minScaleHorizontalDp = -50.0f;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tune);

        settings = Settings.getInstance(this);
        animation = new NotificationAnimation(this, null, 0, null);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.settings_animation_tune_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final View rootLayout = findViewById(R.id.activity_layout);
        if (rootLayout != null) {
            rootLayout.setOnTouchListener((v, event) -> {
                View currentFocus = getCurrentFocus();
                if (currentFocus instanceof EditText) {
                    currentFocus.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
                return false;
            });
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;

        float screenWidthDp = metrics.widthPixels / density;
        float screenHeightDp = metrics.heightPixels / density;

        float holeCenterXDp = screenWidthDp / 2.0f;
        float holeCenterYDp = 38.0f;

        maxShiftLeftDp = -holeCenterXDp;
        maxShiftRightDp = screenWidthDp - holeCenterXDp;
        maxShiftTopDp = -holeCenterYDp;
        maxShiftBottomDp = screenHeightDp - holeCenterYDp;

        minScaleBaseDp = -50.0f;

        float[] bounds = animation.getTuningShiftBoundsDp();

        if (bounds != null) {
            maxShiftLeftDp = bounds[0];
            maxShiftRightDp = bounds[1];
            maxShiftTopDp = bounds[2];
            maxShiftBottomDp = bounds[3];
        }

        setupTuningRow(R.id.sliderScaleBase, R.id.inputScaleBase, R.id.btnAddScaleBaseMinus, R.id.btnAddScaleBasePlus, "scale_base", 0.25f);
        setupTuningRow(R.id.sliderScaleHorizontal, R.id.inputScaleHorizontal, R.id.btnAddScaleHorizontalMinus, R.id.btnAddScaleHorizontalPlus, "scale_horizontal", 0.25f);
        setupTuningRow(R.id.sliderShiftVertical, R.id.inputShiftVertical, R.id.btnShiftVerticalMinus, R.id.btnShiftVerticalPlus, "shift_vertical", 0.25f);
        setupTuningRow(R.id.sliderShiftHorizontal, R.id.inputShiftHorizontal, R.id.btnShiftHorizontalMinus, R.id.btnShiftHorizontalPlus, "shift_horizontal", 0.25f);
        setupTuningRow(R.id.sliderThickness, R.id.inputThickness, R.id.btnAddThicknessMinus, R.id.btnAddThicknessPlus, "thickness", 0.25f);
        setupTuningRow(R.id.sliderSpeed, R.id.inputSpeed, R.id.btnSpeedMinus, R.id.btnSpeedPlus, "speed", 0.1f);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTuningRow(int sliderId, int inputId, int minusId, int plusId, final String key, final float step) {
        final Slider slider = findViewById(sliderId);
        final EditText input = findViewById(inputId);
        View minusBtn = findViewById(minusId);
        View plusBtn = findViewById(plusId);

        if (slider == null || input == null || minusBtn == null || plusBtn == null) return;

        float minBound = -1000.0f;
        float maxBound = 1000.0f;

        if (key.equals("scale_base")) {
            minBound = minScaleBaseDp; // Bound dynamically locked to prevent rendering crashes
            maxBound = 100.0f;
        } else if (key.equals("scale_horizontal")) {
            minBound = minScaleHorizontalDp;
            maxBound = 100.0f;
        } else if (key.equals("thickness")) {
            minBound = 0.0f;
            maxBound = 7.5f;
        } else if (key.equals("speed")) {
            minBound = 0.5f;
            maxBound = 2.0f;
        } else if (key.equals("shift_horizontal")) {
            minBound = maxShiftLeftDp;
            maxBound = maxShiftRightDp;
        } else if (key.equals("shift_vertical")) {
            minBound = maxShiftTopDp;
            maxBound = maxShiftBottomDp;
        }

        final float min = minBound;
        final float max = maxBound;
        
        slider.setStepSize(0.0f);
        slider.setValueFrom(min);
        slider.setValueTo(max);

        float initVal = clamp(getAnimValue(key), min, max);
        slider.setValue(initVal);
        input.setText(String.format(java.util.Locale.US, "%.2f", initVal));

        slider.addOnChangeListener((sl, value, fromUser) -> {
            if (fromUser && !isProgrammaticChange) {
                isProgrammaticChange = true;
                float cleanVal = clamp(value, min, max);
                input.setText(String.format(java.util.Locale.US, "%.2f", cleanVal));
                saveDirectValue(key, cleanVal);
                isProgrammaticChange = false;
            }
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        float val = clamp(Float.parseFloat(text), min, max);
                        isProgrammaticChange = true;
                        slider.setValue(val);
                        saveDirectValue(key, val);
                        input.setText(String.format(java.util.Locale.US, "%.2f", val));
                        isProgrammaticChange = false;
                    }
                } catch (Exception ignored) {}
            }
        });

        class DirectionalTuner implements Runnable {
            private boolean inc = true;
            public DirectionalTuner setDirection(boolean inc) { this.inc = inc; return this; }
            @Override
            public void run() {
                try {
                    isProgrammaticChange = true;
                    triggerStepChange(key, inc ? step : -step);
                    isProgrammaticChange = false;
                    autoClickHandler.postDelayed(this, 80);
                } catch (Exception ignored) {}
            }
        }
        final DirectionalTuner tuner = new DirectionalTuner();

        minusBtn.setOnLongClickListener(v -> { autoClickHandler.post(tuner.setDirection(false)); return true; });
        plusBtn.setOnLongClickListener(v -> { autoClickHandler.post(tuner.setDirection(true)); return true; });

        View.OnTouchListener stopTouch = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                autoClickHandler.removeCallbacksAndMessages(null);
            }
            return false;
        };
        minusBtn.setOnTouchListener(stopTouch);
        plusBtn.setOnTouchListener(stopTouch);
    }

    private float clamp(float val, float min, float max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    private float getAnimValue(String key) {
        switch (key) {
            case "scale_base": return animation.getDpAddScaleBase();
            case "scale_horizontal": return animation.getDpAddScaleHorizontal();
            case "shift_vertical": return animation.getDpShiftVertical();
            case "shift_horizontal": return animation.getDpShiftHorizontal();
            case "thickness": return animation.getDpAddThickness();
            case "speed": return animation.getSpeedFactor();
            default: return 1.0f;
        }
    }

    private void saveDirectValue(String key, float val) {
        if (key.equals("scale_base")) settings.setDpAddScaleBase(val);
        else if (key.equals("scale_horizontal")) settings.setDpAddScaleHorizontal(val);
        else if (key.equals("shift_vertical")) settings.setDpShiftVertical(val);
        else if (key.equals("shift_horizontal")) settings.setDpShiftHorizontal(val);
        else if (key.equals("thickness")) settings.setDpAddThickness(val);
        else if (key.equals("speed")) settings.setSpeedFactor(val);
        onSettingsChanged();
    }

    private void triggerStepChange(String key, float delta) {
        float currentVal = getAnimValue(key);
        float stepSize = Math.abs(delta);
        float nextVal;

        if (delta > 0) {
            nextVal = (float) (Math.floor((currentVal + 0.001f) / stepSize) * stepSize) + stepSize;
        } else {
            nextVal = (float) (Math.ceil((currentVal - 0.001f) / stepSize) * stepSize) - stepSize;
        }

        if (key.equals("scale_base")) {
            nextVal = clamp(nextVal, minScaleBaseDp, 100.0f);
            settings.setDpAddScaleBase(nextVal);
        } else if (key.equals("scale_horizontal")) {
            nextVal = clamp(nextVal, minScaleHorizontalDp, 100.0f);
            settings.setDpAddScaleHorizontal(nextVal);
        } else if (key.equals("shift_vertical")) {
            nextVal = clamp(nextVal, maxShiftTopDp, maxShiftBottomDp);
            settings.setDpShiftVertical(nextVal);
        } else if (key.equals("shift_horizontal")) {
            nextVal = clamp(nextVal, maxShiftLeftDp, maxShiftRightDp);
            settings.setDpShiftHorizontal(nextVal);
        } else if (key.equals("thickness")) {
            nextVal = clamp(nextVal, 0.0f, 7.5f);
            settings.setDpAddThickness(nextVal);
        } else if (key.equals("speed")) {
            nextVal = clamp(nextVal, 0.5f, 2.0f);
            settings.setSpeedFactor(nextVal);
        }
        
        onSettingsChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TestNotification.show(this, TestNotification.NOTIFICATION_ID_TUNE);
        updateLabels();
        settings.registerOnSettingsChangedListener(this);
        Settings.tuning = true;
    }

    @Override
    protected void onStop() {
        Settings.tuning = false;
        settings.unregisterOnSettingsChangedListener(this);
        TestNotification.hide(this, TestNotification.NOTIFICATION_ID_TUNE);
        super.onStop();
    }

    @Override
    public void onSettingsChanged() {
        updateLabels();
        isProgrammaticChange = true;
        updateUIElements();
        isProgrammaticChange = false;
    }

    private void updateUIElements() {
        try {
            updateSliderSafely(R.id.sliderScaleBase, R.id.inputScaleBase, animation.getDpAddScaleBase(), minScaleBaseDp, 100.0f);
            updateSliderSafely(R.id.sliderScaleHorizontal, R.id.inputScaleHorizontal, animation.getDpAddScaleHorizontal(), minScaleHorizontalDp, 100.0f);
            updateSliderSafely(R.id.sliderShiftVertical, R.id.inputShiftVertical, animation.getDpShiftVertical(), maxShiftTopDp, maxShiftBottomDp);
            updateSliderSafely(R.id.sliderShiftHorizontal, R.id.inputShiftHorizontal, animation.getDpShiftHorizontal(), maxShiftLeftDp, maxShiftRightDp);
            updateSliderSafely(R.id.sliderThickness, R.id.inputThickness, animation.getDpAddThickness(), 0.0f, 7.5f);
            updateSliderSafely(R.id.sliderSpeed, R.id.inputSpeed, animation.getSpeedFactor(), 0.5f, 2.0f);
        } catch (Exception ignored) {}
    }

    private void updateSliderSafely(int sliderId, int inputId, float value, float min, float max) {
        Slider slider = findViewById(sliderId);
        EditText input = findViewById(inputId);
        if (slider == null || input == null) return;

        slider.setStepSize(0.0f);
        slider.setValueFrom(min);
        slider.setValueTo(max);

        float cleanVal = clamp(value, min, max);
        slider.setValue(cleanVal);

        String newText = String.format(java.util.Locale.US, "%.2f", cleanVal);
        if (!input.getText().toString().equals(newText)) {
            input.setText(newText);
            input.setSelection(newText.length());
        }
    }

    public void btnClick(View view) {
        int id = view.getId();
        if (id == R.id.btnAddScaleBaseMinus) triggerStepChange("scale_base", -0.25f);
        else if (id == R.id.btnAddScaleBasePlus) triggerStepChange("scale_base", 0.25f);
        else if (id == R.id.btnAddScaleHorizontalMinus) triggerStepChange("scale_horizontal", -0.25f);
        else if (id == R.id.btnAddScaleHorizontalPlus) triggerStepChange("scale_horizontal", 0.25f);
        else if (id == R.id.btnShiftVerticalMinus) triggerStepChange("shift_vertical", -0.25f);
        else if (id == R.id.btnShiftVerticalPlus) triggerStepChange("shift_vertical", 0.25f);
        else if (id == R.id.btnShiftHorizontalMinus) triggerStepChange("shift_horizontal", -0.25f);
        else if (id == R.id.btnShiftHorizontalPlus) triggerStepChange("shift_horizontal", 0.25f);
        else if (id == R.id.btnAddThicknessMinus) triggerStepChange("thickness", -0.25f);
        else if (id == R.id.btnAddThicknessPlus) triggerStepChange("thickness", 0.25f);
        else if (id == R.id.btnSpeedMinus) triggerStepChange("speed", -0.1f);
        else if (id == R.id.btnSpeedPlus) triggerStepChange("speed", 0.1f);
    }

    private void updateLabels() {
        try {
            ((TextView) findViewById(R.id.tvAddScaleBase)).setText(getString(R.string.tune_scale_base, animation.getDpAddScaleBase()));
            ((TextView) findViewById(R.id.tvAddScaleHorizontal)).setText(getString(R.string.tune_scale_horizontal, animation.getDpAddScaleHorizontal()));
            ((TextView) findViewById(R.id.tvShiftVertical)).setText(getString(R.string.tune_shift_vertical, animation.getDpShiftVertical()));
            ((TextView) findViewById(R.id.tvShiftHorizontal)).setText(getString(R.string.tune_shift_horizontal, animation.getDpShiftHorizontal()));
            ((TextView) findViewById(R.id.tvAddThickness)).setText(getString(R.string.tune_thickness, animation.getDpAddThickness()));
            ((TextView) findViewById(R.id.tvSpeed)).setText(getString(R.string.tune_speed, animation.getSpeedFactor()));
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("AlwaysShowAction")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.add(R.string.reset);
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menuItem.setOnMenuItemClickListener(item -> {
            settings.resetTuning();
            onSettingsChanged();
            return true;
        });
        return super.onCreateOptionsMenu(menu);
    }
}
