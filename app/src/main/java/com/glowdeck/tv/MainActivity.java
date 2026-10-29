package com.glowdeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewParent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, remote-first WLED controller. No WebView, tracking, or external libraries. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF080E1C;
    private static final int PANEL = 0xFF111B30;
    private static final int TILE = 0xFF1B2941;
    private static final int BORDER = 0xFF2A3C58;
    private static final int ACCENT = 0xFF4BB7FF;
    private static final int WHITE = 0xFFF1F7FF;
    private static final int MUTED = 0xFF97AAC5;
    private static final int GOOD = 0xFF73E0A5;
    private static final int BAD = 0xFFFF9C9D;

    private static final int[] COLOR_VALUES = {
            0xFFFFB66E, 0xFFFFFFFF, 0xFFFF414F, 0xFFFF842F,
            0xFFFFD94B, 0xFF5DE08E, 0xFF3E9BFF, 0xFFB178FF
    };
    private static final String[] COLOR_NAMES = {
            "Warm", "White", "Red", "Orange", "Yellow", "Green", "Blue", "Purple"
    };
    private static final String[] POPULAR_EFFECTS = {
            "Solid", "Breathe", "Rainbow", "Rainbow Runner", "Colorloop",
            "Fireworks", "Fire 2012", "Twinkle", "Chase", "Meteor", "Sparkle", "Wipe"
    };
    private static final String[] POPULAR_PALETTES = {
            "Default", "Rainbow", "Party", "Ocean", "Forest", "Lava", "Fire", "Sunset", "Cloud"
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    // One worker serializes all WLED commands: WLED controllers are small ESP devices.
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService discoveryWork = Executors.newSingleThreadExecutor();
    private final List<WledDevice> devices = new ArrayList<>(); // Main thread only.
    private String selectedHost = ""; // "*" targets every configured device.
    private int selectedSegment = -1; // -1 = all segments, per-device segments otherwise.
    private String message = "Ready";
    private boolean running = false;
    private boolean scanning = false;
    private FrameLayout rootFrame;
    private FrameLayout drawerOverlay;
    private LinearLayout navList;
    private LinearLayout workspace;
    private LinearLayout controlList;
    private ScrollView drawerScroll;
    // Focus and drawer navigation are kept separate from WLED state refreshes.
    private String drawerReturnFocusTag = "nav:home";
    private String pendingFocusRestoration = null;
    private String rememberedDashboardFocusTag = "power:on";
    private String rememberedRailFocusTag = "nav:home";
    private String adjustingSliderTag = null;
    private final ArrayDeque<DrawerVisit> drawerHistory = new ArrayDeque<>();
    private String pendingDrawerFocus = null;
    private int pendingDrawerScrollY = -1;
    private boolean deferredStructuralRender = false;
    private String pendingSliderType = null;
    private int pendingSliderValue = -1;
    private final Runnable commitSlider = this::flushSliderCommand;
    private static final class DrawerVisit {
        final String key, focusTag;
        final int scrollY;
        DrawerVisit(String key, String focusTag, int scrollY) {
            this.key = key; this.focusTag = focusTag; this.scrollY = scrollY;
        }
    }
    private TextView subheading;
    private String activeDrawer = "";
    private boolean tvMode;
    private boolean wideTouchLayout;
    private boolean touchNavAtBottom;
    private ScrollView touchScroll;
    private final List<SavedGroup> savedGroups = new ArrayList<>();
    private final List<Favorite> favorites = new ArrayList<>();
    private final List<GlowScene> scenes = new ArrayList<>();
    private GlowDeckRepository store;
    private float uiScale = 1f; // Keep the entire dashboard inside the TV viewport.
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discoveryListener;
    private WifiManager.MulticastLock multicastLock;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            refreshAll();
            ui.postDelayed(this, 8000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        chooseDisplayMode();
        store = new GlowDeckRepository(this, getPreferences(MODE_PRIVATE));
        favorites.addAll(store.loadFavorites());
        scenes.addAll(store.loadScenes());
        loadDevices();
        loadGroups();
        buildCurrentShell();
        render();
        if (tvMode) rootFrame.post(() -> restoreFocus("nav:home", "power:on"));
    }

    private void chooseDisplayMode() {
        Configuration config = getResources().getConfiguration();
        tvMode = (config.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
                (getPackageManager().hasSystemFeature("android.software.leanback") &&
                        !getPackageManager().hasSystemFeature("android.hardware.touchscreen"));
        int width = config.screenWidthDp;
        // Phone/tablet layouts adapt to current *window*, including split-screen.
        wideTouchLayout = !tvMode && width >= 720;
        touchNavAtBottom = !tvMode && width < 600;
        if (tvMode) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            float h = getResources().getDisplayMetrics().heightPixels /
                    getResources().getDisplayMetrics().density;
            uiScale = Math.min(1.0f, Math.max(0.64f, h / 540f));
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(0);
            uiScale = 1f;
        }
    }

    private void buildCurrentShell() {
        if (tvMode) buildShell();
        else buildTouchShell();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerOverlay != null) {
            rootFrame.removeView(drawerOverlay);
            drawerOverlay = null;
            drawerScroll = null;
            activeDrawer = "";
            drawerHistory.clear();
        }
        chooseDisplayMode();
        buildCurrentShell();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        ui.removeCallbacks(ticker);
        ui.post(ticker);
        if (!getPreferences(MODE_PRIVATE).getBoolean("firstScanDone", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("firstScanDone", true).apply();
            ui.postDelayed(this::scan, 1400);
        }
    }

    @Override protected void onPause() {
        running = false;
        ui.removeCallbacks(ticker);
        ui.removeCallbacks(commitSlider);
        flushSliderCommand();
        stopDiscovery();
        super.onPause();
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        discoveryWork.shutdownNow();
        super.onDestroy();
    }

    private void loadDevices() {
        String json = store.readDevices();
        if (json == null) {
            // First-use convenience: the user's previously configured Fireplace WLED.
            devices.add(new WledDevice("192.168.1.71", "Fireplace"));
            selectedHost = "192.168.1.71";
            saveDevices();
            return;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) continue;
                String host = normalizeHost(entry.optString("host"));
                if (!host.isEmpty()) devices.add(new WledDevice(host, entry.optString("alias")));
            }
        } catch (JSONException ignored) { }
        selectedHost = devices.isEmpty() ? "*" : devices.get(0).host;
    }

    private void saveDevices() {
        JSONArray array = new JSONArray();
        for (WledDevice d : devices) {
            JSONObject entry = new JSONObject();
            try { entry.put("host", d.host); entry.put("alias", d.alias); }
            catch (JSONException ignored) { }
            array.put(entry);
        }
        store.saveDevices(array.toString());
    }

    /** TV shell: permanent compact icon rail, full-width content and modal slide-out drawers. */
    private void buildShell() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundResource(R.drawable.glowdeck_night);
        setContentView(rootFrame);

        LinearLayout shell = horizontal(Gravity.TOP);
        shell.setPadding(dp(10), dp(8), dp(10), dp(8));
        rootFrame.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout rail = vertical();
        rail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(5), dp(8), dp(5), dp(7));
        rail.setBackground(shape(0xFF0A1325, 12, BORDER, 1));
        shell.addView(rail, sized(83, -1));
        navList = rail;
        shell.addView(space(10, 1));

        workspace = vertical();
        shell.addView(workspace, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout top = horizontal(Gravity.CENTER_VERTICAL);
        workspace.addView(top, sized(-1, 48));
        LinearLayout brand = vertical();
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView wordmark = text("GLOWDECK", 19, WHITE, true);
        wordmark.setLetterSpacing(.16f);
        brand.addView(wordmark);
        TextView caption = text("WLED  /  TV CONTROL", 9, MUTED, true);
        caption.setLetterSpacing(.13f);
        brand.addView(caption);
        subheading = text("", 11, MUTED, false);
        subheading.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(subheading, new LinearLayout.LayoutParams(0, -1, 1));
        top.addView(space(12, 1));
        top.addView(button("+ Device", "add", () -> openDrawer("devices"), false), sized(100, 33));

        View rule = new View(this);
        rule.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams ruleLp = sized(-1, 1);
        ruleLp.topMargin = dp(3);
        ruleLp.bottomMargin = dp(7);
        workspace.addView(rule, ruleLp);
        controlList = vertical();
        workspace.addView(controlList, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void render() {
        if (navList == null || controlList == null) return;
        String focusTag = pendingFocusRestoration != null ? pendingFocusRestoration : focusedTag();
        pendingFocusRestoration = null;
        renderNavigation();
        if (tvMode) renderDashboard();
        else renderTouchDashboard();
        int online = 0;
        for (WledDevice d : devices) if (d.online) online++;
        subheading.setText(online + "/" + devices.size() + " online" + (scanning ? "  ·  Scanning…" : ""));
        if (tvMode && drawerOverlay == null) {
            final String returnTag = focusTag == null ? rememberedRailFocusTag : focusTag;
            controlList.post(() -> restoreFocus(returnTag, "nav:home"));
        }
        refreshVisibleState();
    }

    private void renderNavigation() {
        if (!tvMode) { renderTouchNavigation(); return; }
        navList.removeAllViews();
        String[][] entries = {
                {"home", "Home"}, {"devices", "Lights"}, {"favorites", "Favorites"},
                {"scenes", "Scenes"}, {"effects", "Effects"}, {"presets", "Presets"},
                {"settings", "Settings"}
        };
        for (String[] entry : entries) {
            boolean active = activeDrawer.equals(entry[0]) ||
                    (activeDrawer.isEmpty() && entry[0].equals("home"));
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(4), dp(2), dp(4));
            item.setBackground(shape(active ? 0xFF173A70 : 0xFF0A1325,
                    9, active ? 0xFF278CFF : 0xFF0A1325, active ? 1 : 0));
            item.setFocusable(true);
            item.setClickable(true);
            item.setTag("nav:" + entry[0]);
            item.setContentDescription(entry[1]);
            View icon = new NavIconView(entry[0], active ? 0xFF82CAFF : MUTED);
            item.addView(icon, sized(27, 26));
            TextView label = text(entry[1], 10, active ? WHITE : MUTED, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label, sized(-1, 18));
            item.setOnClickListener(v -> {
                if (entry[0].equals("home")) closeDrawer();
                else openDrawer(entry[0]);
            });
            setRailFocus(item, active);
            navList.addView(item, sized(-1, 53));
            navList.addView(space(1, 5));
        }
    }

    private void setRailFocus(View view, boolean selected) {
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) rememberFocus(v);
            int background = focused ? 0xFF224C88 : selected ? 0xFF173A70 : 0xFF0A1325;
            v.setBackground(shape(background, 9, focused ? ACCENT :
                    selected ? 0xFF278CFF : background, focused || selected ? 2 : 0));
        });
    }

    /** Small stroke icons are drawn natively, so no special fonts or dependencies are required. */
    private final class NavIconView extends View {
        private final String symbol;
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        NavIconView(String symbol, int color) {
            super(MainActivity.this);
            this.symbol = symbol;
            pen.setColor(color);
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(2.1f);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save();
            float scale = Math.min(getWidth(), getHeight()) / 28f;
            c.translate(getWidth() / 2f, getHeight() / 2f);
            c.scale(scale, scale);
            switch (symbol) {
                case "home": {
                    Path p = new Path();
                    p.moveTo(-11, -1); p.lineTo(0, -11); p.lineTo(11, -1);
                    p.moveTo(-8, -2); p.lineTo(-8, 10); p.lineTo(8, 10); p.lineTo(8, -2);
                    p.moveTo(-3, 10); p.lineTo(-3, 2); p.lineTo(3, 2); p.lineTo(3, 10);
                    c.drawPath(p, pen); break;
                }
                case "devices": {
                    c.drawCircle(0, -4, 8, pen);
                    c.drawLine(-5, 3, -4, 8, pen); c.drawLine(5, 3, 4, 8, pen);
                    c.drawLine(-4, 9, 4, 9, pen); c.drawLine(-3, 12, 3, 12, pen); break;
                }
                case "favorites": {
                    Path star = new Path();
                    for (int i = 0; i < 10; i++) {
                        double a = Math.PI * (-0.5 + i / 5.0);
                        float r = (i % 2 == 0) ? 12 : 5.4f;
                        float x = (float) (r * Math.cos(a));
                        float y = (float) (r * Math.sin(a));
                        if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                    }
                    star.close(); c.drawPath(star, pen); break;
                }
                case "scenes": {
                    for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
                        float x = -10 + col * 12, y = -10 + row * 12;
                        c.drawRoundRect(x, y, x + 8, y + 8, 1.5f, 1.5f, pen);
                    } break;
                }
                case "effects": {
                    c.drawLine(-8, 10, 7, -5, pen);
                    c.drawLine(5, -8, 5, -12, pen); c.drawLine(3, -10, 7, -10, pen);
                    c.drawLine(-8, -7, -8, -11, pen); c.drawLine(-10, -9, -6, -9, pen);
                    c.drawLine(10, 4, 10, 0, pen); c.drawLine(8, 2, 12, 2, pen); break;
                }
                case "presets": {
                    Path p = new Path();
                    p.moveTo(-11, -4); p.lineTo(0, -10); p.lineTo(11, -4); p.lineTo(0, 2); p.close();
                    p.moveTo(-11, 1); p.lineTo(0, 7); p.lineTo(11, 1);
                    p.moveTo(-11, 6); p.lineTo(0, 12); p.lineTo(11, 6);
                    c.drawPath(p, pen); break;
                }
                case "groups": {
                    c.drawCircle(-5, -5, 4.3f, pen); c.drawCircle(6, -5, 3.7f, pen);
                    c.drawArc(-13, 0, 2, 13, 185, 170, false, pen);
                    c.drawArc(-1, 1, 13, 12, 185, 170, false, pen); break;
                }
                case "settings": {
                    c.drawCircle(0, 0, 6.7f, pen);
                    c.drawCircle(0, 0, 2, pen);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        float x1 = (float) Math.cos(a) * 8.5f;
                        float y1 = (float) Math.sin(a) * 8.5f;
                        float x2 = (float) Math.cos(a) * 12f;
                        float y2 = (float) Math.sin(a) * 12f;
                        c.drawLine(x1, y1, x2, y2, pen);
                    } break;
                }
            }
            c.restore();
        }
    }

    private boolean multiTarget() { return "*".equals(selectedHost) || selectedHost.startsWith("@g:"); }

    private String selectedName() {
        if ("*".equals(selectedHost)) return "All lights";
        if (selectedHost.startsWith("@g:")) {
            for (SavedGroup g : savedGroups) if (("@g:" + g.id).equals(selectedHost)) return g.name;
        }
        WledDevice d = currentDevice();
        return d == null ? "Your lights" : d.name();
    }

    private void selectHost(String host) {
        selectedHost = host;
        selectedSegment = -1;
        dismissAllDrawers();
        render();
        WledDevice d = currentDevice();
        if (d != null) requestPresets(d);
    }

    // ---------- Adaptive Android tablet / phone UI (v1.5) ----------
    // TV uses the unchanged remote-first shell. Touch uses a scrollable dashboard,
    // an icon rail on tablets, and compact bottom navigation on phones.
    private void buildTouchShell() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundResource(R.drawable.glowdeck_night);
        setContentView(rootFrame);
        LinearLayout app = vertical();
        app.setPadding(dp(10), dp(8), dp(10), dp(8));
        rootFrame.addView(app, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout body = horizontal(Gravity.TOP);
        app.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (!touchNavAtBottom) {
            navList = vertical();
            navList.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            navList.setPadding(dp(3), dp(9), dp(3), dp(9));
            navList.setBackground(shape(0xFF0B1629, 13, BORDER, 1));
            body.addView(navList, sized(76, -1));
            body.addView(space(10, 1));
        }
        workspace = vertical();
        body.addView(workspace, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout top = horizontal(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(0), dp(4), dp(5));
        workspace.addView(top, sized(-1, 59));
        LinearLayout brand = vertical();
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = text("GLOWDECK", 20, WHITE, true);
        name.setLetterSpacing(.10f);
        brand.addView(name);
        brand.addView(text("WLED  ·  LIGHT CONTROL", 10, MUTED, true));
        subheading = text("", 12, MUTED, false);
        subheading.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(subheading, new LinearLayout.LayoutParams(0, -1, 1));
        top.addView(space(8, 1));
        top.addView(button("+", "add", () -> openDrawer("devices"), true), sized(48, 48));
        touchScroll = new ScrollView(this);
        touchScroll.setFillViewport(false);
        touchScroll.setVerticalScrollBarEnabled(false);
        workspace.addView(touchScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        controlList = vertical();
        controlList.setPadding(dp(3), dp(5), dp(3), dp(18));
        touchScroll.addView(controlList, new ScrollView.LayoutParams(-1, -2));

        if (touchNavAtBottom) {
            app.addView(space(1, 5));
            navList = horizontal(Gravity.CENTER_VERTICAL);
            navList.setBackground(shape(0xFF0B1629, 14, BORDER, 1));
            navList.setPadding(dp(3), dp(4), dp(3), dp(4));
            app.addView(navList, sized(-1, 62));
        }
    }

    private void renderTouchNavigation() {
        navList.removeAllViews();
        String[][] items = touchNavAtBottom ? new String[][] {
                {"home", "Home"}, {"devices", "Lights"}, {"favorites", "Faves"},
                {"scenes", "Scenes"}, {"settings", "More"}
        } : new String[][] {
                {"home", "Home"}, {"devices", "Lights"}, {"favorites", "Favorites"},
                {"scenes", "Scenes"}, {"effects", "Effects"},
                {"presets", "Presets"}, {"groups", "Groups"}, {"settings", "More"}
        };
        for (String[] entry : items) {
            boolean active = activeDrawer.equals(entry[0]) ||
                    (activeDrawer.isEmpty() && "home".equals(entry[0]));
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setBackground(shape(active ? 0xFF173A70 : 0xFF0B1629, 10,
                    active ? ACCENT : 0xFF0B1629, active ? 1 : 0));
            item.setFocusable(true);
            item.setClickable(true);
            item.setTag("nav:" + entry[0]);
            item.setContentDescription(entry[1]);
            item.addView(new NavIconView(entry[0].equals("settings") ? "settings" : entry[0],
                    active ? ACCENT : MUTED), sized(25, 25));
            TextView label = text(entry[1], touchNavAtBottom ? 10 : 11,
                    active ? WHITE : MUTED, active);
            label.setGravity(Gravity.CENTER);
            item.addView(label, sized(-1, 20));
            item.setOnClickListener(v -> {
                if ("home".equals(entry[0])) { if (drawerOverlay != null) dismissAllDrawers();
                    if (touchScroll != null) touchScroll.smoothScrollTo(0, 0); }
                else openDrawer(entry[0]);
            });
            setRailFocus(item, active);
            if (touchNavAtBottom) navList.addView(item,
                    new LinearLayout.LayoutParams(0, dp(54), 1));
            else { navList.addView(item, sized(-1, 58)); navList.addView(space(1, 5)); }
        }
    }

    private LinearLayout touchCard(String title, String link, Runnable action) {
        LinearLayout card = compactPanel();
        card.setPadding(dp(14), dp(12), dp(14), dp(13));
        LinearLayout head = horizontal(Gravity.CENTER_VERTICAL);
        head.addView(text(title, 13, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (link != null) head.addView(button(link, "touch:more:" + title,
                action, false), sized(105, 48));
        card.addView(head, sized(-1, 48));
        card.addView(space(1, 6));
        return card;
    }

    private void renderTouchDashboard() {
        // No global scroll reset: keep reading position during structural refresh.
        int y = touchScroll == null ? 0 : touchScroll.getScrollY();
        controlList.removeAllViews();
        WledDevice d = currentDevice();
        if (d == null && !multiTarget()) { selectedHost = "*"; d = currentDevice(); }
        final WledDevice device = d;
        boolean twoColumns = wideTouchLayout;
        LinearLayout hero = horizontal(Gravity.CENTER_VERTICAL);
        LinearLayout label = vertical();
        TextView name = text(selectedName(), touchNavAtBottom ? 23 : 26, WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTag("live:title");
        label.addView(name);
        TextView status = text(multiTarget() ? targets().size() + " controllers" :
                device == null ? "Add a controller" :
                        (device.online ? "● Online" : "○ Offline") + "  ·  " + device.host,
                12, device != null && !device.online ? BAD : MUTED, false);
        status.setTag("live:connection");
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.addView(status);
        hero.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(button("Lights  ▾", "hero:devices", () -> openDrawer("devices"),
                false), sized(touchNavAtBottom ? 92 : 110, 48));
        controlList.addView(hero, sized(-1, 65));
        controlList.addView(space(1, 12));
        if (devices.isEmpty()) {
            LinearLayout empty = touchCard("Get started", null, null);
            empty.addView(text("Add a WLED device by IP address or scan your Wi-Fi network.", 14, MUTED, false));
            empty.addView(space(1, 15));
            empty.addView(button("Find or add lights", "touch:add", () -> openDrawer("devices"), true), sized(-1, 52));
            controlList.addView(empty);
            return;
        }
        addTouchFavorites(controlList);
        controlList.addView(space(1, 12));
        addTouchScenes(controlList);
        controlList.addView(space(1, 12));
        if (twoColumns) {
            LinearLayout columns = horizontal(Gravity.TOP);
            LinearLayout left = vertical(), right = vertical();
            columns.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            columns.addView(space(12, 1));
            columns.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
            controlList.addView(columns);
            addTouchPower(left, device);
            left.addView(space(1, 12));
            addTouchColors(left);
            addTouchEffects(right, device);
            right.addView(space(1, 12));
            addTouchAnimation(right, device);
            controlList.addView(space(1, 12));
            addTouchPresets(controlList, device);
        } else {
            addTouchPower(controlList, device);
            controlList.addView(space(1, 12));
            addTouchColors(controlList);
            controlList.addView(space(1, 12));
            addTouchPresets(controlList, device);
            controlList.addView(space(1, 12));
            addTouchEffects(controlList, device);
            controlList.addView(space(1, 12));
            addTouchAnimation(controlList, device);
        }
        if (touchScroll != null && y != 0) touchScroll.post(() -> touchScroll.scrollTo(0, y));
    }

    /** One-tap favorites are available in the main dashboard on every device type. */
    private void addTouchFavorites(LinearLayout parent) {
        LinearLayout card = touchCard("★  FAVORITES", "Manage  ›", () -> openDrawer("favorites"));
        if (favorites.isEmpty()) {
            card.addView(text("Keep your most-used lighting actions here.", 13, MUTED, false));
            card.addView(space(1, 9));
            card.addView(button("+ Add first favorite", "favorite:add:home", this::addFavoriteDialog, true),
                    sized(-1, 49));
        } else {
            int columns = touchNavAtBottom ? 2 : 3;
            for (int i = 0; i < Math.min(6, favorites.size()); i += columns) {
                LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
                for (int j = 0; j < columns && i + j < Math.min(6, favorites.size()); j++) {
                    Favorite favorite = favorites.get(i + j);
                    TextView action = button("★  " + favorite.name, "favorite:" + favorite.id,
                            () -> runFavorite(favorite), false);
                    action.setContentDescription("Run " + favorite.name + " on " + targetDescription(favorite.target));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
                    if (j > 0) lp.leftMargin = dp(7);
                    row.addView(action, lp);
                }
                card.addView(row);
                if (i + columns < Math.min(6, favorites.size())) card.addView(space(1, 8));
            }
        }
        parent.addView(card, sized(-1, -2));
    }

    // -------- GlowDeck multi-device scene builder (v1.6) --------

    private GlowScene sceneById(String id) {
        for (GlowScene scene : scenes) if (scene.id.equals(id)) return scene;
        return null;
    }

    private void addTouchScenes(LinearLayout parent) {
        LinearLayout card = touchCard("▦  SCENES", "Manage  ›", () -> openDrawer("scenes"));
        if (scenes.isEmpty()) {
            card.addView(text("Capture different looks across lights, then activate them together.", 13, MUTED, false));
            card.addView(space(1, 9));
            card.addView(button("+ Create a scene", "scene:add:home", this::createSceneDialog, true), sized(-1, 49));
        } else {
            int columns = touchNavAtBottom ? 2 : 3;
            for (int i = 0; i < Math.min(6, scenes.size()); i += columns) {
                LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
                for (int j = 0; j < columns && i + j < Math.min(6, scenes.size()); j++) {
                    GlowScene scene = scenes.get(i + j);
                    TextView button = button("▦ " + scene.name, "glowscene:" + scene.id,
                            () -> playScene(scene), false);
                    button.setContentDescription("Activate " + scene.name + " across " + scene.targets.size() + " lights");
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
                    if (j > 0) lp.leftMargin = dp(7);
                    row.addView(button, lp);
                }
                card.addView(row);
                if (i + columns < Math.min(6, scenes.size())) card.addView(space(1, 8));
            }
        }
        parent.addView(card, sized(-1, -2));
    }

    private void renderScenesDrawer(LinearLayout list, LinearLayout sheet) {
        drawerInfo(list, "Each scene stores a different look for every selected WLED controller. Editing never changes the live lights.");
        if (scenes.isEmpty()) drawerInfo(list, "No scenes saved. Arrange your WLED lights, then capture their current looks.");
        for (int i = 0; i < scenes.size(); i++) {
            GlowScene scene = scenes.get(i);
            list.addView(drawerItem("▶  " + scene.name,
                    scene.targets.size() + " lights  ·  " + scene.transitionMs + " ms transition",
                    i == 0 ? "drawer:first" : "glowscene:" + scene.id,
                    () -> { dismissAllDrawers(); playScene(scene); }, false), lpBottom(5));
            list.addView(button("Edit / favorite  ·  " + scene.name,
                    "glowscene:edit:" + scene.id, () -> editSceneDialog(scene), false), lpBottom(12));
        }
        sheet.addView(space(1, 8));
        sheet.addView(button("+ Capture new scene", "glowscene:add", this::createSceneDialog, true),
                sized(-1, tvMode ? 43 : 49));
    }

    private void createSceneDialog() {
        if (devices.isEmpty()) { toast("Add WLED lights before creating a scene"); return; }
        if (scenes.size() >= 50) { toast("Maximum 50 scenes. Delete one to create another."); return; }
        askText("New scene name", "e.g. Movie Night", "Movie Night", name -> {
            String label = name.trim();
            if (label.isEmpty()) { toast("Scene name is required"); return; }
            GlowScene scene = GlowScene.create(label.length() > 48 ? label.substring(0, 48) : label, 700);
            selectSceneMembers(scene, true);
        });
    }

    private void selectSceneMembers(GlowScene scene, boolean creating) {
        if (devices.isEmpty()) { toast("No WLED lights configured"); return; }
        String[] labels = new String[devices.size()];
        boolean[] checked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            WledDevice d = devices.get(i);
            labels[i] = d.name() + "  ·  " + (d.online ? "Connected" : "Offline") + "  ·  " + d.host;
            // New scene defaults to all lights, or the currently selected group.
            boolean selected = creating && ("*".equals(selectedHost) ||
                    targets().contains(d));
            if (!creating) for (GlowScene.Target item : scene.targets)
                if (item.deviceId.equals(d.identity()) || item.lastHost.equals(d.host)) selected = true;
            checked[i] = selected;
        }
        new AlertDialog.Builder(this)
                .setTitle(creating ? "Choose lights to capture" : "Choose lights in " + scene.name)
                .setMultiChoiceItems(labels, checked, (dialog, index, enabled) -> checked[index] = enabled)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(creating ? "Capture looks" : "Save selection", (dialog, button) -> {
                    List<WledDevice> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) selected.add(devices.get(i));
                    if (selected.isEmpty()) { toast("Select at least one light"); return; }
                    if (creating) captureNewScene(scene, selected);
                    else updateSceneMembers(scene, selected);
                }).show();
    }

    /** Capture actual /json state rather than the simplified dashboard model. */
    private void captureNewScene(GlowScene scene, List<WledDevice> selected) {
        message = "Capturing " + selected.size() + " WLED looks…";
        refreshVisibleState();
        network.execute(() -> {
            List<GlowScene.Target> captured = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (WledDevice d : selected) {
                try { captured.add(GlowScene.Target.capture(d, WledApi.snapshot(d.host))); }
                catch (Exception e) { errors.add(d.name() + ": " + concise(e)); }
            }
            ui.post(() -> {
                if (!errors.isEmpty() || captured.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Scene not saved")
                            .setMessage("Every selected light must be captured successfully. " +
                                    "No scene has been saved or lights changed.\n\n" +
                                    android.text.TextUtils.join("\n", errors))
                            .setPositiveButton("Retry", (x, y) -> captureNewScene(scene, selected))
                            .setNegativeButton("Cancel", null).show();
                    return;
                }
                scene.targets.addAll(captured);
                scenes.add(scene);
                saveAndShowScenes();
                toast("Saved scene: " + scene.name + " (" + scene.targets.size() + " lights)");
            });
        });
    }

    /** Membership editing keeps existing saved looks; captures only newly included lights. */
    private void updateSceneMembers(GlowScene scene, List<WledDevice> selected) {
        network.execute(() -> {
            List<GlowScene.Target> newTargets = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (WledDevice d : selected) {
                GlowScene.Target old = null;
                for (GlowScene.Target target : scene.targets)
                    if (target.deviceId.equals(d.identity()) || target.lastHost.equals(d.host)) { old = target; break; }
                if (old != null) newTargets.add(old);
                else try { newTargets.add(GlowScene.Target.capture(d, WledApi.snapshot(d.host))); }
                catch (Exception e) { errors.add(d.name() + ": " + concise(e)); }
            }
            ui.post(() -> {
                if (!errors.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Selection not changed")
                            .setMessage("Newly added lights could not be captured. Existing scene is unchanged.\n\n" +
                                    android.text.TextUtils.join("\n", errors))
                            .setPositiveButton("OK", null).show();
                    return;
                }
                scene.targets.clear();
                scene.targets.addAll(newTargets);
                saveAndShowScenes();
                toast("Scene updated. No live lights changed.");
            });
        });
    }

    private void editSceneDialog(GlowScene scene) {
        new AlertDialog.Builder(this).setTitle(scene.name)
                .setItems(new String[]{"▶ Play scene", "Edit individual lights", "Change included lights",
                        "Rename", "Transition speed", "★ Add to Favorites", "Duplicate", "Delete scene"},
                        (dialog, which) -> {
                            if (which == 0) { dismissAllDrawers(); playScene(scene); }
                            else if (which == 1) editSceneLights(scene);
                            else if (which == 2) selectSceneMembers(scene, false);
                            else if (which == 3) askText("Rename scene", "Scene name", scene.name, name -> {
                                String value = name.trim();
                                if (!value.isEmpty()) { scene.name = value.length() > 48 ? value.substring(0, 48) : value;
                                    saveAndShowScenes(); }
                            });
                            else if (which == 4) editSceneTransition(scene);
                            else if (which == 5) favoriteScene(scene);
                            else if (which == 6) {
                                if (scenes.size() >= 50) { toast("Maximum 50 scenes"); return; }
                                try {
                                    GlowScene duplicate = GlowScene.create(scene.name + " Copy", scene.transitionMs);
                                    for (GlowScene.Target t : scene.targets)
                                        duplicate.targets.add(GlowScene.Target.fromJson(t.toJson()));
                                    scenes.add(duplicate);
                                    saveAndShowScenes();
                                } catch (JSONException e) { toast("Could not duplicate scene"); }
                            } else if (which == 7) new AlertDialog.Builder(this)
                                    .setTitle("Delete " + scene.name + "?")
                                    .setMessage("This only deletes the saved scene. WLED lights and presets won't change. " +
                                            "Related favorites will become unavailable until removed.")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        scenes.remove(scene); saveAndShowScenes();
                                    }).setNegativeButton("Cancel", null).show();
                        }).setNegativeButton("Close", null).show();
    }

    private void editSceneLights(GlowScene scene) {
        String[] labels = new String[scene.targets.size()];
        for (int i = 0; i < labels.length; i++) {
            GlowScene.Target t = scene.targets.get(i);
            labels[i] = t.name + " · " + (t.state.optBoolean("on") ? "On" : "Off") +
                    " · " + Math.round(t.state.optInt("bri", 128) * 100f / 255f) + "%";
        }
        new AlertDialog.Builder(this).setTitle("Edit light looks · " + scene.name)
                .setItems(labels, (dialog, index) -> editSceneLight(scene, index))
                .setNegativeButton("Close", null).show();
    }

    private void editSceneLight(GlowScene scene, int index) {
        if (index < 0 || index >= scene.targets.size()) return;
        GlowScene.Target t = scene.targets.get(index);
        new AlertDialog.Builder(this).setTitle(t.name + " · " + scene.name)
                .setItems(new String[]{"Turn on", "Turn off", "Brightness", "Quick color", "Effect",
                        "Capture live look again", "Remove this light"}, (dialog, which) -> {
                    try {
                        if (which == 0 || which == 1) replaceSceneTarget(scene, index, t.withPower(which == 0));
                        if (which == 2) {
                            String[] labels = {"10%", "25%", "40%", "60%", "75%", "100%"};
                            int[] values = {10, 25, 40, 60, 75, 100};
                            new AlertDialog.Builder(this).setTitle(t.name + " brightness")
                                    .setItems(labels, (d, i) -> {
                                        try { replaceSceneTarget(scene, index,
                                                t.withBrightness(Math.round(values[i] * 255f / 100f))); }
                                        catch (JSONException e) { toast("Could not update scene"); }
                                    }).setNegativeButton("Cancel", null).show();
                        }
                        if (which == 3) new AlertDialog.Builder(this)
                                .setTitle(t.name + " color")
                                .setItems(COLOR_NAMES, (d, i) -> {
                                    try { replaceSceneTarget(scene, index,
                                            t.withColor(COLOR_VALUES[i] & 0xFFFFFF)); }
                                    catch (JSONException e) { toast("Could not update scene"); }
                                }).setNegativeButton("Cancel", null).show();
                        if (which == 4) {
                            WledDevice device = SceneExecutor.findDevice(t, devices);
                            if (device == null || device.effects.isEmpty()) {
                                toast("Connect this WLED light to load its effects first"); return;
                            }
                            List<String> options = new ArrayList<>();
                            for (String effect : device.effects)
                                if (validOption(effect)) options.add(effect);
                            new AlertDialog.Builder(this).setTitle(t.name + " effect")
                                    .setItems(options.toArray(new String[0]), (d, i) -> {
                                        try { replaceSceneTarget(scene, index, t.withEffect(options.get(i))); }
                                        catch (JSONException e) { toast("Could not update scene"); }
                                    }).setNegativeButton("Cancel", null).show();
                        }
                        if (which == 5) recaptureSceneLight(scene, index);
                        if (which == 6) {
                            if (scene.targets.size() == 1) {
                                toast("A scene needs at least one light. Delete the scene instead."); return;
                            }
                            new AlertDialog.Builder(this).setTitle("Remove " + t.name + " from scene?")
                                    .setMessage("Only the saved scene is modified; the actual light won't change.")
                                    .setPositiveButton("Remove", (d, w) -> {
                                        scene.targets.remove(index); saveAndShowScenes();
                                    }).setNegativeButton("Cancel", null).show();
                        }
                    } catch (JSONException e) { toast("Could not update scene"); }
                }).setNegativeButton("Close", null).show();
    }

    private void replaceSceneTarget(GlowScene scene, int index, GlowScene.Target target) {
        if (!scenes.contains(scene) || index < 0 || index >= scene.targets.size()) return;
        scene.targets.set(index, target);
        saveAndShowScenes();
        toast("Saved " + target.name + " · No live changes");
    }

    private void recaptureSceneLight(GlowScene scene, int index) {
        GlowScene.Target target = scene.targets.get(index);
        WledDevice device = SceneExecutor.findDevice(target, devices);
        if (device == null) { toast("WLED device is missing. Rescan lights first."); return; }
        network.execute(() -> {
            try {
                GlowScene.Target updated = GlowScene.Target.capture(device, WledApi.snapshot(device.host));
                ui.post(() -> replaceSceneTarget(scene, index, updated));
            } catch (Exception e) { ui.post(() -> toast("Capture failed: " + concise(e))); }
        });
    }

    private void editSceneTransition(GlowScene scene) {
        int[] times = {0, 300, 700, 1500, 3000, 5000};
        String[] titles = {"Instant", "0.3 seconds", "0.7 seconds", "1.5 seconds", "3 seconds", "5 seconds"};
        new AlertDialog.Builder(this).setTitle("Transition · " + scene.name)
                .setItems(titles, (dialog, which) -> { scene.transitionMs = times[which];
                    saveAndShowScenes(); }).setNegativeButton("Cancel", null).show();
    }

    private void chooseSceneToFavorite() {
        if (scenes.isEmpty()) { toast("Create a GlowDeck scene first"); return; }
        String[] names = new String[scenes.size()];
        for (int i = 0; i < names.length; i++) names[i] = scenes.get(i).name;
        new AlertDialog.Builder(this).setTitle("Favorite GlowDeck scene")
                .setItems(names, (dialog, which) -> favoriteScene(scenes.get(which)))
                .setNegativeButton("Cancel", null).show();
    }

    private void favoriteScene(GlowScene scene) {
        if (favorites.size() >= 50) { toast("Maximum 50 favorites"); return; }
        for (Favorite f : favorites) if (Favorite.SCENE.equals(f.action) &&
                f.target.equals("scene:" + scene.id)) { toast("Scene already in Favorites"); return; }
        favorites.add(Favorite.createScene(scene.name, scene.id));
        saveAndShowFavorites();
        toast("Added ★ " + scene.name);
    }

    private void saveAndShowScenes() {
        store.saveScenes(scenes);
        render();
        if (drawerOverlay != null && "scenes".equals(activeDrawer)) showDrawer();
    }

    private void playScene(GlowScene scene) {
        if (scene.targets.isEmpty()) { toast("This scene contains no lights"); return; }
        message = "Applying " + scene.name + " to " + scene.targets.size() + " lights…";
        refreshVisibleState();
        List<WledDevice> available = new ArrayList<>(devices);
        network.execute(() -> {
            SceneExecutor.Result result = SceneExecutor.execute(scene, available,
                    WledApi::snapshot, (host, patch) -> WledApi.send(host, patch));
            ui.post(() -> {
                message = scene.name + ": " + result.summary(scene.targets.size());
                toast(message);
                if (!result.problems.isEmpty()) new AlertDialog.Builder(this)
                        .setTitle("Scene applied with issues")
                        .setMessage(result.summary(scene.targets.size()) + "\n\n" +
                                android.text.TextUtils.join("\n", result.problems))
                        .setPositiveButton("OK", null).show();
                refreshVisibleState();
                refreshAll();
            });
        });
    }

    private String targetDescription(String target) {
        if (target.startsWith("scene:")) {
            GlowScene scene = sceneById(target.substring(6));
            return scene == null ? "missing scene" : scene.name;
        }
        if ("*".equals(target)) return "all lights";
        if (target.startsWith("@g:")) for (SavedGroup group : savedGroups) {
            if (target.equals("@g:" + group.id)) return group.name;
        }
        for (WledDevice d : devices) if (d.host.equals(target)) return d.name();
        return "missing device or group";
    }

    private void renderFavoritesDrawer(LinearLayout list, LinearLayout sheet) {
        drawerInfo(list, "Tap or press OK to run. Add uses the currently selected light or group: " + selectedName());
        if (favorites.isEmpty()) drawerInfo(list, "No favorites yet. Add a power, color, brightness, or preset shortcut.");
        for (int i = 0; i < favorites.size(); i++) {
            Favorite f = favorites.get(i);
            list.addView(drawerItem("★  " + f.name, f.detail() + "  ·  " + targetDescription(f.target),
                    i == 0 ? "drawer:first" : "favorite:run:" + f.id,
                    () -> { dismissAllDrawers(); runFavorite(f); }, false), lpBottom(4));
            list.addView(button("Edit / reorder  ·  " + f.name, "favorite:edit:" + f.id,
                    () -> showFavoriteActions(f), false), lpBottom(11));
        }
        sheet.addView(space(1, 8));
        sheet.addView(button("+ Add favorite", "favorite:add", this::addFavoriteDialog, true), sized(-1, tvMode ? 43 : 49));
    }

    private void addFavoriteDialog() {
        if (devices.isEmpty()) { toast("Add a WLED device first"); return; }
        final String target = selectedHost;
        final WledDevice d = currentDevice();
        final String targetLabel = selectedName();
        new AlertDialog.Builder(this).setTitle("Add favorite for " + targetLabel)
                .setItems(new String[]{"Power ON", "Power OFF", "Set brightness", "Set quick color", "WLED preset", "GlowDeck scene"},
                        (dialog, choice) -> {
                            if (choice == 0) nameAndSaveFavorite(target, Favorite.POWER, 1, "", -1, targetLabel + " On");
                            else if (choice == 1) nameAndSaveFavorite(target, Favorite.POWER, 0, "", -1, targetLabel + " Off");
                            else if (choice == 2) {
                                int[] levels = {25, 50, 75, 100};
                                new AlertDialog.Builder(this).setTitle("Brightness • " + targetLabel)
                                        .setItems(new String[]{"25%", "50%", "75%", "100%"}, (unused, idx) -> {
                                            int level = Math.max(1, Math.round(levels[idx] * 255f / 100f));
                                            nameAndSaveFavorite(target, Favorite.BRIGHTNESS, level, "", -1,
                                                    targetLabel + " " + levels[idx] + "%");
                                        }).setNegativeButton("Cancel", null).show();
                            } else if (choice == 3) {
                                new AlertDialog.Builder(this).setTitle("Quick color • " + targetLabel)
                                        .setItems(COLOR_NAMES, (unused, idx) -> nameAndSaveFavorite(target,
                                                Favorite.COLOR, COLOR_VALUES[idx] & 0xFFFFFF, "", -1,
                                                targetLabel + " " + COLOR_NAMES[idx]))
                                        .setNegativeButton("Cancel", null).show();
                            } else if (choice == 5) {
                                chooseSceneToFavorite();
                            } else {
                                if (d == null || d.presets.isEmpty()) {
                                    if (d != null) requestPresets(d);
                                    toast("Presets are loading. Try again when your WLED presets appear.");
                                    return;
                                }
                                String[] names = new String[d.presets.size()];
                                for (int i = 0; i < names.length; i++) names[i] = d.presets.get(i).name;
                                new AlertDialog.Builder(this).setTitle("Favorite WLED preset")
                                        .setItems(names, (unused, idx) -> {
                                            WledDevice.Preset preset = d.presets.get(idx);
                                            nameAndSaveFavorite(target, Favorite.PRESET, 0,
                                                    preset.name, preset.id, preset.name);
                                        }).setNegativeButton("Cancel", null).show();
                            }
                        }).setNegativeButton("Cancel", null).show();
    }

    private void nameAndSaveFavorite(String target, String kind, int value,
                                     String presetName, int presetId, String suggestedName) {
        if (favorites.size() >= 50) { toast("Maximum 50 favorites. Remove an old one first."); return; }
        askText("Name this favorite", "e.g. Movie Night", suggestedName, name -> {
            String label = name.trim();
            if (label.isEmpty()) { toast("Favorite name cannot be blank"); return; }
            if (label.length() > 48) label = label.substring(0, 48);
            Favorite favorite = Favorite.create(label, target, kind, value, presetName, presetId);
            favorites.add(favorite);
            saveAndShowFavorites();
            toast("Saved ★ " + favorite.name);
        });
    }

    private void showFavoriteActions(Favorite favorite) {
        new AlertDialog.Builder(this).setTitle(favorite.name)
                .setItems(new String[]{"Run now", "Rename", "Move up", "Move down", "Remove"}, (dialog, which) -> {
                    int pos = favorites.indexOf(favorite);
                    if (pos < 0) return;
                    if (which == 0) { dismissAllDrawers(); runFavorite(favorite); return; }
                    if (which == 1) {
                        askText("Rename favorite", "Favorite name", favorite.name, text -> {
                            if (text.trim().isEmpty()) return;
                            int index = favorites.indexOf(favorite);
                            if (index < 0) return;
                            String label = text.trim();
                            favorites.set(index, favorite.renamed(label.length() > 48 ? label.substring(0, 48) : label));
                            saveAndShowFavorites();
                        });
                        return;
                    }
                    if (which == 2 && pos > 0) Collections.swap(favorites, pos, pos - 1);
                    if (which == 3 && pos < favorites.size() - 1) Collections.swap(favorites, pos, pos + 1);
                    if (which == 4) {
                        new AlertDialog.Builder(this).setTitle("Remove " + favorite.name + "?")
                                .setMessage("Only the shortcut is removed; your WLED device and presets are unchanged.")
                                .setPositiveButton("Remove", (d, w) -> {
                                    favorites.remove(favorite); saveAndShowFavorites();
                                }).setNegativeButton("Cancel", null).show();
                        return;
                    }
                    saveAndShowFavorites();
                }).show();
    }

    private void saveAndShowFavorites() {
        store.saveFavorites(favorites);
        render();
        if (drawerOverlay != null && "favorites".equals(activeDrawer)) showDrawer();
    }

    private List<WledDevice> favoriteTargets(Favorite favorite) {
        List<WledDevice> result = new ArrayList<>();
        if ("*".equals(favorite.target)) return new ArrayList<>(devices);
        if (favorite.target.startsWith("@g:")) {
            for (SavedGroup g : savedGroups) if (favorite.target.equals("@g:" + g.id)) {
                for (WledDevice d : devices) if (g.hosts.contains(d.host)) result.add(d);
                return result;
            }
            return result;
        }
        for (WledDevice d : devices) if (favorite.target.equals(d.host)) result.add(d);
        return result;
    }

    private void runFavorite(Favorite favorite) {
        if (Favorite.SCENE.equals(favorite.action)) {
            GlowScene scene = sceneById(favorite.target.substring(6));
            if (scene != null) playScene(scene);
            else toast("Scene shortcut unavailable. Open Favorites to remove it.");
            return;
        }
        List<WledDevice> target = favoriteTargets(favorite);
        if (target.isEmpty()) { toast("Favorite unavailable: " + targetDescription(favorite.target)); return; }
        message = "Activating " + favorite.name + "…";
        refreshVisibleState();
        network.execute(() -> {
            ActionDispatcher.Result result = ActionDispatcher.execute(favorite, target, WledApi::send);
            ui.post(() -> {
                String status = result.successful + "/" + target.size() + " lights updated";
                if (result.unsupported > 0) status += ", " + result.unsupported + " unsupported";
                if (!result.errors.isEmpty()) status += ", " + result.errors.size() + " offline/failed";
                message = favorite.name + ": " + status;
                toast(message);
                refreshVisibleState();
            });
            for (WledDevice d : target) fetchDevice(d);
        });
    }

    private void addTouchPower(LinearLayout parent, WledDevice d) {
        LinearLayout card = touchCard("POWER  &  BRIGHTNESS", null, null);
        LinearLayout buttons = horizontal(Gravity.CENTER_VERTICAL);
        buttons.addView(button("ON", "power:on", () -> { setPower(true); refreshVisibleState(); },
                d != null && d.on), new LinearLayout.LayoutParams(0, dp(50), 1));
        buttons.addView(space(9, 1));
        buttons.addView(button("OFF", "power:off", () -> { setPower(false); refreshVisibleState(); },
                d == null || !d.on), new LinearLayout.LayoutParams(0, dp(50), 1));
        card.addView(buttons);
        card.addView(space(1, 15));
        card.addView(touchSeek("Brightness", "touch:bri", d == null ? 128 : d.brightness, this::setBrightnessLevel));
        if (!multiTarget() && d != null && d.segments.size() > 1) {
            card.addView(space(1, 9));
            card.addView(button("LED segment  ▾", "touch:segment", this::showSegments, false), sized(-1, 49));
        }
        parent.addView(card, sized(-1, -2));
    }

    private void addTouchColors(LinearLayout parent) {
        LinearLayout card = touchCard("QUICK COLORS", "Palettes  ›", () -> openDrawer("palettes"));
        for (int r = 0; r < 2; r++) {
            LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
            for (int c = 0; c < 4; c++) {
                int idx = r * 4 + c;
                int color = COLOR_VALUES[idx];
                LinearLayout item = vertical();
                item.setGravity(Gravity.CENTER);
                item.setBackground(shape(TILE, 9, BORDER, 1));
                item.setFocusable(true);
                item.setClickable(true);
                item.setTag("color:" + idx);
                item.setContentDescription("Set " + COLOR_NAMES[idx] + " color");
                View spot = new View(this);
                spot.setBackground(shape(color, 18, color, 0));
                item.addView(spot, sized(27, 27));
                item.addView(space(1, 4));
                TextView name = text(COLOR_NAMES[idx], 10, WHITE, false);
                name.setGravity(Gravity.CENTER);
                item.addView(name);
                item.setOnClickListener(v -> setColor(color));
                setFocusStyle(item, TILE, 9);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(70), 1);
                if (c > 0) lp.leftMargin = dp(6);
                row.addView(item, lp);
            }
            card.addView(row);
            if (r == 0) card.addView(space(1, 7));
        }
        parent.addView(card, sized(-1, -2));
    }

    private void addTouchEffects(LinearLayout parent, WledDevice d) {
        LinearLayout card = touchCard("EFFECTS", "Browse all  ›", () -> openDrawer("effects"));
        List<Integer> quick = d == null ? Collections.emptyList() :
                popularIds(d.effects, POPULAR_EFFECTS, 6);
        if (quick.isEmpty()) card.addView(text("Connect to load WLED effects", 13, MUTED, false));
        else for (int i = 0; i < quick.size(); i += 2) {
            LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
            for (int col = 0; col < 2 && i + col < quick.size(); col++) {
                int fx = quick.get(i + col);
                LinearLayout fxView = effectTile(d.effects.get(fx), fx, d.effect == fx);
                fxView.setMinimumHeight(dp(54));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1);
                if (col > 0) lp.leftMargin = dp(8);
                row.addView(fxView, lp);
            }
            card.addView(row);
            if (i + 2 < quick.size()) card.addView(space(1, 8));
        }
        parent.addView(card, sized(-1, -2));
    }

    private void addTouchAnimation(LinearLayout parent, WledDevice d) {
        LinearLayout card = touchCard("ANIMATION", "More  ›", () -> openDrawer("settings"));
        card.addView(touchSeek("Speed", "touch:speed", d == null ? 128 : d.speed, this::setSpeedLevel));
        card.addView(space(1, 8));
        card.addView(touchSeek("Intensity", "touch:intensity", d == null ? 128 : d.intensity,
                this::setIntensityLevel));
        parent.addView(card, sized(-1, -2));
    }

    private void addTouchPresets(LinearLayout parent, WledDevice d) {
        LinearLayout card = touchCard("SAVED PRESETS", "View all  ›", () -> openDrawer("presets"));
        if (d == null || d.presets.isEmpty()) {
            card.addView(text("Your saved WLED presets will appear here.", 13, MUTED, false));
        } else {
            int limit = Math.min(4, d.presets.size());
            for (int i = 0; i < limit; i += 2) {
                LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
                for (int j = 0; j < 2 && i + j < limit; j++) {
                    WledDevice.Preset preset = d.presets.get(i + j);
                    TextView chip = button("✧  " + preset.name, "scene:" + preset.id,
                            () -> applyPreset(preset), !multiTarget() && d.currentPreset == preset.id);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(49), 1);
                    if (j > 0) lp.leftMargin = dp(7);
                    row.addView(chip, lp);
                }
                card.addView(row);
                if (i + 2 < limit) card.addView(space(1, 7));
            }
        }
        parent.addView(card, sized(-1, -2));
    }

    private interface LevelSelected { void set(int value); }

    private LinearLayout touchSeek(String label, String tag, int level, LevelSelected onChange) {
        LinearLayout group = vertical();
        LinearLayout head = horizontal(Gravity.CENTER_VERTICAL);
        head.addView(text(label, 13, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = text(Math.round(level * 100f / 255f) + "%", 14, ACCENT, true);
        amount.setTag(tag + ":value");
        head.addView(amount);
        group.addView(head);
        SeekBar seek = new SeekBar(this);
        seek.setMax(255);
        seek.setProgress(level);
        seek.setTag(tag);
        // The layout already provides a 48dp touch target. Do not call
        // SeekBar.setMinHeight(): this method is absent on older Fire OS.
        seek.setContentDescription(label);
        seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (user) amount.setText(Math.round(progress * 100f / 255f) + "%");
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                onChange.set(bar.getProgress()); // One WLED command per completed gesture.
            }
        });
        group.addView(seek, sized(-1, 48));
        return group;
    }

    private void setBrightnessLevel(int value) {
        int n = clamp(value, 1, 255);
        for (WledDevice d : targets()) d.brightness = n;
        send("brightness", d -> WledApi.object("bri", n));
    }
    private void setSpeedLevel(int value) {
        int n = clamp(value, 0, 255);
        for (WledDevice d : targets()) d.speed = n;
        send("speed", d -> segmentPatch(d, new JSONObject().put("sx", n)));
    }
    private void setIntensityLevel(int value) {
        int n = clamp(value, 0, 255);
        for (WledDevice d : targets()) d.intensity = n;
        send("intensity", d -> segmentPatch(d, new JSONObject().put("ix", n)));
    }

    /** Single-screen home: two weighted columns plus a small preset shortcut strip. */
    private void renderDashboard() {
        controlList.removeAllViews();
        WledDevice base = currentDevice();
        if (base == null && !multiTarget()) selectedHost = "*";
        base = currentDevice();
        final WledDevice device = base;
        final boolean multi = multiTarget();

        LinearLayout hero = horizontal(Gravity.CENTER_VERTICAL);
        LinearLayout heroName = vertical();
        hero.addView(heroName, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text(selectedName(), 23, WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTag("live:title");
        heroName.addView(title);
        String connection = multi ? targets().size() + " WLED controllers" :
                device == null ? "Find your WLED devices" :
                        (device.online ? "● Online" : "○ Offline") + "  ·  " + device.host +
                                (device.version.isEmpty() ? "" : "  ·  WLED " + device.version);
        TextView connectionLabel = text(connection, 11,
                !multi && device != null && !device.online ? BAD : MUTED, false);
        connectionLabel.setTag("live:connection");
        heroName.addView(connectionLabel);
        hero.addView(button("◉  Devices", "hero:devices", () -> openDrawer("devices"), false), sized(109, 35));
        controlList.addView(hero, sized(-1, 54));
        controlList.addView(space(1, 7));

        if (devices.isEmpty()) {
            LinearLayout empty = panel();
            empty.addView(text("Get started with WLED", 19, WHITE, true));
            empty.addView(text("Add a device by IP address or discover one on your network.", 13, MUTED, false));
            empty.addView(space(1, 15));
            empty.addView(button("Open device drawer", "empty:add", () -> openDrawer("devices"), true), sized(-1, 46));
            controlList.addView(empty);
            return;
        }

        LinearLayout twoColumns = horizontal(Gravity.TOP);
        controlList.addView(twoColumns, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout left = vertical();
        LinearLayout right = vertical();
        twoColumns.addView(left, new LinearLayout.LayoutParams(0, -1, .47f));
        twoColumns.addView(space(9, 1));
        twoColumns.addView(right, new LinearLayout.LayoutParams(0, -1, .53f));

        LinearLayout mainPanel = compactPanel();
        LinearLayout onRow = horizontal(Gravity.CENTER_VERTICAL);
        onRow.addView(text("POWER", 11, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        onRow.addView(button("ON", "power:on", () -> setPower(true), device != null && device.on), sized(80, 43));
        onRow.addView(space(7, 1));
        onRow.addView(button("OFF", "power:off", () -> setPower(false), device == null || !device.on), sized(80, 43));
        mainPanel.addView(onRow, sized(-1, 53));
        mainPanel.addView(space(1, 8));
        LinearLayout brightnessHead = horizontal(Gravity.CENTER_VERTICAL);
        brightnessHead.addView(text("☼  BRIGHTNESS", 12, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        int level = device == null ? 128 : device.brightness;
        TextView brightnessValue = text(Math.round(level * 100f / 255f) + "%", 15, WHITE, true);
        brightnessValue.setGravity(Gravity.CENTER);
        brightnessValue.setTag("bri:adjust");
        brightnessValue.setContentDescription("Brightness " + Math.round(level * 100f / 255f) +
                " percent. Press OK, then left or right to adjust; Back to finish.");
        brightnessValue.setFocusable(true);
        brightnessValue.setClickable(true);
        brightnessValue.setOnClickListener(v -> toggleSlider("bri:adjust"));
        setFocusStyle(brightnessValue, PANEL, 8);
        brightnessHead.addView(brightnessValue, sized(58, 27));
        mainPanel.addView(brightnessHead, sized(-1, 26));
        LinearLayout brightnessControls = horizontal(Gravity.CENTER_VERTICAL);
        brightnessControls.addView(button("−", "bri:down", () -> adjustBrightness(-13), false), sized(40, 33));
        brightnessControls.addView(space(8, 1));
        LinearLayout track = horizontal(Gravity.CENTER_VERTICAL);
        track.setBackground(shape(0xFF273751, 7, 0xFF273751, 0));
        track.setTag("live:bri:track");
        float portion = Math.max(.01f, Math.min(1f, level / 255f));
        View fill = new View(this);
        fill.setBackground(shape(0xFF268DFF, 7, 0xFF268DFF, 0));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(8), portion));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8), 1f - portion));
        brightnessControls.addView(track, new LinearLayout.LayoutParams(0, dp(9), 1));
        brightnessControls.addView(space(8, 1));
        brightnessControls.addView(button("+", "bri:up", () -> adjustBrightness(13), false), sized(40, 33));
        mainPanel.addView(brightnessControls, sized(-1, 40));
        if (!multi && device != null && device.segments.size() > 1) {
            mainPanel.addView(space(1, 4));
            mainPanel.addView(button("Segment: " + (selectedSegment < 0 ? "All" : "#" + (selectedSegment + 1)) + "  ▾",
                    "segment", this::showSegments, false), sized(-1, 31));
        }
        left.addView(mainPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        left.addView(space(1, 9));

        LinearLayout colorPanel = compactPanel();
        LinearLayout colorsTitle = horizontal(Gravity.CENTER_VERTICAL);
        colorsTitle.addView(text("QUICK COLORS", 11, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        colorsTitle.addView(button("Palettes  ›", "open:palettes", () -> openDrawer("palettes"), false), sized(100, 28));
        colorPanel.addView(colorsTitle);
        colorPanel.addView(space(1, 12));
        LinearLayout swatches = horizontal(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            final int index = i;
            int color = COLOR_VALUES[i];
            TextView dot = text("●", 27, color, true);
            dot.setGravity(Gravity.CENTER);
            dot.setFocusable(true);
            dot.setClickable(true);
            dot.setTag("color:" + i);
            dot.setContentDescription(COLOR_NAMES[i] + " color");
            dot.setOnClickListener(v -> setColor(color));
            dot.setBackground(shape(TILE, 13, TILE, 0));
            dot.setOnFocusChangeListener((v, f) -> {
                if (f) rememberFocus(v);
                v.setBackground(shape(f ? 0xFF2C4A75 : TILE, 13,
                        f ? ACCENT : TILE, f ? 2 : 0));
            });
            LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(0, dp(39), 1);
            if (index > 0) colorLp.leftMargin = dp(3);
            swatches.addView(dot, colorLp);
        }
        colorPanel.addView(swatches);
        colorPanel.addView(space(1, 5));
        colorPanel.addView(text("Select to set a solid color", 10, MUTED, false));
        left.addView(colorPanel, sized(-1, 121));

        LinearLayout effectPanel = compactPanel();
        LinearLayout effectsTitle = horizontal(Gravity.CENTER_VERTICAL);
        effectsTitle.addView(text("EFFECTS", 12, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        effectsTitle.addView(button("Browse all  ›", "open:effects", () -> openDrawer("effects"), false), sized(120, 29));
        effectPanel.addView(effectsTitle);
        effectPanel.addView(space(1, 7));
        List<Integer> quick = device == null ? Collections.emptyList() :
                popularIds(device.effects, POPULAR_EFFECTS, 6);
        if (quick.isEmpty()) {
            effectPanel.addView(text("Waiting for WLED effects…", 12, MUTED, false));
        } else {
            for (int r = 0; r < 2; r++) {
                LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
                for (int c = 0; c < 3; c++) {
                    int i = r * 3 + c;
                    if (i >= quick.size()) break;
                    int fxId = quick.get(i);
                    LinearLayout fxTile = effectTile(device.effects.get(fxId), fxId, device.effect == fxId);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(51), 1);
                    if (c > 0) lp.leftMargin = dp(6);
                    row.addView(fxTile, lp);
                }
                effectPanel.addView(row);
                if (r == 0) effectPanel.addView(space(1, 6));
            }
        }
        right.addView(effectPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        right.addView(space(1, 9));

        LinearLayout animation = compactPanel();
        LinearLayout animHeading = horizontal(Gravity.CENTER_VERTICAL);
        animHeading.addView(text("ANIMATION", 11, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        animHeading.addView(button("More  ›", "open:settings", () -> openDrawer("settings"), false), sized(86, 28));
        animation.addView(animHeading);
        animation.addView(compactStepper("Speed", "speed", device == null ? 128 : device.speed, this::adjustSpeed));
        animation.addView(compactStepper("Intensity", "intensity", device == null ? 128 : device.intensity, this::adjustIntensity));
        right.addView(animation, sized(-1, 113));

        controlList.addView(space(1, 8));
        LinearLayout sceneBar = horizontal(Gravity.CENTER_VERTICAL);
        sceneBar.setPadding(dp(9), dp(3), dp(8), dp(3));
        sceneBar.setBackground(shape(0xFF0F1A30, 11, BORDER, 1));
        sceneBar.addView(text("★  FAVS", 10, MUTED, true), sized(69, -1));
        if (favorites.isEmpty()) {
            TextView noFavorites = text("Pin a lighting shortcut to get started", 11, MUTED, false);
            sceneBar.addView(noFavorites, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            for (int i = 0; i < Math.min(3, favorites.size()); i++) {
                Favorite favorite = favorites.get(i);
                TextView chip = button("★ " + favorite.name, "favorite:" + favorite.id,
                        () -> runFavorite(favorite), false);
                chip.setContentDescription("Run favorite " + favorite.name + " on " + targetDescription(favorite.target));
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(38), 1);
                chipLp.leftMargin = dp(6);
                sceneBar.addView(chip, chipLp);
            }
        }
        sceneBar.addView(space(7, 1));
        sceneBar.addView(button("Manage ›", "favorite:manage", () -> openDrawer("favorites"), false), sized(94, 36));
        controlList.addView(sceneBar, sized(-1, 51));
    }

    private LinearLayout effectTile(String name, int id, boolean active) {
        LinearLayout tile = horizontal(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(7), dp(3), dp(4), dp(3));
        tile.setBackground(shape(active ? 0xFF1B416B : TILE, 10,
                active ? 0xFF299BFF : BORDER, active ? 2 : 1));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setTag("fx:" + id);
        tile.setContentDescription("Effect " + name);
        int[] accentColors = {0xFF26C6FF, 0xFFD858F2, 0xFFFA6D49,
                0xFFFFC547, 0xFF42E0B0, 0xFF8675FF};
        View dot = new View(this);
        int hue = accentColors[(id % accentColors.length)];
        dot.setBackground(shape(hue, 8, hue, 0));
        tile.addView(dot, sized(9, 26));
        tile.addView(space(7, 1));
        TextView label = text(name, 11, WHITE, true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        tile.setOnClickListener(v -> setEffect(id));
        setFocusStyle(tile, active ? 0xFF1B416B : TILE, 10);
        return tile;
    }

    /** Modal drawers keep focus inside the sheet and restore the exact opening control. */
    private void openDrawer(String key) {
        if (adjustingSliderTag != null) exitSliderMode();
        String origin = focusedTag();
        if (drawerOverlay == null) {
            drawerReturnFocusTag = origin == null ? rememberedDashboardFocusTag : origin;
            drawerHistory.clear();
        } else if (!key.equals(activeDrawer)) {
            drawerHistory.push(new DrawerVisit(activeDrawer, origin,
                    drawerScroll == null ? 0 : drawerScroll.getScrollY()));
        }
        activeDrawer = key;
        pendingDrawerFocus = null;
        showDrawer();
        renderNavigation();
    }

    /** Selection is final; Back alone walks nested drawers. */
    private void dismissAllDrawers() {
        drawerHistory.clear();
        closeDrawer();
    }

    private void closeDrawer() {
        if (drawerOverlay == null) return;
        if (!drawerHistory.isEmpty()) {
            DrawerVisit previous = drawerHistory.pop();
            activeDrawer = previous.key;
            pendingDrawerFocus = previous.focusTag;
            pendingDrawerScrollY = previous.scrollY;
            showDrawer();
            renderNavigation();
            return;
        }
        rootFrame.removeView(drawerOverlay);
        drawerOverlay = null;
        drawerScroll = null;
        activeDrawer = "";
        renderNavigation();
        final String returnTag = drawerReturnFocusTag;
        pendingFocusRestoration = returnTag;
        rootFrame.post(() -> {
            restoreFocus(returnTag, rememberedDashboardFocusTag);
            if (returnTag.equals(pendingFocusRestoration)) pendingFocusRestoration = null;
        });
    }

    @Override public void onBackPressed() {
        if (adjustingSliderTag != null) exitSliderMode();
        else if (drawerOverlay != null) closeDrawer();
        else super.onBackPressed();
    }

    private void showDrawer() {
        View previouslyFocusedView = getCurrentFocus();
        String previousFocused = isChildOf(previouslyFocusedView, drawerOverlay) ? focusedTag() : null;
        int previousScrollY = pendingDrawerScrollY >= 0 ? pendingDrawerScrollY :
                drawerScroll == null ? 0 : drawerScroll.getScrollY();
        pendingDrawerScrollY = -1;
        if (drawerOverlay != null) rootFrame.removeView(drawerOverlay);
        drawerOverlay = new FrameLayout(this);
        rootFrame.addView(drawerOverlay, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackgroundColor(0xAF020815);
        scrim.setOnClickListener(v -> closeDrawer());
        drawerOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(15), dp(13), dp(15), dp(12));
        sheet.setBackground(shape(0xFF101E35, 16, 0xFF316092, 1));
        int windowWidth = getResources().getConfiguration().screenWidthDp;
        boolean phoneSheet = !tvMode && touchNavAtBottom;
        int drawerWidth = phoneSheet ? -1 : dp(Math.min(tvMode ? 390 : 420, Math.max(260, windowWidth - 20)));
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(drawerWidth,
                phoneSheet ? dp(Math.max(250, (int) (getResources().getConfiguration().screenHeightDp * 0.87f))) : -1,
                phoneSheet ? Gravity.BOTTOM : Gravity.RIGHT);
        sheetLp.setMargins(dp(phoneSheet ? 5 : 0), dp(phoneSheet ? 0 : 8),
                dp(phoneSheet ? 5 : 9), dp(phoneSheet ? 5 : 8));
        drawerOverlay.addView(sheet, sheetLp);
        if (phoneSheet) {
            sheet.setTranslationY(dp(700));
            sheet.animate().translationY(0f).setDuration(190).start();
        } else {
            sheet.setTranslationX(dp(440));
            sheet.animate().translationX(0f).setDuration(190).start();
        }
        LinearLayout top = horizontal(Gravity.CENTER_VERTICAL);
        TextView title = text(drawerTitle(), 21, WHITE, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(button("✕", "drawer:close", this::closeDrawer, false), sized(tvMode ? 44 : 48, tvMode ? 39 : 48));
        sheet.addView(top, sized(-1, tvMode ? 45 : 53));
        sheet.addView(space(1, 7));
        ScrollView scrolling = new ScrollView(this);
        drawerScroll = scrolling;
        scrolling.setVerticalScrollBarEnabled(false);
        scrolling.setFillViewport(false);
        sheet.addView(scrolling, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout list = vertical();
        scrolling.addView(list);
        if (activeDrawer.equals("devices")) renderDevicesDrawer(list, sheet);
        else if (activeDrawer.equals("effects")) renderEffectsDrawer(list);
        else if (activeDrawer.equals("favorites")) renderFavoritesDrawer(list, sheet);
        else if (activeDrawer.equals("presets")) renderPresetsDrawer(list, sheet);
        else if (activeDrawer.equals("scenes")) renderScenesDrawer(list, sheet);
        else if (activeDrawer.equals("groups")) renderGroupsDrawer(list, sheet);
        else if (activeDrawer.equals("palettes")) renderPalettesDrawer(list);
        else if (activeDrawer.equals("settings")) renderSettingsDrawer(list);
        final String requested = pendingDrawerFocus != null ? pendingDrawerFocus : previousFocused;
        pendingDrawerFocus = null;
        sheet.post(() -> {
            if (drawerOverlay == null || drawerScroll != scrolling) return;
            if (requested != null) scrolling.scrollTo(0, previousScrollY);
            View focus = requested == null ? null : drawerOverlay.findViewWithTag(requested);
            if (focus == null) focus = sheet.findViewWithTag("drawer:first");
            if (focus == null) focus = sheet.findViewWithTag("drawer:close");
            if (focus != null) focus.requestFocus();
        });
    }

    private String drawerTitle() {
        switch (activeDrawer) {
            case "devices": return "Your lights";
            case "favorites": return "Favorites";
            case "effects": return "Effects";
            case "presets": return "Presets";
            case "scenes": return "GlowDeck Scenes";
            case "groups": return "Groups";
            case "palettes": return "Palettes";
            case "settings": return "More options";
            default: return "GlowDeck";
        }
    }

    private void drawerInfo(LinearLayout list, String caption) {
        TextView txt = text(caption, 11, MUTED, false);
        list.addView(txt, lpBottom(13));
    }

    private TextView drawerItem(String name, String detail, String id, Runnable callback, boolean active) {
        String value = detail.isEmpty() ? name : name + "\n" + detail;
        TextView b = text(value, 13, WHITE, true);
        b.setLineSpacing(dp(3), 1f);
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setPadding(dp(13), dp(9), dp(8), dp(9));
        b.setBackground(shape(active ? 0xFF173B69 : TILE, 10,
                active ? 0xFF329DFF : BORDER, active ? 2 : 1));
        b.setTag(id);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(v -> callback.run());
        if (!tvMode) b.setMinHeight(dp(54));
        setFocusStyle(b, active ? 0xFF173B69 : TILE, 10);
        return b;
    }

    private void renderDevicesDrawer(LinearLayout list, LinearLayout sheet) {
        drawerInfo(list, tvMode ? "Select a controller. Only the drawer scrolls if you have many lights." :
                "Tap a light or group to control it, or discover another device.");
        list.addView(drawerItem("◉  All devices", devices.size() + " controllers",
                "drawer:first", () -> selectHost("*"), "*".equals(selectedHost)), lpBottom(7));
        for (WledDevice d : devices) {
            String status = d.online ? d.on ? "● On" : "○ Off" : "○ Offline";
            list.addView(drawerItem("◈  " + d.name(), status + "   ·   " + d.host,
                    "device:" + d.host, () -> selectHost(d.host), selectedHost.equals(d.host)), lpBottom(7));
        }
        LinearLayout footer = horizontal(Gravity.CENTER_VERTICAL);
        footer.addView(button("⌕ Scan", "drawer:scan", this::scan, false), new LinearLayout.LayoutParams(0, dp(tvMode ? 42 : 49), 1));
        footer.addView(space(7, 1));
        footer.addView(button("+ Add device", "drawer:add", this::showAddDialog, true), new LinearLayout.LayoutParams(0, dp(tvMode ? 42 : 49), 1));
        sheet.addView(space(1, 10));
        sheet.addView(footer, sized(-1, tvMode ? 42 : 49));
    }

    private void renderEffectsDrawer(LinearLayout list) {
        WledDevice d = currentDevice();
        if (d == null || d.effects.isEmpty()) { drawerInfo(list, "Effects will appear after connecting to WLED."); return; }
        drawerInfo(list, "Choose an animation. Options come from your WLED firmware.");
        int count = 0;
        for (int i = 0; i < d.effects.size(); i++) {
            if (!validOption(d.effects.get(i))) continue;
            final int effectId = i;
            list.addView(drawerItem("✦  " + d.effects.get(i), "", count == 0 ? "drawer:first" : "effect:" + i,
                    () -> { setEffect(effectId); dismissAllDrawers(); }, d.effect == i), lpBottom(6));
            count++;
        }
    }

    private void renderPalettesDrawer(LinearLayout list) {
        WledDevice d = currentDevice();
        if (d == null || d.palettes.isEmpty()) { drawerInfo(list, "Palettes will appear after connecting to WLED."); return; }
        drawerInfo(list, "Palette names and indices are read from the selected controller.");
        int count = 0;
        for (int i = 0; i < d.palettes.size(); i++) {
            if (!validOption(d.palettes.get(i))) continue;
            final int paletteId = i;
            list.addView(drawerItem("◒  " + d.palettes.get(i), "", count == 0 ? "drawer:first" : "palette:" + i,
                    () -> { setPalette(paletteId); dismissAllDrawers(); }, d.palette == i), lpBottom(6));
            count++;
        }
    }

    private void renderPresetsDrawer(LinearLayout list, LinearLayout sheet) {
        WledDevice d = currentDevice();
        drawerInfo(list, "Saved presets are read from WLED. For multiple lights, matching preset names are used.");
        if (d == null || !d.loadedPresets) {
            drawerInfo(list, "Loading presets from the controller…");
            if (d != null) requestPresets(d);
        } else if (d.presets.isEmpty()) {
            drawerInfo(list, "No saved WLED presets. Save the current look to create one.");
        } else {
            for (int i = 0; i < d.presets.size(); i++) {
                WledDevice.Preset p = d.presets.get(i);
                list.addView(drawerItem("▤  " + p.name, "WLED preset " + p.id,
                        i == 0 ? "drawer:first" : "preset:" + p.id,
                        () -> { applyPreset(p); dismissAllDrawers(); }, !multiTarget() && d.currentPreset == p.id), lpBottom(6));
            }
        }
        if (!multiTarget() && d != null) {
            sheet.addView(space(1, 8));
            sheet.addView(button("+ Save current look", "drawer:savepreset", this::showSavePreset, true), sized(-1, 42));
        }
    }

    private void renderGroupsDrawer(LinearLayout list, LinearLayout sheet) {
        drawerInfo(list, "Control all lights together, or create your own device groups.");
        list.addView(drawerItem("◉  All devices", devices.size() + " lights",
                "drawer:first", () -> selectHost("*"), "*".equals(selectedHost)), lpBottom(8));
        for (SavedGroup group : new ArrayList<>(savedGroups)) {
            list.addView(drawerItem("◎  " + group.name, group.hosts.size() + " devices  ·  " +
                    (tvMode ? "Press OK to select" : "Tap to select"),
                    "group:" + group.id, () -> selectHost("@g:" + group.id),
                    selectedHost.equals("@g:" + group.id)), lpBottom(8));
            TextView edit = button("Edit / delete " + group.name, "group:edit:" + group.id,
                    () -> showGroupActions(group), false);
            list.addView(edit, lpBottom(13));
        }
        sheet.addView(space(1, 8));
        sheet.addView(button("+ Create group", "group:new", this::createGroup, true), sized(-1, 43));
    }

    private void renderSettingsDrawer(LinearLayout list) {
        drawerInfo(list, "Advanced WLED options and app configuration.");
        list.addView(drawerItem("▦  Scenes", "Capture and coordinate several lights",
                "settings:scenes", () -> openDrawer("scenes"), false), lpBottom(7));
        list.addView(drawerItem("★  Favorites", "Pin and manage one-tap lighting actions",
                "settings:favorites", () -> openDrawer("favorites"), false), lpBottom(7));
        list.addView(drawerItem("◎  Groups", "Create and select collections of controllers", "settings:groups",
                () -> openDrawer("groups"), false), lpBottom(7));
        list.addView(drawerItem("◉  Change device", selectedName(), "drawer:first",
                () -> openDrawer("devices"), false), lpBottom(7));
        list.addView(drawerItem("◒  Color palettes", "Browse the WLED palette library", "drawer:palettes",
                () -> openDrawer("palettes"), false), lpBottom(7));
        WledDevice d = currentDevice();
        if (!multiTarget() && d != null) {
            if (d.segments.size() > 1) list.addView(drawerItem("▦  LED segments", "Select a segment", "drawer:segments",
                    this::showSegments, false), lpBottom(7));
            list.addView(drawerItem("⚙  Manage " + d.name(), "Rename, remove or refresh", "drawer:manage",
                    this::showManageDialog, false), lpBottom(7));
        }
        list.addView(drawerItem("⌕  Scan network", "Discover nearby WLED devices", "drawer:scan",
                this::scan, false), lpBottom(7));
        list.addView(drawerItem("+  Add device", "Enter IP address or hostname", "drawer:add",
                this::showAddDialog, false), lpBottom(7));
        list.addView(drawerItem("↻  Refresh status", "Read current state from every WLED device", "drawer:refresh",
                () -> { refreshAll(); toast("Refreshing device status"); }, false), lpBottom(7));
        drawerInfo(list, "GlowDeck v1.4  ·  Local network control  ·  TV, tablet and phone");
    }

    private static final class SavedGroup {
        final String id;
        String name;
        final List<String> hosts = new ArrayList<>();
        SavedGroup(String id, String name) { this.id = id; this.name = name; }
    }

    private void loadGroups() {
        savedGroups.clear();
        try {
            JSONArray data = new JSONArray(store.readGroups());
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                SavedGroup group = new SavedGroup(item.optString("id"), item.optString("name"));
                JSONArray hosts = item.optJSONArray("hosts");
                if (hosts != null) for (int j = 0; j < hosts.length(); j++) group.hosts.add(hosts.optString(j));
                if (!group.name.trim().isEmpty() && !group.id.isEmpty()) savedGroups.add(group);
            }
        } catch (JSONException ignored) { }
    }

    private void saveGroups() {
        JSONArray items = new JSONArray();
        for (SavedGroup group : savedGroups) {
            JSONObject entry = new JSONObject();
            try {
                entry.put("id", group.id);
                entry.put("name", group.name);
                JSONArray hosts = new JSONArray();
                for (String host : group.hosts) hosts.put(host);
                entry.put("hosts", hosts);
                items.put(entry);
            } catch (JSONException ignored) { }
        }
        store.saveGroups(items.toString());
    }

    private void createGroup() {
        if (devices.isEmpty()) { toast("Add devices before creating groups"); return; }
        askText("New lighting group", "e.g. Living room", "", name -> {
            if (name.trim().isEmpty()) { toast("Enter a group name"); return; }
            SavedGroup group = new SavedGroup(String.valueOf(System.currentTimeMillis()), name.trim());
            chooseGroupMembers(group, true);
        });
    }

    private void chooseGroupMembers(SavedGroup group, boolean newGroup) {
        if (devices.isEmpty()) { toast("Add a WLED controller first"); return; }
        String[] names = new String[devices.size()];
        boolean[] checked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            WledDevice device = devices.get(i);
            names[i] = device.name() + "  ·  " + device.host;
            checked[i] = group.hosts.contains(device.host);
        }
        new AlertDialog.Builder(this).setTitle("Lights in " + group.name)
                .setMultiChoiceItems(names, checked, (dialog, index, enabled) -> checked[index] = enabled)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save group", (dialog, which) -> {
                    group.hosts.clear();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) group.hosts.add(devices.get(i).host);
                    if (group.hosts.isEmpty()) { toast("Choose at least one light"); return; }
                    if (newGroup) savedGroups.add(group);
                    saveGroups();
                    selectedHost = "@g:" + group.id;
                    if (drawerOverlay != null) showDrawer();
                    render();
                }).show();
    }

    private void showGroupActions(SavedGroup group) {
        new AlertDialog.Builder(this).setTitle(group.name)
                .setItems(new String[]{"Change members", "Rename group", "Delete group"}, (dialog, which) -> {
                    if (which == 0) chooseGroupMembers(group, false);
                    if (which == 1) askText("Rename group", "Group name", group.name, name -> {
                        if (!name.trim().isEmpty()) { group.name = name.trim(); saveGroups(); showDrawer(); render(); }
                    });
                    if (which == 2) new AlertDialog.Builder(this).setTitle("Delete group?")
                            .setMessage("Only the group is removed. Your WLED devices are not changed.")
                            .setPositiveButton("Delete", (d, w) -> {
                                savedGroups.remove(group);
                                if (selectedHost.equals("@g:" + group.id)) selectedHost = "*";
                                saveGroups(); showDrawer(); render();
                            }).setNegativeButton("Cancel", null).show();
                }).show();
    }

    private LinearLayout compactPanel() {
        LinearLayout panel = vertical();
        panel.setPadding(dp(11), dp(9), dp(11), dp(7));
        panel.setBackground(shape(PANEL, 12, BORDER, 1));
        return panel;
    }

    private LinearLayout compactColorCell(String label, int color, int index) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(shape(TILE, 9, BORDER, 1));
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setTag("color:" + index);
        View dot = new View(this);
        dot.setBackground(shape(color, 40, color, 0));
        cell.addView(dot, sized(17, 17));
        cell.addView(space(1, 4));
        cell.addView(text(label, 10, WHITE, true));
        cell.setOnClickListener(v -> setColor(color));
        setFocusStyle(cell, TILE, 9);
        return cell;
    }

    private LinearLayout compactStepper(String label, String tag, int level, DeltaSelected step) {
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 12, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button("−", tag + ":down", () -> step.run(-15), false), sized(37, 31));
        TextView amount = text(Math.round(level / 255f * 100f) + "%", 12, ACCENT, true);
        amount.setGravity(Gravity.CENTER);
        amount.setTag(tag + ":adjust");
        amount.setContentDescription(label + " " + Math.round(level / 255f * 100f) +
                " percent. Press OK, then left or right to adjust; Back to finish.");
        amount.setFocusable(true);
        amount.setClickable(true);
        amount.setOnClickListener(v -> toggleSlider(tag + ":adjust"));
        setFocusStyle(amount, PANEL, 8);
        row.addView(amount, sized(51, 31));
        row.addView(button("+", tag + ":up", () -> step.run(15), false), sized(37, 31));
        return row;
    }

    /** Keep a compatibility entrypoint for advanced actions. */
    private void showAdvancedDialog() { openDrawer("settings"); }

    private void sectionHeader(String title, String subtitle) {
        LinearLayout header = vertical();
        header.addView(text(title, 13, WHITE, true));
        header.addView(space(1, 4));
        header.addView(text(subtitle, 12, MUTED, false));
        controlList.addView(header, lpBottom(9));
    }

    private LinearLayout colorCell(String name, int color, int idx) {
        LinearLayout cell = horizontal(Gravity.CENTER_VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(shape(TILE, 13, BORDER, 1));
        cell.setFocusable(true);
        cell.setTag("color:" + idx);
        View dot = new View(this);
        dot.setBackground(shape(color, 50, color, 0));
        cell.addView(dot, sized(22, 22));
        cell.addView(space(9, 1));
        cell.addView(text(name, 14, WHITE, true));
        cell.setOnClickListener(v -> setColor(color));
        setFocusStyle(cell, TILE, 13);
        return cell;
    }

    private interface IdSelected { void run(int id); }
    private interface DeltaSelected { void run(int delta); }

    private void addOptionGrid(LinearLayout parent, List<String> names, List<Integer> ids,
                               String prefix, int active, IdSelected handler) {
        if (ids.isEmpty()) {
            parent.addView(text("No options reported by the device.", 13, MUTED, false));
            return;
        }
        for (int i = 0; i < ids.size(); i += 4) {
            LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
            for (int j = i; j < Math.min(i + 4, ids.size()); j++) {
                int id = ids.get(j);
                TextView option = button(names.get(id), prefix + ":" + id,
                        () -> handler.run(id), id == active);
                option.setSingleLine(true);
                option.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1);
                if (j > i) lp.leftMargin = dp(9);
                row.addView(option, lp);
            }
            parent.addView(row);
            if (i + 4 < ids.size()) parent.addView(space(1, 9));
        }
    }

    private LinearLayout stepper(String label, String key, int level, DeltaSelected adjuster) {
        LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 15, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button("−", key + ":down", () -> adjuster.run(-15), false), sized(50, 42));
        row.addView(space(10, 1));
        TextView value = text(Math.round(level / 255f * 100) + "%", 16, ACCENT, true);
        value.setGravity(Gravity.CENTER);
        row.addView(value, sized(64, 42));
        row.addView(space(10, 1));
        row.addView(button("+", key + ":up", () -> adjuster.run(15), false), sized(50, 42));
        return row;
    }

    private static List<Integer> popularIds(List<String> names, String[] popular, int max) {
        List<Integer> ids = new ArrayList<>();
        for (String name : popular) {
            int found = indexByName(names, name);
            if (found >= 0 && !ids.contains(found)) ids.add(found);
            if (ids.size() >= max) break;
        }
        for (int i = 0; i < names.size() && ids.size() < max; i++) {
            if (validOption(names.get(i)) && !ids.contains(i)) ids.add(i);
        }
        return ids;
    }

    private static int countAvailable(List<String> names) {
        int count = 0;
        for (String value : names) if (validOption(value)) count++;
        return count;
    }

    private static boolean validOption(String name) {
        return name != null && !name.trim().isEmpty() && !name.equals("-") &&
                !name.equalsIgnoreCase("RSVD");
    }

    private static int indexByName(List<String> names, String wanted) {
        for (int i = 0; i < names.size(); i++)
            if (names.get(i).equalsIgnoreCase(wanted) && validOption(names.get(i))) return i;
        return -1;
    }

    private WledDevice currentDevice() {
        List<WledDevice> active = targets();
        for (WledDevice d : active) if (d.online) return d;
        return active.isEmpty() ? null : active.get(0);
    }

    private List<WledDevice> targets() {
        if ("*".equals(selectedHost)) return new ArrayList<>(devices);
        if (selectedHost.startsWith("@g:")) {
            List<WledDevice> result = new ArrayList<>();
            for (SavedGroup group : savedGroups) {
                if (!selectedHost.equals("@g:" + group.id)) continue;
                for (WledDevice d : devices) if (group.hosts.contains(d.host)) result.add(d);
                return result;
            }
            return result;
        }
        for (WledDevice d : devices) if (d.host.equals(selectedHost))
            return Collections.singletonList(d);
        return Collections.emptyList();
    }

    @FunctionalInterface private interface Command { JSONObject patch(WledDevice d) throws JSONException; }

    private void send(String action, Command command) {
        List<WledDevice> target = targets();
        if (target.isEmpty()) { toast("Add a WLED device first"); return; }
        message = "Sending " + action + "…";
        refreshVisibleState();
        network.execute(() -> {
            int ok = 0, skipped = 0;
            String firstError = "";
            for (WledDevice d : target) {
                try {
                    JSONObject patch = command.patch(d);
                    if (patch == null) { skipped++; continue; }
                    WledApi.send(d.host, patch);
                    ok++;
                } catch (Exception ex) { if (firstError.isEmpty()) firstError = concise(ex); }
            }
            final int sent = ok, skippedCount = skipped;
            final String error = firstError;
            ui.post(() -> {
                message = sent + " of " + target.size() + " updated" +
                        (skippedCount > 0 ? "  ·  " + skippedCount + " unsupported" : "") +
                        (!error.isEmpty() ? "  ·  " + error : "");
                refreshVisibleState();
            });
            for (WledDevice d : target) fetchDevice(d);
        });
    }

    private JSONObject segmentPatch(WledDevice d, JSONObject segValues) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("on", true);
        if (selectedSegment >= 0 && !multiTarget()) {
            JSONObject specific = new JSONObject(segValues.toString());
            specific.put("id", selectedSegment);
            body.put("seg", new JSONArray().put(specific));
        } else {
            JSONArray segments = new JSONArray();
            for (WledDevice.Segment segment : d.segments) {
                JSONObject entry = new JSONObject(segValues.toString());
                entry.put("id", segment.id);
                segments.put(entry);
            }
            body.put("seg", segments.length() == 0 ? segValues : segments);
        }
        return body;
    }

    private void setPower(boolean on) {
        for (WledDevice d : targets()) d.on = on;
        send(on ? "power on" : "power off", d -> WledApi.object("on", on));
    }

    private void adjustBrightness(int delta) {
        WledDevice base = currentDevice();
        if (base == null) return;
        int value = clamp(base.brightness + delta, 1, 255);
        for (WledDevice d : targets()) d.brightness = value;
        send("brightness", d -> WledApi.object("bri", value));
    }

    private void setColor(int rgb) {
        send("color", d -> {
            JSONArray components = new JSONArray().put(Color.red(rgb)).put(Color.green(rgb)).put(Color.blue(rgb));
            JSONObject seg = new JSONObject().put("col", new JSONArray().put(components)).put("fx", 0);
            return segmentPatch(d, seg);
        });
    }

    private void setEffect(int id) {
        WledDevice base = currentDevice();
        if (base == null || id >= base.effects.size()) return;
        String chosen = base.effects.get(id);
        send("effect " + chosen, d -> {
            int mapped = indexByName(d.effects, chosen);
            return mapped < 0 ? null : segmentPatch(d, new JSONObject().put("fx", mapped));
        });
    }

    private void setPalette(int id) {
        WledDevice base = currentDevice();
        if (base == null || id >= base.palettes.size()) return;
        String chosen = base.palettes.get(id);
        send("palette " + chosen, d -> {
            int mapped = indexByName(d.palettes, chosen);
            return mapped < 0 ? null : segmentPatch(d, new JSONObject().put("pal", mapped));
        });
    }

    private void adjustSpeed(int delta) {
        WledDevice base = currentDevice();
        if (base == null) return;
        int value = clamp(base.speed + delta, 0, 255);
        for (WledDevice d : targets()) d.speed = value;
        send("speed", d -> segmentPatch(d, new JSONObject().put("sx", value)));
    }

    private void adjustIntensity(int delta) {
        WledDevice base = currentDevice();
        if (base == null) return;
        int value = clamp(base.intensity + delta, 0, 255);
        for (WledDevice d : targets()) d.intensity = value;
        send("intensity", d -> segmentPatch(d, new JSONObject().put("ix", value)));
    }

    private void applyPreset(WledDevice.Preset preset) {
        send("preset " + preset.name, d -> {
            for (WledDevice.Preset p : d.presets) {
                if (multiTarget() ? p.name.equalsIgnoreCase(preset.name) : p.id == preset.id)
                    return WledApi.object("ps", p.id);
            }
            return null;
        });
    }

    private void showAllEffects() { openDrawer("effects"); }
    private void showAllPalettes() { openDrawer("palettes"); }

    private void showOptions(String title, boolean effect) {
        WledDevice d = currentDevice();
        if (d == null) return;
        List<String> names = effect ? d.effects : d.palettes;
        List<Integer> ids = new ArrayList<>();
        List<String> captions = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (validOption(names.get(i))) { ids.add(i); captions.add(names.get(i)); }
        }
        if (ids.isEmpty()) { toast("Nothing available on this device"); return; }
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(captions.toArray(new String[0]), (dialog, which) -> {
                    if (effect) setEffect(ids.get(which)); else setPalette(ids.get(which));
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showAllPresets() {
        WledDevice d = currentDevice();
        if (d == null) return;
        if (d.presets.isEmpty()) { toast("No presets found on this WLED device"); return; }
        String[] names = new String[d.presets.size()];
        for (int i = 0; i < names.length; i++) names[i] = d.presets.get(i).name;
        new AlertDialog.Builder(this).setTitle("Saved WLED presets")
                .setItems(names, (dialog, which) -> applyPreset(d.presets.get(which)))
                .setNegativeButton("Cancel", null).show();
    }

    private void showSavePreset() {
        WledDevice d = currentDevice();
        if (d == null || multiTarget()) return;
        int available = -1;
        for (int id = 1; id <= 250; id++) {
            boolean used = false;
            for (WledDevice.Preset p : d.presets) if (p.id == id) { used = true; break; }
            if (!used) { available = id; break; }
        }
        if (available < 0) { toast("No empty preset slots available"); return; }
        final int id = available;
        askText("Save current look as preset", "e.g. Movie night", "", value -> {
            if (value.trim().isEmpty()) { toast("Enter a preset name"); return; }
            send("save preset", item -> new JSONObject()
                    .put("psave", id).put("n", value.trim()).put("ib", true).put("sb", true));
            d.loadedPresets = false;
            ui.postDelayed(() -> requestPresets(d), 1800);
        });
    }

    private void showSegments() {
        WledDevice d = currentDevice();
        if (d == null) return;
        String[] names = new String[d.segments.size() + 1];
        names[0] = "All segments";
        for (int i = 0; i < d.segments.size(); i++) names[i + 1] = d.segments.get(i).label();
        int checked = 0;
        for (int i = 0; i < d.segments.size(); i++) {
            if (d.segments.get(i).id == selectedSegment) checked = i + 1;
        }
        new AlertDialog.Builder(this).setTitle("Which segment should change?")
                .setSingleChoiceItems(names, checked,
                        (dialog, which) -> {
                            selectedSegment = which == 0 ? -1 : d.segments.get(which - 1).id;
                            dialog.dismiss();
                            render();
                        }).setNegativeButton("Cancel", null).show();
    }

    private interface TextAnswered { void onAnswer(String value); }
    private void askText(String title, String hint, String defaultValue, TextAnswered callback) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(17);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setHint(hint);
        edit.setText(defaultValue);
        edit.setSelectAllOnFocus(true);
        LinearLayout container = vertical();
        container.setPadding(dp(24), dp(3), dp(24), dp(3));
        container.addView(edit, new LinearLayout.LayoutParams(-1, dp(65)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> callback.onAnswer(edit.getText().toString()))
                .create();
        dialog.getWindow();
        dialog.show();
    }

    private void showAddDialog() {
        askText("Add WLED device", "IP address or hostname (e.g. 192.168.1.71)", "", value -> {
            String host = normalizeHost(value);
            if (host.isEmpty()) { toast("Enter a valid IP or hostname"); return; }
            for (WledDevice device : devices) if (device.host.equalsIgnoreCase(host)) {
                selectedHost = device.host;
                render();
                toast("Device already added");
                return;
            }
            // Validate first: don't save random non-WLED endpoints to the list.
            message = "Connecting to " + host + "…";
            render();
            discoveryWork.execute(() -> verifyAndAdd(host, ""));
        });
    }

    private static String normalizeHost(String input) {
        if (input == null) return "";
        String host = input.trim().replaceFirst("(?i)^https?://", "");
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        host = host.trim().toLowerCase(Locale.US);
        if (host.isEmpty() || host.length() > 253 || !host.matches("[a-z0-9.-]+(:[0-9]{1,5})?")) return "";
        return host;
    }

    private void showManageDialog() {
        WledDevice d = currentDevice();
        if (d == null || multiTarget()) return;
        new AlertDialog.Builder(this).setTitle(d.name())
                .setItems(new String[]{"Rename device", "Remove device", "Refresh device"},
                        (dialog, option) -> {
                            if (option == 0) askText("Name this device", "Living Room", d.name(), value -> {
                                d.alias = value.trim();
                                saveDevices();
                                render();
                            });
                            else if (option == 1) new AlertDialog.Builder(this)
                                    .setTitle("Remove " + d.name() + "?")
                                    .setMessage("This only removes it from GlowDeck TV; it does not reset WLED.")
                                    .setPositiveButton("Remove", (confirm, which) -> {
                                        devices.remove(d);
                                        saveDevices();
                                        selectedHost = devices.isEmpty() ? "*" : devices.get(0).host;
                                        selectedSegment = -1;
                                        render();
                                    }).setNegativeButton("Cancel", null).show();
                            else { d.loadedPresets = false; d.loadingPresets = false; refreshOne(d); }
                        }).setNegativeButton("Cancel", null).show();
    }

    private void refreshAll() {
        List<WledDevice> copy = new ArrayList<>(devices);
        network.execute(() -> {
            for (WledDevice d : copy) fetchDevice(d);
        });
    }

    private void refreshOne(WledDevice d) { network.execute(() -> fetchDevice(d)); }

    private void fetchDevice(WledDevice d) {
        try {
            JSONObject snapshot = WledApi.snapshot(d.host);
            ui.post(() -> {
                if (!devices.contains(d)) return;
                JSONObject state = snapshot.optJSONObject("state");
                String stateSignature = state == null ? "" : state.toString();
                boolean changed = !d.online || !stateSignature.equals(d.lastStateSignature);
                int previousEffectCount = d.effects.size();
                int previousSegmentCount = d.segments.size();
                d.applySnapshot(snapshot);
                // Only rebuild if new controls became available; status polling updates in place.
                if (previousEffectCount != d.effects.size() ||
                        previousSegmentCount != d.segments.size()) {
                    if (adjustingSliderTag == null) {
                        render();
                        if (drawerOverlay != null && ("effects".equals(activeDrawer) ||
                                "palettes".equals(activeDrawer) || "settings".equals(activeDrawer)))
                            showDrawer();
                    } else { deferredStructuralRender = true; refreshVisibleState(); }
                } else if (changed) refreshVisibleState();
                if (!d.loadedPresets) requestPresets(d);
            });
        } catch (Exception ex) {
            ui.post(() -> {
                if (!devices.contains(d)) return;
                String failure = concise(ex);
                boolean changed = d.online || !failure.equals(d.error);
                d.online = false;
                d.error = failure;
                if (changed) refreshVisibleState();
            });
        }
    }

    private void requestPresets(WledDevice d) {
        if (d.loadedPresets || d.loadingPresets) return;
        d.loadingPresets = true; // queued: don't flood the tiny WLED HTTP server.
        // Read asynchronously. Failure is nonfatal (e.g. WLED 0.10 or locked filesystem).
        network.execute(() -> {
            try {
                JSONObject p = WledApi.presets(d.host);
                ui.post(() -> { if (devices.contains(d)) {
                    d.applyPresets(p);
                    render();
                    if (drawerOverlay != null && ("scenes".equals(activeDrawer) ||
                            "presets".equals(activeDrawer))) showDrawer();
                } });
            } catch (Exception ex) {
                ui.post(() -> { if (devices.contains(d)) {
                    d.loadingPresets = false;
                    d.loadedPresets = true;
                    render();
                } });
            }
        });
    }

    private void verifyAndAdd(String rawHost, String providedName) {
        String host = normalizeHost(rawHost);
        if (host.isEmpty()) return;
        try {
            JSONObject j = WledApi.snapshot(host);
            ui.post(() -> {
                for (WledDevice current : devices) if (current.host.equalsIgnoreCase(host)) return;
                String label = providedName;
                WledDevice newDevice = new WledDevice(host, label);
                newDevice.applySnapshot(j);
                devices.add(newDevice);
                saveDevices();
                if (!scanning) selectedHost = host;
                message = "Found " + newDevice.name() + " at " + host;
                render();
                if (drawerOverlay != null && activeDrawer.equals("devices")) showDrawer();
                requestPresets(newDevice);
            });
        } catch (Exception ex) {
            ui.post(() -> {
                if (!scanning) { message = "Could not connect to " + host + ": " + concise(ex); toast(message); render(); }
            });
        }
    }

    // Built-in Android NSD discovers the exact _wled._tcp service advertised by WLED firmware.
    private void scan() {
        if (scanning) { toast("Discovery already running"); return; }
        scanning = true;
        message = "Scanning for WLED devices on this network…";
        render();
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("glowdeck-discovery");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
            nsd = (NsdManager) getSystemService(NSD_SERVICE);
            discoveryListener = new NsdManager.DiscoveryListener() {
                @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    ui.post(() -> finishScan("Discovery could not start (" + errorCode + ")"));
                }
                @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    ui.post(() -> finishScan("Discovery stopped"));
                }
                @Override public void onDiscoveryStarted(String serviceType) { }
                @Override public void onDiscoveryStopped(String serviceType) { }
                @Override public void onServiceLost(NsdServiceInfo serviceInfo) { }
                @Override public void onServiceFound(NsdServiceInfo info) {
                    if (!scanning || info.getServiceType() == null ||
                            !info.getServiceType().startsWith("_wled._tcp")) return;
                    try {
                        nsd.resolveService(info, new NsdManager.ResolveListener() {
                            @Override public void onResolveFailed(NsdServiceInfo i, int code) { }
                            @Override public void onServiceResolved(NsdServiceInfo resolved) {
                                if (resolved.getHost() instanceof Inet4Address) {
                                    String address = resolved.getHost().getHostAddress();
                                    discoveryWork.execute(() -> verifyAndAdd(address, ""));
                                }
                            }
                        });
                    } catch (Exception ignored) { }
                }
            };
            nsd.discoverServices("_wled._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception ex) {
            finishScan("Discovery unavailable: " + concise(ex));
            return;
        }
        // Also leverage WLED's own cached peer discovery: /json/nodes.
        for (WledDevice device : new ArrayList<>(devices)) {
            discoveryWork.execute(() -> {
                try {
                    JSONObject nodes = WledApi.nodes(device.host);
                    JSONArray list = nodes.optJSONArray("nodes");
                    if (list == null) return;
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject node = list.optJSONObject(i);
                        if (node != null) {
                            String host = normalizeHost(node.optString("ip", ""));
                            if (!host.isEmpty()) verifyAndAdd(host, "");
                        }
                    }
                } catch (Exception ignored) { }
            });
        }
        ui.postDelayed(() -> {
            if (scanning) finishScan("Discovery finished. Use Add device for any missing lights.");
        }, 11500);
    }

    private void finishScan(String result) {
        stopDiscovery();
        message = result;
        render();
        if (drawerOverlay != null && activeDrawer.equals("devices")) showDrawer();
    }

    private void stopDiscovery() {
        if (nsd != null && discoveryListener != null) {
            try { nsd.stopServiceDiscovery(discoveryListener); } catch (Exception ignored) { }
        }
        discoveryListener = null;
        if (multicastLock != null) {
            try { if (multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) { }
        }
        multicastLock = null;
        scanning = false;
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private static String concise(Exception e) {
        String s = e.getMessage();
        if (s == null || s.isEmpty()) s = e.getClass().getSimpleName();
        return s.length() > 86 ? s.substring(0, 86) + "…" : s;
    }
    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }

    // ---------- Android TV friendly view helpers ----------
    private int dp(float value) { return (int) (value * uiScale * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(int gravity) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(gravity);
        return l;
    }
    private View space(int width, int height) {
        View v = new View(this);
        v.setLayoutParams(sized(width, height));
        return v;
    }
    private LinearLayout.LayoutParams sized(int width, int height) {
        return new LinearLayout.LayoutParams(width == -1 ? -1 : dp(width), height == -1 ? -1 : dp(height));
    }
    private LinearLayout.LayoutParams lpBottom(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(margin);
        return lp;
    }
    private TextView text(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size * Math.max(.83f, uiScale));
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }
    private GradientDrawable shape(int color, int radius, int borderColor, int borderDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (borderDp > 0) d.setStroke(dp(borderDp), borderColor);
        return d;
    }
    private LinearLayout panel() {
        LinearLayout l = vertical();
        l.setPadding(dp(19), dp(18), dp(19), dp(18));
        l.setBackground(shape(PANEL, 17, BORDER, 1));
        return l;
    }
    private TextView button(String title, String tag, Runnable clicked, boolean emphasis) {
        TextView view = text(title, 12, emphasis ? 0xFF05272A : WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        view.setBackground(shape(emphasis ? ACCENT : TILE, 12, emphasis ? ACCENT : BORDER, 1));
        view.setFocusable(true);
        view.setClickable(true);
        view.setTag(tag);
        view.setOnClickListener(v -> clicked.run());
        if (!tvMode) { view.setMinWidth(dp(48)); view.setMinHeight(dp(48)); }
        setFocusStyle(view, emphasis ? ACCENT : TILE, 12);
        return view;
    }
    private void setFocusStyle(View view, int original, int radius) {
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) rememberFocus(v);
            int bg = focused ? 0xFF304E65 : original;
            v.setBackground(shape(bg, radius, focused ? ACCENT :
                    (original == ACCENT ? ACCENT : BORDER), focused ? 2 : 1));
            v.setScaleX(focused ? 1.025f : 1f);
            v.setScaleY(focused ? 1.045f : 1f);
            v.setElevation(focused ? dp(8) : 0);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                // Both the filled buttons and outlined buttons need readable focus text.
                String tag = v.getTag() instanceof String ? (String) v.getTag() : "";
                int normalText = (tag.equals("speed:adjust") || tag.equals("intensity:adjust")) ?
                        ACCENT : original == ACCENT ? 0xFF05272A : WHITE;
                tv.setTextColor(focused ? WHITE : normalText);
            }
        });
    }


    // ---------- Remote navigation / focus management (v1.3) ----------
    private String focusedTag() {
        View view = getCurrentFocus();
        return view != null && view.getTag() instanceof String ? (String) view.getTag() : null;
    }

    private void rememberFocus(View v) {
        if (!(v.getTag() instanceof String)) return;
        String tag = (String) v.getTag();
        if (tag.startsWith("nav:")) rememberedRailFocusTag = tag;
        else if (!tag.startsWith("drawer:") && !tag.startsWith("device:") &&
                !tag.startsWith("effect:") && !tag.startsWith("preset:") &&
                !tag.startsWith("palette:") && !tag.startsWith("group:"))
            rememberedDashboardFocusTag = tag;
    }

    private boolean restoreFocus(String preferred, String fallback) {
        View v = findTag(preferred);
        if (v == null) v = findTag(fallback);
        if (v == null) v = findTag("nav:home");
        return v != null && v.requestFocus();
    }

    private View findTag(String tag) {
        return rootFrame == null || tag == null ? null : rootFrame.findViewWithTag(tag);
    }

    private static boolean isChildOf(View view, ViewGroup parent) {
        if (view == null || parent == null) return false;
        View cursor = view;
        while (cursor != null) {
            if (cursor == parent) return true;
            ViewParent ancestor = cursor.getParent();
            cursor = ancestor instanceof View ? (View) ancestor : null;
        }
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // Touch devices keep Android's native keyboard/IME and accessibility navigation.
        if (!tvMode) return super.dispatchKeyEvent(event);
        View current = getCurrentFocus();
        if (current == null || !isChildOf(current, rootFrame)) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK && adjustingSliderTag != null) {
            if (pressed) exitSliderMode();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (pressed && event.getRepeatCount() == 0 && drawerOverlay == null) openDrawer("devices");
            return drawerOverlay == null || activeDrawer.equals("devices");
        }
        String tag = focusedTag();
        if (isSliderTag(tag) && (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (pressed && event.getRepeatCount() == 0) toggleSlider(tag);
            return true;
        }
        if (adjustingSliderTag != null && (code == KeyEvent.KEYCODE_DPAD_LEFT ||
                code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (pressed) {
                int amount = event.getRepeatCount() >= 12 ? 15 : event.getRepeatCount() >= 5 ? 8 : 3;
                adjustSliderByDpad(code == KeyEvent.KEYCODE_DPAD_RIGHT ? amount : -amount);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                // Send the final value once the user stops holding the direction.
                ui.removeCallbacks(commitSlider);
                ui.postDelayed(commitSlider, 65);
            }
            return true;
        }
        if (pressed && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // Up/down leaves adjustment mode without submitting an unwanted click.
            if (adjustingSliderTag != null) {
                exitSliderMode();
                current = getCurrentFocus();
            }
            if (current != null && routeDpad(current, code)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isSliderTag(String tag) {
        return "bri:adjust".equals(tag) || "speed:adjust".equals(tag) || "intensity:adjust".equals(tag);
    }

    private void toggleSlider(String tag) {
        if (!isSliderTag(tag)) return;
        if (tag.equals(adjustingSliderTag)) { exitSliderMode(); return; }
        if (adjustingSliderTag != null) exitSliderMode();
        adjustingSliderTag = tag;
        View v = findTag(tag);
        if (v != null) v.setBackground(shape(0xFF19568B, 8, ACCENT, 2));
        Toast.makeText(this, "◀ ▶ adjust  ·  Back to finish", Toast.LENGTH_SHORT).show();
    }

    private void exitSliderMode() {
        flushSliderCommand();
        String old = adjustingSliderTag;
        adjustingSliderTag = null;
        View v = findTag(old);
        if (v != null) {
            // Remain visibly focused after leaving edit mode.
            v.setBackground(shape(0xFF304E65, 8, ACCENT, 2));
            v.requestFocus();
        }
        if (deferredStructuralRender) {
            deferredStructuralRender = false;
            render();
        }
    }

    private void adjustSliderByDpad(int delta) {
        WledDevice selected = currentDevice();
        if (selected == null || adjustingSliderTag == null) return;
        String type = adjustingSliderTag.substring(0, adjustingSliderTag.indexOf(':'));
        int current = "bri".equals(type) ? selected.brightness :
                "speed".equals(type) ? selected.speed : selected.intensity;
        int value = clamp(current + delta, "bri".equals(type) ? 1 : 0, 255);
        if (value == current) return;
        if (pendingSliderType != null && !pendingSliderType.equals(type)) flushSliderCommand();
        for (WledDevice d : targets()) {
            if ("bri".equals(type)) d.brightness = value;
            else if ("speed".equals(type)) d.speed = value;
            else d.intensity = value;
        }
        pendingSliderType = type;
        pendingSliderValue = value;
        refreshVisibleState();
        ui.removeCallbacks(commitSlider);
        ui.postDelayed(commitSlider, 210); // coalesce rapid key repeats to protect ESP devices
    }

    private void flushSliderCommand() {
        ui.removeCallbacks(commitSlider);
        String type = pendingSliderType;
        int value = pendingSliderValue;
        pendingSliderType = null;
        pendingSliderValue = -1;
        if (type == null || value < 0) return;
        if ("bri".equals(type)) send("brightness", d -> WledApi.object("bri", value));
        else if ("speed".equals(type))
            send("speed", d -> segmentPatch(d, new JSONObject().put("sx", value)));
        else if ("intensity".equals(type))
            send("intensity", d -> segmentPatch(d, new JSONObject().put("ix", value)));
    }

    private boolean routeDpad(View current, int code) {
        if (drawerOverlay != null) return routeDrawerDpad(current, code);
        String tag = focusedTag();
        if (tag != null && tag.startsWith("nav:")) {
            String[] nav = {"nav:home", "nav:devices", "nav:favorites", "nav:scenes",
                    "nav:effects", "nav:presets", "nav:settings"};
            int index = 0;
            for (int i = 0; i < nav.length; i++) if (nav[i].equals(tag)) index = i;
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                int next = clamp(index + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1), 0, nav.length - 1);
                if (next != index) restoreFocus(nav[next], tag);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                restoreFocus(rememberedDashboardFocusTag, "power:on");
                return true;
            }
            return true; // Left edge of the navigation rail.
        }
        List<View> choices = new ArrayList<>();
        collectFocusable(workspace, choices);
        collectFocusable(navList, choices);
        View next = nearestDirectionalView(current, choices, code);
        if (next != null) { next.requestFocus(); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT)
            return restoreFocus(rememberedRailFocusTag, "nav:home");
        return true; // At an edge: keep focus rather than let Android jump unpredictably.
    }

    private boolean routeDrawerDpad(View current, int code) {
        List<View> choices = new ArrayList<>();
        collectFocusable(drawerOverlay, choices);
        if (choices.isEmpty()) return true;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            // Vertical order follows the visual reading order, including drawer footer buttons.
            int position = choices.indexOf(current);
            int next = clamp(position + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1),
                    0, choices.size() - 1);
            if (position == -1) next = 0;
            choices.get(next).requestFocus(); // ScrollView brings offscreen entries into view.
            return true;
        }
        // Only move sideways between controls in the same row, never behind the scrim.
        View near = nearestDirectionalView(current, choices, code);
        if (near != null) {
            Rect source = new Rect(), destination = new Rect();
            current.getGlobalVisibleRect(source);
            near.getGlobalVisibleRect(destination);
            if (Math.abs(source.centerY() - destination.centerY()) <= dp(22)) near.requestFocus();
        }
        return true;
    }

    private void collectFocusable(View root, List<View> output) {
        if (root == null || root.getVisibility() != View.VISIBLE) return;
        if (root.isFocusable() && root.isEnabled() && root.isShown() &&
                root.getTag() instanceof String) output.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) collectFocusable(group.getChildAt(i), output);
        }
    }

    /** Deterministic four-way navigation. Prefer beam-aligned controls over diagonal jumps. */
    private View nearestDirectionalView(View current, List<View> candidates, int code) {
        Rect from = new Rect();
        if (!current.getGlobalVisibleRect(from)) return null;
        int cx = from.centerX(), cy = from.centerY();
        View best = null;
        float bestScore = Float.MAX_VALUE;
        for (View candidate : candidates) {
            if (candidate == current) continue;
            Rect rect = new Rect();
            if (!candidate.getGlobalVisibleRect(rect)) continue;
            int dx = rect.centerX() - cx, dy = rect.centerY() - cy;
            int primary, secondary;
            boolean beam;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (dx >= -2) continue;
                primary = -dx; secondary = Math.abs(dy);
                beam = from.top < rect.bottom && from.bottom > rect.top;
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (dx <= 2) continue;
                primary = dx; secondary = Math.abs(dy);
                beam = from.top < rect.bottom && from.bottom > rect.top;
            } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (dy >= -2) continue;
                primary = -dy; secondary = Math.abs(dx);
                beam = from.left < rect.right && from.right > rect.left;
            } else {
                if (dy <= 2) continue;
                primary = dy; secondary = Math.abs(dx);
                beam = from.left < rect.right && from.right > rect.left;
            }
            float score = primary + secondary * 2.5f + (beam ? 0 : dp(105));
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    /** Refresh actual values without destroying buttons, focus, scroll position, or slider mode. */
    private void refreshVisibleState() {
        if (rootFrame == null) return;
        WledDevice d = currentDevice();
        int online = 0;
        for (WledDevice item : devices) if (item.online) online++;
        if (subheading != null)
            subheading.setText(online + "/" + devices.size() + " online" + (scanning ? "  ·  Scanning…" : ""));
        setLiveText("live:title", selectedName());
        String connection = multiTarget() ? targets().size() + " WLED controllers" :
                d == null ? "Find your WLED devices" :
                (d.online ? "● Online" : "○ Offline") + "  ·  " + d.host +
                        (d.version.isEmpty() ? "" : "  ·  WLED " + d.version);
        setLiveText("live:connection", connection);
        TextView connectionLabel = (TextView) findTag("live:connection");
        if (connectionLabel != null) connectionLabel.setTextColor(d != null && !multiTarget() && !d.online ? BAD : MUTED);
        if (d == null) return;
        int brightness = Math.round(d.brightness * 100f / 255);
        updateTouchSlider("touch:bri", d.brightness);
        updateTouchSlider("touch:speed", d.speed);
        updateTouchSlider("touch:intensity", d.intensity);
        setLiveText("bri:adjust", brightness + "%");
        setLiveText("speed:adjust", Math.round(d.speed * 100f / 255) + "%");
        setLiveText("intensity:adjust", Math.round(d.intensity * 100f / 255) + "%");
        setSliderDescription("bri:adjust", "Brightness", d.brightness);
        setSliderDescription("speed:adjust", "Speed", d.speed);
        setSliderDescription("intensity:adjust", "Intensity", d.intensity);
        LinearLayout track = (LinearLayout) findTag("live:bri:track");
        if (track != null && track.getChildCount() == 2) {
            float progress = Math.max(.01f, Math.min(1f, d.brightness / 255f));
            LinearLayout.LayoutParams fill = (LinearLayout.LayoutParams) track.getChildAt(0).getLayoutParams();
            LinearLayout.LayoutParams empty = (LinearLayout.LayoutParams) track.getChildAt(1).getLayoutParams();
            fill.weight = progress; empty.weight = 1f - progress;
            track.getChildAt(0).setLayoutParams(fill);
            track.getChildAt(1).setLayoutParams(empty);
        }
        updateButtonActive("power:on", d.on);
        updateButtonActive("power:off", !d.on);
        for (int i = 0; i < d.effects.size(); i++) {
            View tile = findTag("fx:" + i);
            if (tile == null || tile.hasFocus()) continue;
            boolean selected = d.effect == i;
            int color = selected ? 0xFF1B416B : TILE;
            setFocusStyle(tile, color, 10);
            tile.setBackground(shape(color, 10, selected ? 0xFF299BFF : BORDER, selected ? 2 : 1));
        }
        for (WledDevice.Preset preset : d.presets) {
            updateButtonActive("scene:" + preset.id,
                    !multiTarget() && d.currentPreset == preset.id);
        }
        // Keep drawer focus and its scroll offset exactly where they were.
        if (drawerOverlay != null && "devices".equals(activeDrawer)) {
            for (WledDevice item : devices) {
                View card = drawerOverlay.findViewWithTag("device:" + item.host);
                if (card instanceof TextView) ((TextView) card).setText("◈  " + item.name() +
                        "\n" + (item.online ? item.on ? "● On" : "○ Off" : "○ Offline") +
                        "   ·   " + item.host);
            }
        }
    }

    private void updateTouchSlider(String tag, int value) {
        View slider = findTag(tag);
        if (slider instanceof SeekBar && !slider.isPressed()) {
            SeekBar seek = (SeekBar) slider;
            if (seek.getProgress() != value) seek.setProgress(value);
            setLiveText(tag + ":value", Math.round(value * 100f / 255f) + "%");
        }
    }

    private void setSliderDescription(String tag, String label, int level) {
        View v = findTag(tag);
        if (v != null) v.setContentDescription(label + " " + Math.round(level * 100f / 255) +
                " percent. Press OK then left or right to adjust; Back to finish.");
    }

    private void setLiveText(String tag, String value) {
        View v = findTag(tag);
        if (v instanceof TextView && !value.contentEquals(((TextView) v).getText()))
            ((TextView) v).setText(value);
    }

    private void updateButtonActive(String tag, boolean active) {
        View view = findTag(tag);
        if (!(view instanceof TextView) || view.hasFocus()) return;
        int base = active ? ACCENT : TILE;
        setFocusStyle(view, base, 12);
        view.setBackground(shape(base, 12, active ? ACCENT : BORDER, 1));
        ((TextView) view).setTextColor(active ? 0xFF05272A : WHITE);
    }

}
