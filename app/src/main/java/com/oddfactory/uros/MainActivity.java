package com.oddfactory.uros;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView state;
    private boolean settingsOpen;
    private final Runnable update = new Runnable() {
        @Override public void run() { updateState(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setDecorFitsSystemWindows(false);
        settingsOpen=getIntent().getBooleanExtra("settings",false);
        buildUi();
    }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private GradientDrawable panel(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(20));
        shape.setStroke(dp(2), Color.rgb(96, 70, 69));
        return shape;
    }
    private TextView label(LinearLayout parent, int resource, int size) {
        TextView text = new TextView(this);
        text.setText(resource);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(69, 46, 48));
        text.setPadding(0, dp(8), 0, dp(8));
        parent.addView(text);
        return text;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets edges = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            scroll.setPadding(edges.left, edges.top, edges.right, edges.bottom);
            return insets;
        });
        TextView heading=label(page, R.string.title, 29);
        label(page, R.string.subtitle, 15);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18), dp(12), dp(18), dp(18));
        controls.setBackground(panel(Color.WHITE));
        LinearLayout.LayoutParams block = new LinearLayout.LayoutParams(-1, -2);
        block.topMargin = dp(16);
        page.addView(controls, block);
        state = label(controls, R.string.status_off, 17);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Button launch=button(controls, R.string.start, () -> {
            Preferences.get(this).edit().putBoolean("running", true).apply();
            // Repeated Start must repair the live windows even when the preference is already true.
            UrosAccessibilityService.showIfConnected();
            updateState();
            if (!accessibilityEnabled()) openAccessibility();
        });
        button(controls, R.string.stop, () -> {
            Preferences.get(this).edit().putBoolean("running", false).apply();
            updateState();
        });
        label(page, R.string.quick_help, 16);
        LinearLayout detail=new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(18),dp(12),dp(18),dp(18));
        detail.setBackground(panel(Color.WHITE));
        Button more=button(page,R.string.more,()->{});
        detail.setVisibility(settingsOpen?View.VISIBLE:View.GONE);
        more.setText(settingsOpen?R.string.less:R.string.more);
        more.setOnClickListener(v->{settingsOpen=!settingsOpen;detail.setVisibility(settingsOpen?View.VISIBLE:View.GONE);more.setText(settingsOpen?R.string.less:R.string.more);});
        page.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        button(detail, R.string.accessibility, this::openAccessibility);
        label(detail, R.string.accessibility_help, 14);
        button(detail, R.string.brightness_permission, () -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
            android.net.Uri.parse("package:"+getPackageName()))));
        label(detail, R.string.brightness_help, 14);
        slider(detail, R.string.size, "controlSize", 64, 160, Preferences.get(this).getInt("controlSize",104));
        slider(detail, R.string.max_length, "controlMaxPercent", 100, 240, Preferences.get(this).getInt("controlMaxPercent",165));
        slider(detail, R.string.control_position, "controlPosition", 30, 85, Preferences.get(this).getInt("controlPosition",60));
        button(detail, R.string.reset, () -> {
            Preferences.get(this).edit().putInt("controlSize",104).putInt("controlMaxPercent",165).putInt("controlPosition",60).apply();
            buildUi();
            updateState();
        });
        button(detail,R.string.reset_position,()->{
            Preferences.get(this).edit().remove("placePillowX").remove("placePillowY").remove("placeUrosX").remove("placeUrosY").apply();
            UrosAccessibilityService.showIfConnected();
        });
        label(detail, R.string.bounds_help, 14);
        label(detail, R.string.privacy, 14);
        label(detail,R.string.font_notice,13);
        UiFont.apply(page);
        heading.setTypeface(UiFont.bold(this));
        launch.setTypeface(UiFont.bold(this));
        launch.setTextSize(30);launch.setMinHeight(dp(88));
        launch.setTextColor(Color.WHITE);launch.setBackground(panel(Color.rgb(165,57,72)));
        setContentView(scroll);
    }
    private Button button(LinearLayout parent, int title, Runnable click) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(view -> click.run());
        button.setMinHeight(dp(54));
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    private void slider(LinearLayout parent, int title, String key, int min, int max, int initial) {
        TextView titleView = label(parent, title, 15);
        titleView.setText(getString(title, initial));
        SeekBar seek = new SeekBar(this);
        seek.setMin(min);
        seek.setMax(max);
        seek.setProgress(initial);
        seek.setContentDescription(getString(title, initial));
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                titleView.setText(getString(title, value));
                seek.setContentDescription(getString(title, value));
                if (fromUser) Preferences.get(MainActivity.this).edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }
    private boolean accessibilityEnabled() {
        // This display-only service requests no events. Some system lists omit it even while bound.
        if (UrosAccessibilityService.connected) return true;
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        ComponentName desired = new ComponentName(this, UrosAccessibilityService.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (desired.equals(ComponentName.unflattenFromString(info.getId()))) return true;
        }
        return false;
    }
    private void openAccessibility() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (ActivityNotFoundException ignored) { Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show(); }
    }
    private void updateState() {
        int message;
        if (!Preferences.running(this)) message = R.string.status_off;
        else if (!accessibilityEnabled()) message = R.string.status_disabled;
        else if (!UrosAccessibilityService.connected) message = R.string.status_waiting;
        else if (UrosAccessibilityService.status == 2) message = R.string.status_failed;
        else if (UrosAccessibilityService.status == 3) message = R.string.status_window_failed;
        else message = R.string.status_running;
        state.setText(message);
        if(message==R.string.status_running && !Settings.System.canWrite(this)) state.append("\n밝기 변경 허용이 필요합니다. 볼륨은 바로 사용할 수 있어요.");
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(update); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
}
