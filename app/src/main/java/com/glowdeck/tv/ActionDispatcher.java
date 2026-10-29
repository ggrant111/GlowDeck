package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Reusable synchronous command engine. Caller chooses its executor; never touch UI here. */
final class ActionDispatcher {
    interface Sender { void send(String host, JSONObject payload) throws Exception; }
    static final class Result {
        int successful;
        int unsupported;
<<<<<<< HEAD
=======
        int unconfirmed;
>>>>>>> 62916f5714b46463127f168628eceeae27726df4
        final List<String> errors = new ArrayList<>();
    }
    private ActionDispatcher() {}

    static Result execute(Favorite favorite, List<WledDevice> devices, Sender sender) {
        Result result = new Result();
        for (WledDevice d : devices) {
            try {
                JSONObject patch = payload(favorite, d);
                if (patch == null) { result.unsupported++; continue; }
                sender.send(d.host, patch);
                result.successful++;
<<<<<<< HEAD
=======
            } catch (WledCommandVerifier.UnconfirmedException error) {
                result.unconfirmed++;
                result.errors.add(d.name() + ": request sent but not confirmed: " + error.getMessage());
>>>>>>> 62916f5714b46463127f168628eceeae27726df4
            } catch (Exception error) {
                result.errors.add(d.name() + ": " + (error.getMessage() == null ? "network error" : error.getMessage()));
            }
        }
        return result;
    }

    /** Return null only when the action is unsupported by this particular WLED device. */
    static JSONObject payload(Favorite favorite, WledDevice d) throws JSONException {
        JSONObject result = new JSONObject();
        switch (favorite.action) {
            case Favorite.POWER:
                return result.put("on", favorite.value == 1);
            case Favorite.BRIGHTNESS:
                return result.put("on", true).put("bri", favorite.value);
            case Favorite.COLOR: {
                int rgb = favorite.value;
<<<<<<< HEAD
                JSONArray color = new JSONArray()
                        .put((rgb >> 16) & 255).put((rgb >> 8) & 255).put(rgb & 255);
                JSONObject change = new JSONObject().put("fx", 0)
                        .put("col", new JSONArray().put(color));
                // Use full segment array when known, including non-selected segments.
                JSONArray segments = new JSONArray();
                for (WledDevice.Segment segment : d.segments)
                    segments.put(new JSONObject(change.toString()).put("id", segment.id));
                return result.put("on", true)
                        .put("seg", segments.length() > 0 ? segments : change);
=======
                // Like dashboard swatches, Favorites edit primary Fx color without resetting
                // the current effect or overwriting secondary/tertiary RGB(W) colors.
                JSONArray segments = new JSONArray();
                for (WledDevice.Segment segment : d.segments) {
                    if (!WledEffectColors.supports(d.metadataForEffect(segment.effect), 0)) continue;
                    segments.put(new JSONObject().put("id", segment.id)
                            .put("col", WledEffectColors.withSlot(segment.colors, 0, rgb)));
                }
                if (segments.length() == 0 && !d.segments.isEmpty()) return null;
                return result.put("on", true).put("seg", segments.length() > 0 ? segments :
                        new JSONObject().put("col", WledEffectColors.withSlot(null, 0, rgb)));
>>>>>>> 62916f5714b46463127f168628eceeae27726df4
            }
            case Favorite.PRESET: {
                int preset = -1;
                for (WledDevice.Preset p : d.presets) {
                    if (!favorite.presetName.isEmpty() && p.name.equalsIgnoreCase(favorite.presetName)) {
                        preset = p.id;
                        break;
                    }
                    if (favorite.presetName.isEmpty() && p.id == favorite.presetId) preset = p.id;
                }
                // For a single device, the saved preset slot works before the catalog loads.
                if (preset == -1 && !favorite.target.equals("*") &&
                        !favorite.target.startsWith("@g:") && d.host.equals(favorite.target) &&
                        !d.loadedPresets && favorite.presetId >= 1 && favorite.presetId <= 250)
                    preset = favorite.presetId;
                return preset >= 1 ? result.put("ps", preset) : null;
            }
            default: return null;
        }
    }
}
