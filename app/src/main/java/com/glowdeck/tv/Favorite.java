package com.glowdeck.tv;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** Immutable, explicit lighting action. Never use an ambiguous power toggle. */
final class Favorite {
    static final String POWER = "power";
    static final String BRIGHTNESS = "brightness";
    static final String COLOR = "color";
    static final String PRESET = "preset";

    final String id;
    final String name;
    final String target; // device host, @g:<group ID>, or * for all devices
    final String action;
    final int value;
    final String presetName;
    final int presetId;

    Favorite(String id, String name, String target, String action, int value,
             String presetName, int presetId) {
        this.id = id;
        this.name = name;
        this.target = target;
        this.action = action;
        this.value = value;
        this.presetName = presetName == null ? "" : presetName;
        this.presetId = presetId;
    }

    static Favorite create(String name, String target, String action, int value,
                           String presetName, int presetId) {
        return new Favorite(UUID.randomUUID().toString(), name, target, action,
                value, presetName, presetId);
    }

    Favorite renamed(String newName) {
        return new Favorite(id, newName, target, action, value, presetName, presetId);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("name", name).put("target", target)
                .put("action", action).put("value", value)
                .put("presetName", presetName).put("presetId", presetId);
    }

    static Favorite fromJson(JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "").trim();
        String name = json.optString("name", "").trim();
        String target = json.optString("target", "").trim();
        String action = json.optString("action", "");
        int value = json.optInt("value", 0);
        String presetName = json.optString("presetName", "");
        int presetId = json.optInt("presetId", -1);
        if (id.isEmpty() || name.isEmpty() || target.isEmpty()) return null;
        switch (action) {
            case POWER:
                if (value != 0 && value != 1) return null;
                break;
            case BRIGHTNESS:
                if (value < 1 || value > 255) return null;
                break;
            case COLOR:
                if (value < 0 || value > 0xFFFFFF) return null;
                break;
            case PRESET:
                if (presetName.isEmpty() && (presetId < 1 || presetId > 250)) return null;
                break;
            default: return null;
        }
        return new Favorite(id, name, target, action, value, presetName, presetId);
    }

    String detail() {
        switch (action) {
            case POWER: return value == 1 ? "Turn on" : "Turn off";
            case BRIGHTNESS: return "Brightness " + Math.round(value * 100f / 255) + "%";
            case COLOR: return String.format(java.util.Locale.US, "Color #%06X", value);
            case PRESET: return "Preset " + (presetName.isEmpty() ? presetId : presetName);
            default: return "Lighting action";
        }
    }
}
