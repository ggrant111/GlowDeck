package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;

/** WLED /json/fxdata color-slot metadata and non-destructive segment color patches. */
final class WledEffectColors {
    private WledEffectColors() {}
    private static final String[] DEFAULTS = {"Fx", "Bg", "Cs"};

    /** A null label means this color slot is disabled for the selected effect. */
    static String[] labels(String metadata) {
        String[] labels = new String[3];
        // WLED <0.14 or an unavailable metadata entry: offer legacy three slots.
        if (metadata == null || metadata.indexOf(';') < 0) {
            System.arraycopy(DEFAULTS, 0, labels, 0, 3);
            return labels;
        }
        String[] sections = metadata.split(";", -1);
        if (sections.length < 2) {
            System.arraycopy(DEFAULTS, 0, labels, 0, 3);
            return labels;
        }
        // An explicitly empty color section means NO colors, not all colors.
        String[] values = sections[1].split(",", -1);
        for (int i = 0; i < labels.length && i < values.length; i++) {
            String value = values[i].trim();
            labels[i] = value.isEmpty() ? null : "!".equals(value) ? DEFAULTS[i] : value;
        }
        return labels;
    }

    static boolean supports(String metadata, int slot) {
        return slot >= 0 && slot < 3 && labels(metadata)[slot] != null;
    }

    static int rgb(JSONArray colors, int slot) {
        if (colors == null || slot < 0 || slot > 2) return 0xFF808080;
        JSONArray color = colors.optJSONArray(slot);
        if (color == null) return 0xFF808080;
        return 0xFF000000 | (bounded(color.optInt(0,128)) << 16) |
                (bounded(color.optInt(1,128)) << 8) | bounded(color.optInt(2,128));
    }

    /** Send all three slots: preserve other colors and the existing RGBW white channels. */
    static JSONArray withSlot(JSONArray existing, int slot, int rgb) throws JSONException {
        if (slot < 0 || slot > 2) throw new JSONException("Unsupported color slot");
        JSONArray result = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONArray original = existing == null ? null : existing.optJSONArray(i);
            JSONArray color = new JSONArray();
            for (int component = 0; component < 3; component++) {
                int value = original == null ? 0 : bounded(original.optInt(component, 0));
                if (i == slot) value = (rgb >> (16 - component * 8)) & 255;
                color.put(value);
            }
            if (original != null && original.length() > 3)
                color.put(bounded(original.optInt(3, 0)));
            result.put(color);
        }
        return result;
    }

    static JSONArray copy(JSONArray value) {
        if (value == null) return new JSONArray();
        try { return new JSONArray(value.toString()); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    private static int bounded(int value) { return Math.max(0, Math.min(255, value)); }
}
