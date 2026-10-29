package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A GlowDeck scene is a collection of device-specific desired states; it never edits WLED presets. */
final class GlowScene {
    final String id;
    String name;
    int transitionMs;
    final List<Target> targets = new ArrayList<>();

    GlowScene(String id, String name, int transitionMs) {
        this.id = id;
        this.name = name;
        this.transitionMs = Math.max(0, Math.min(60000, transitionMs));
    }

    static GlowScene create(String name, int transitionMs) {
        return new GlowScene(UUID.randomUUID().toString(), name, transitionMs);
    }

    JSONObject toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (Target t : targets) array.put(t.toJson());
        return new JSONObject().put("id", id).put("name", name)
                .put("transitionMs", transitionMs).put("targets", array);
    }

    static GlowScene fromJson(JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "").trim();
        String name = json.optString("name", "").trim();
        if (id.isEmpty() || name.isEmpty()) return null;
        GlowScene scene = new GlowScene(id, name, json.optInt("transitionMs", 700));
        JSONArray targets = json.optJSONArray("targets");
        if (targets == null) return null;
        for (int i = 0; i < targets.length() && scene.targets.size() < 32; i++) {
            Target t = Target.fromJson(targets.optJSONObject(i));
            if (t != null) scene.targets.add(t);
        }
        return scene.targets.isEmpty() ? null : scene;
    }

    static String identity(JSONObject info, String host) {
        String id = info == null ? "" : info.optString("deviceId", "").trim();
        if (!id.isEmpty()) return "wled:" + id.toLowerCase(java.util.Locale.US);
        String mac = info == null ? "" : info.optString("mac", "").trim();
        if (!mac.isEmpty()) return "mac:" + mac.toLowerCase(java.util.Locale.US);
        return "host:" + host.toLowerCase(java.util.Locale.US);
    }

    static final class Target {
        final String deviceId;
        final String lastHost;
        final String name;
        JSONObject state;

        Target(String deviceId, String lastHost, String name, JSONObject state) {
            this.deviceId = deviceId;
            this.lastHost = lastHost;
            this.name = name;
            this.state = state;
        }

        static Target capture(WledDevice device, JSONObject snapshot) throws JSONException {
            JSONObject state = snapshot.optJSONObject("state");
            if (state == null) throw new JSONException("Missing WLED state");
            return new Target(identity(snapshot.optJSONObject("info"), device.host), device.host,
                    device.name(), sanitized(state, snapshot.optJSONArray("effects"),
                            snapshot.optJSONArray("palettes")));
        }

        static Target fromJson(JSONObject item) {
            if (item == null) return null;
            String id = item.optString("deviceId", "").trim();
            String host = item.optString("host", "").trim();
            JSONObject state = item.optJSONObject("state");
            if (id.isEmpty() || host.isEmpty() || state == null) return null;
            try {
                return new Target(id, host, item.optString("name", host),
                        sanitized(state, null, null));
            } catch (JSONException ignored) { return null; }
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("deviceId", deviceId).put("host", lastHost)
                    .put("name", name).put("state", state);
        }

        /** Restrict saved state to lighting fields, never LED strip layout, reboots, or network settings. */
        private static JSONObject sanitized(JSONObject state, JSONArray effects, JSONArray palettes)
                throws JSONException {
            JSONObject out = new JSONObject().put("on", state.optBoolean("on", false))
                    .put("bri", Math.max(1, Math.min(255, state.optInt("bri", 128))));
            JSONArray segments = state.optJSONArray("seg");
            JSONArray selected = new JSONArray();
            if (segments != null) for (int i = 0; i < segments.length() && i < 32; i++) {
                JSONObject s = segments.optJSONObject(i);
                if (s == null) continue;
                if (s.has("start") && s.has("stop") && s.optInt("stop") <= s.optInt("start")) continue;
                int id = s.optInt("id", i);
                if (id < 0 || id > 63) continue;
                JSONObject part = new JSONObject().put("id", id).put("on", s.optBoolean("on", true));
                for (String key : new String[]{"bri", "fx", "pal", "sx", "ix", "c1", "c2", "c3"}) {
                    if (s.has(key) && s.opt(key) instanceof Number) part.put(key, s.optInt(key));
                }
                for (String key : new String[]{"o1", "o2", "o3"})
                    if (s.has(key) && s.opt(key) instanceof Boolean) part.put(key, s.optBoolean(key));
                JSONArray colors = s.optJSONArray("col");
                if (colors != null) {
                    JSONArray safeColors = new JSONArray();
                    for (int c = 0; c < Math.min(3, colors.length()); c++) {
                        JSONArray rgb = colors.optJSONArray(c);
                        if (rgb == null) continue;
                        JSONArray safeRgb = new JSONArray();
                        for (int k = 0; k < Math.min(4, rgb.length()); k++)
                            safeRgb.put(Math.max(0, Math.min(255, rgb.optInt(k))));
                        if (safeRgb.length() >= 3) safeColors.put(safeRgb);
                    }
                    if (safeColors.length() > 0) part.put("col", safeColors);
                }
                String fxName = s.optString("fxName", "");
                if (fxName.isEmpty() && effects != null) fxName = effects.optString(s.optInt("fx", -1), "");
                String palName = s.optString("palName", "");
                if (palName.isEmpty() && palettes != null) palName = palettes.optString(s.optInt("pal", -1), "");
                if (!fxName.isEmpty()) part.put("fxName", fxName);
                if (!palName.isEmpty()) part.put("palName", palName);
                selected.put(part);
            }
            if (selected.length() > 0) out.put("seg", selected);
            return out;
        }

        Target withPower(boolean on) throws JSONException {
            JSONObject updated = new JSONObject(state.toString()).put("on", on);
            return new Target(deviceId, lastHost, name, updated);
        }
        Target withBrightness(int bri) throws JSONException {
            JSONObject updated = new JSONObject(state.toString())
                    .put("on", true).put("bri", Math.max(1, Math.min(255, bri)));
            return new Target(deviceId, lastHost, name, updated);
        }
        Target withColor(int rgb) throws JSONException {
            JSONObject updated = new JSONObject(state.toString()).put("on", true);
            JSONArray seg = updated.optJSONArray("seg");
            if (seg == null || seg.length() == 0) seg = new JSONArray().put(new JSONObject().put("id", 0));
            JSONArray col = new JSONArray().put(new JSONArray()
                    .put((rgb >> 16) & 255).put((rgb >> 8) & 255).put(rgb & 255));
            for (int i = 0; i < seg.length(); i++) {
                JSONObject part = seg.getJSONObject(i);
                part.put("on", true).put("fx", 0).put("fxName", "Solid").put("col", col);
            }
            updated.put("seg", seg);
            return new Target(deviceId, lastHost, name, updated);
        }
        Target withEffect(String effect) throws JSONException {
            JSONObject updated = new JSONObject(state.toString()).put("on", true);
            JSONArray seg = updated.optJSONArray("seg");
            if (seg == null || seg.length() == 0) seg = new JSONArray().put(new JSONObject().put("id", 0));
            for (int i = 0; i < seg.length(); i++) {
                JSONObject part = seg.getJSONObject(i);
                part.put("on", true).put("fxName", effect);
                part.remove("fx"); // exact ID resolved against target firmware on activation
            }
            updated.put("seg", seg);
            return new Target(deviceId, lastHost, name, updated);
        }
    }
}
