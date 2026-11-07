package com.uitm.excessbaggagefee;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final double ECONOMY_LIMIT_KG = 20.0;
    private static final double BUSINESS_LIMIT_KG = 40.0;
    private static final double SERVICE_TAX = 0.08;
    private static final double MAX_WEIGHT_KG = 300;
    private static final double MAX_RATE = 1000;

    private final DecimalFormat kg = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private View root, cardBreakdown, ivHeroLogo;
    private ScrollView scroll;
    private TextInputLayout tilWeight, tilRate;
    private TextInputEditText etWeight, etRate;
    private Slider slider;
    private MaterialButtonToggleGroup toggleClass;
    private ChipGroup chipRate;
    private LinearProgressIndicator progress;
    private TextView tvHeroTotal, tvStatus, tvProgressLeft, tvAllowance;
    private TextView tvExcessWeight, tvCharge, tvTax, tvTotal, tvFormula;

    private ValueAnimator totalAnimator;
    private float shownTotal = 0f;
    private boolean syncing = false;
    private boolean lastOver = false;

    // last valid result (used by "Share result")
    private double lastWeight, lastExcess, lastTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayShowTitleEnabled(false); // logo + name sit inside the toolbar
        }

        root = findViewById(R.id.root);
        scroll = findViewById(R.id.scroll);
        View cardHero = findViewById(R.id.cardHero);
        View cardTip = findViewById(R.id.cardTip);
        View cardInputs = findViewById(R.id.cardInputs);
        cardBreakdown = findViewById(R.id.cardBreakdown);
        ivHeroLogo = findViewById(R.id.ivHeroLogo);
        tilWeight = findViewById(R.id.tilWeight);
        tilRate = findViewById(R.id.tilRate);
        etWeight = findViewById(R.id.etWeight);
        etRate = findViewById(R.id.etRate);
        slider = findViewById(R.id.sliderWeight);
        toggleClass = findViewById(R.id.toggleClass);
        chipRate = findViewById(R.id.chipRate);
        progress = findViewById(R.id.progress);
        tvHeroTotal = findViewById(R.id.tvHeroTotal);
        tvStatus = findViewById(R.id.tvStatus);
        tvProgressLeft = findViewById(R.id.tvProgressLeft);
        tvAllowance = findViewById(R.id.tvAllowance);
        tvExcessWeight = findViewById(R.id.tvExcessWeight);
        tvCharge = findViewById(R.id.tvCharge);
        tvTax = findViewById(R.id.tvTax);
        tvTotal = findViewById(R.id.tvTotal);
        tvFormula = findViewById(R.id.tvFormula);

        setupSlider();
        setupToggle();
        setupRateChips();
        setupTextWatchers();

        findViewById(R.id.btnCalculate).setOnClickListener(v -> onCalculate());
        findViewById(R.id.btnReset).setOnClickListener(v -> reset());
        findViewById(R.id.btnShareResult).setOnClickListener(v -> shareResult());
        etRate.setOnEditorActionListener((v, actionId, event) -> {
            onCalculate();
            return true;
        });

        refresh(false);
        playIntro(cardHero, cardTip, cardInputs);
    }

    // ---------------------------------------------------------------- setup

    private void setupSlider() {
        slider.setLabelFormatter(value -> kg.format(value) + " kg");
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser || syncing) return;
            syncing = true;
            etWeight.setText(value <= 0 ? "" : kg.format(value));
            if (etWeight.getText() != null) etWeight.setSelection(etWeight.getText().length());
            syncing = false;
            refresh(false);
        });
    }

    private void setupToggle() {
        toggleClass.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) refresh(false);
        });
    }

    private void setupRateChips() {
        int count = chipRate.getChildCount();
        for (int idx = 0; idx < count; idx++) {
            final Chip chip = (Chip) chipRate.getChildAt(idx);
            chip.setOnClickListener(v -> {
                etRate.setText((String) chip.getTag());
                if (etRate.getText() != null) etRate.setSelection(etRate.getText().length());
            });
        }
    }

    private void setupTextWatchers() {
        etWeight.addTextChangedListener(new SimpleWatcher() {
            @Override
            void onChanged() {
                if (syncing) return;
                syncSliderFromText();
                refresh(false);
            }
        });
        etRate.addTextChangedListener(new SimpleWatcher() {
            @Override
            void onChanged() {
                syncChipsFromText();
                refresh(false);
            }
        });
    }

    private void syncSliderFromText() {
        double w = parseRaw(etWeight);
        float v = 0f;
        if (!Double.isNaN(w) && w > 0) {
            v = (float) Math.min(slider.getValueTo(), Math.max(0, Math.round(w * 2) / 2.0));
        }
        syncing = true;
        slider.setValue(v);
        syncing = false;
    }

    private void syncChipsFromText() {
        double r = parseRaw(etRate);
        for (int i = 0; i < chipRate.getChildCount(); i++) {
            Chip chip = (Chip) chipRate.getChildAt(i);
            double chipValue = Double.parseDouble((String) chip.getTag());
            chip.setChecked(!Double.isNaN(r) && r == chipValue);
        }
    }

    // ---------------------------------------------------------- calculation

    private boolean isEconomy() {
        return toggleClass.getCheckedButtonId() != R.id.btnBusiness;
    }

    private void onCalculate() {
        hideKeyboard();
        root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        refresh(true);
        if (cardBreakdown.getVisibility() == View.VISIBLE) {
            pulse(tvHeroTotal);
            scroll.post(() -> scroll.smoothScrollTo(0, cardBreakdown.getTop()));
        }
    }

    /**
     * Recalculates everything. When explicit is true (Calculate button), empty fields
     * are reported as errors; while typing, only invalid values are reported.
     */
    private void refresh(boolean explicit) {
        tilWeight.setError(null);
        tilRate.setError(null);

        double limit = isEconomy() ? ECONOMY_LIMIT_KG : BUSINESS_LIMIT_KG;
        tvAllowance.setText(getString(R.string.allowance_label, kg.format(limit)));

        double w = parseField(etWeight, tilWeight, R.string.error_weight_empty,
                R.string.error_weight_invalid, MAX_WEIGHT_KG, R.string.error_weight_too_high, explicit);
        double r = parseField(etRate, tilRate, R.string.error_rate_empty,
                R.string.error_rate_invalid, MAX_RATE, R.string.error_rate_too_high, explicit);

        if (Double.isNaN(w)) {
            showEmptyGauge();
        } else {
            updateGauge(w, limit);
        }

        if (Double.isNaN(w) || Double.isNaN(r)) {
            hideBreakdown();
            animateTotal(0);
            if (explicit) {
                if (tilWeight.getError() != null) shake(tilWeight);
                if (tilRate.getError() != null) shake(tilRate);
                Snackbar.make(root, R.string.notice_fix_errors, Snackbar.LENGTH_LONG).show();
            }
            return;
        }

        // Excess weight is never negative
        double excess = Math.max(0, w - limit);
        double charge = excess * r;
        double tax = SERVICE_TAX * charge;
        double total = charge + tax;

        lastWeight = w;
        lastExcess = excess;
        lastTotal = total;

        tvExcessWeight.setText(getString(R.string.result_excess_weight, kg.format(excess)));
        tvCharge.setText(money(charge));
        tvTax.setText(money(tax));
        tvFormula.setText(excess > 0
                ? getString(R.string.formula_over, kg.format(excess), plain(r), plain(charge))
                : getString(R.string.formula_none));

        showBreakdown();
        animateTotal(total);
    }

    private void updateGauge(double w, double limit) {
        boolean over = w > limit;
        if (over && !lastOver) wiggle(ivHeroLogo);
        lastOver = over;

        int pct = (int) Math.min(100, Math.round(w / limit * 100));
        progress.setIndicatorColor(ContextCompat.getColor(this, over ? R.color.over : R.color.within));
        progress.setProgressCompat(pct, true);
        tvProgressLeft.setText(getString(R.string.progress_label, kg.format(w)));
        tvStatus.setText(over
                ? getString(R.string.status_over, kg.format(w - limit), kg.format(limit))
                : getString(R.string.status_within, kg.format(limit)));
    }

    private void showEmptyGauge() {
        lastOver = false;
        progress.setProgressCompat(0, true);
        tvProgressLeft.setText(getString(R.string.progress_label, "0"));
        tvStatus.setText(R.string.status_empty);
    }

    private void reset() {
        hideKeyboard();
        syncing = true;
        etWeight.setText("");
        slider.setValue(0f);
        syncing = false;
        etRate.setText("");
        toggleClass.check(R.id.btnEconomy);
        chipRate.clearCheck();
        refresh(false);
        scroll.smoothScrollTo(0, 0);
    }

    private void shareResult() {
        String cls = getString(isEconomy() ? R.string.class_economy_short : R.string.class_business_short);
        String text = getString(R.string.share_result_text,
                kg.format(lastWeight), cls, kg.format(lastExcess), plain(lastTotal));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.btn_share_result)));
    }

    // ------------------------------------------------------------- parsing

    private double parseRaw(TextInputEditText et) {
        String s = et.getText() == null ? "" : et.getText().toString().trim().replace(',', '.');
        if (s.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Returns the value, or NaN (and shows an error on the field) when invalid. */
    private double parseField(TextInputEditText et, TextInputLayout til, int emptyRes, int invalidRes,
                              double max, int tooHighRes, boolean explicit) {
        String s = et.getText() == null ? "" : et.getText().toString().trim();
        if (s.isEmpty()) {
            if (explicit) til.setError(getString(emptyRes));
            return Double.NaN;
        }
        double v = parseRaw(et);
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) {
            til.setError(getString(invalidRes));
            return Double.NaN;
        }
        if (v > max) {
            til.setError(getString(tooHighRes));
            return Double.NaN;
        }
        return v;
    }

    // ------------------------------------------------------------ formatting

    private String money(double v) {
        return String.format(Locale.US, "RM %.2f", v);
    }

    private String plain(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    // ------------------------------------------------------------ animations

    private void animateTotal(double target) {
        if (totalAnimator != null) totalAnimator.cancel();
        totalAnimator = ValueAnimator.ofFloat(shownTotal, (float) target);
        totalAnimator.setDuration(450);
        totalAnimator.setInterpolator(new DecelerateInterpolator());
        totalAnimator.addUpdateListener(a -> {
            shownTotal = (float) a.getAnimatedValue();
            String s = money(shownTotal);
            tvHeroTotal.setText(s);
            tvTotal.setText(s);
        });
        totalAnimator.start();
    }

    private void showBreakdown() {
        if (cardBreakdown.getVisibility() == View.VISIBLE) return;
        cardBreakdown.setVisibility(View.VISIBLE);
        cardBreakdown.setAlpha(0f);
        cardBreakdown.setTranslationY(48f);
        cardBreakdown.animate().alpha(1f).translationY(0f).setDuration(400)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void hideBreakdown() {
        cardBreakdown.animate().cancel();
        cardBreakdown.setVisibility(View.GONE);
    }

    private void shake(View v) {
        ObjectAnimator.ofFloat(v, "translationX", 0, -18, 18, -12, 12, -6, 6, 0).setDuration(420).start();
    }

    private void wiggle(View v) {
        ObjectAnimator.ofFloat(v, "rotation", 0, -14, 14, -10, 10, -5, 5, 0).setDuration(600).start();
    }

    private void pulse(final View v) {
        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(140)
                .withEndAction(v.animate().scaleX(1f).scaleY(1f).setDuration(160)::start)
                .start();
    }

    private void playIntro(View... views) {
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            v.setAlpha(0f);
            v.setTranslationY(70f);
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 90L).setDuration(500)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        root.requestFocus();
    }

    // ------------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            send.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, getString(R.string.app_url)));
            startActivity(Intent.createChooser(send, getString(R.string.action_share)));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        abstract void onChanged();

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChanged();
        }
    }
}