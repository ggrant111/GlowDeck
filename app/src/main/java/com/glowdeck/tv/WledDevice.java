package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class WledDevice {
    final String host;
    String alias;
    String reportedName = "";
    String version = "";
    String deviceId = "";
    String mac = "";
    boolean online = false;
    boolean on = false;
    int brightness = 128;
    int effect = 0;
    int palette = 0;
    int speed = 128;
    int intensity = 128;
    int currentPreset = -1;
    int rgb = 0xFFFFA75F;
    int ledCount = 0;
    final List<Segment> segments = new ArrayList<>();
    final List<String> effects = new ArrayList<>();
    final List<String> palettes = new ArrayList<>();
    final List<Preset> presets = new ArrayList<>();
    boolean loadedPresets = false;
    boolean loadingPresets = false;
    String error = "";
    String lastStateSignature = "";

    WledDevice(String host, String alias) { this.host = host; this.alias = alias; }

    String identity() {
        if (!deviceId.isEmpty()) return "wled:" + deviceId.toLowerCase(java.util.Locale.US);
        if (!mac.isEmpty()) return "mac:" + mac.toLowerCase(java.util.Locale.US);
        return "host:" + host.toLowerCase(java.util.Locale.US);
    }

    String name() {
        return alias != null && !alias.trim().isEmpty() ? alias :
               reportedName.isEmpty() ? host : reportedName;
    }

    void applySnapshot(JSONObject response) {
        JSONObject state = response.optJSONObject("state");
        JSONObject info = response.optJSONObject("info");
        if (state == null || info == null) return;
        online = true;
        error = "";
        lastStateSignature = state.toString();
        reportedName = info.optString("name", reportedName);
        version = info.optString("ver", version);
        deviceId = info.optString("deviceId", deviceId);
        mac = info.optString("mac", mac);
        ledCount = info.optJSONObject("leds") == null ? 0 : info.optJSONObject("leds").optInt("count", 0);
        on = state.optBoolean("on", false);
        brightness = state.optInt("bri", brightness);
        currentPreset = state.optInt("ps", -1);

        segments.clear();
        JSONArray seg = state.optJSONArray("seg");
        if (seg != null) {
            for (int i = 0; i < seg.length(); i++) {
                JSONObject s = seg.optJSONObject(i);
                if (s == null) continue;
                int id = s.optInt("id", i);
                if (s.optInt("stop", 1) <= s.optInt("start", 0)) continue;
                segments.add(new Segment(id, s.optString("n", ""), s.optBoolean("on", true)));
            }
            JSONObject chosen = null;
            for (int i = 0; i < seg.length(); i++) {
                JSONObject candidate = seg.optJSONObject(i);
                if (candidate != null && candidate.optBoolean("sel", false)) { chosen = candidate; break; }
            }
            if (chosen == null) chosen = seg.optJSONObject(0);
            if (chosen != null) {
                effect = chosen.optInt("fx", 0);
                palette = chosen.optInt("pal", 0);
                speed = chosen.optInt("sx", 128);
                intensity = chosen.optInt("ix", 128);
                JSONArray colors = chosen.optJSONArray("col");
                if (colors != null) {
                    JSONArray primary = colors.optJSONArray(0);
                    if (primary != null) rgb = 0xFF000000 |
                            (primary.optInt(0, 255) << 16) |
                            (primary.optInt(1, 255) << 8) | primary.optInt(2, 255);
                }
            }
        }
        JSONArray eff = response.optJSONArray("effects");
        if (eff != null) {
            effects.clear();
            for (int i = 0; i < eff.length(); i++) effects.add(eff.optString(i, ""));
        }
        JSONArray pal = response.optJSONArray("palettes");
        if (pal != null) {
            palettes.clear();
            for (int i = 0; i < pal.length(); i++) palettes.add(pal.optString(i, ""));
        }
    }

    void applyPresets(JSONObject data) {
        loadedPresets = true;
        loadingPresets = false;
        presets.clear();
        for (int id = 1; id <= 250; id++) {
            JSONObject p = data.optJSONObject(String.valueOf(id));
            if (p == null) continue;
            String name = p.optString("n", "Preset " + id);
            presets.add(new Preset(id, name.isEmpty() ? "Preset " + id : name));
        }
    }

    static final class Segment {
        final int id;
        final String name;
        final boolean on;
        Segment(int id, String name, boolean on) { this.id = id; this.name = name; this.on = on; }
        String label() { return name.isEmpty() ? "Segment " + (id + 1) : name; }
    }

    static final class Preset {
        final int id;
        final String name;
        Preset(int id, String name) { this.id = id; this.name = name; }
    }
}
