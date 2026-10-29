package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;

/** Performs a single WLED state POST and then separately checks reported device state. */
final class WledCommandVerifier {
    interface Sender { void send(String host, JSONObject change) throws Exception; }
    interface Reader { JSONObject snapshot(String host) throws Exception; }
    interface PresetsReader { JSONObject presets(String host) throws Exception; }
    interface SentCallback { void onSent(String host); }
    static final class UnconfirmedException extends Exception {
        UnconfirmedException(String detail) { super(detail); }
    }
    private WledCommandVerifier() { }

    static void sendAndVerify(String host, JSONObject patch, Sender sender, Reader reader,
                              PresetsReader presetsReader, SentCallback sent) throws Exception {
        sender.send(host, patch);
        if (sent != null) sent.onSent(host);
        String reason = "device has not reported the requested state";
        for (int attempt = 0; attempt < 4; attempt++) {
            if (attempt != 0) Thread.sleep(220L * attempt);
            try {
                JSONObject observed = patch.has("psave") ? presetsReader.presets(host) : reader.snapshot(host);
                if (patch.has("psave") ? matchesSavedPreset(patch, observed) : matches(patch, observed)) return;
                reason = "device still reports a different state";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                reason = "unable to read device state: " +
                        (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        throw new UnconfirmedException(reason);
    }

    static boolean matchesSavedPreset(JSONObject patch, JSONObject presets) {
        if (patch == null || presets == null) return false;
        int id = patch.optInt("psave", -1);
        if (id < 1) return false;
        JSONObject saved = presets.optJSONObject(String.valueOf(id));
        if (saved == null) return false;
        return !patch.has("n") || patch.optString("n").equals(saved.optString("n"));
    }

    static boolean matches(JSONObject requested, JSONObject observed) {
        if (requested == null || observed == null) return false;
        JSONObject state = observed.optJSONObject("state");
        if (state == null || !scalarsMatch(requested, state, "on", "bri", "ps")) return false;
        if (requested.has("seg")) {
            JSONArray actual = state.optJSONArray("seg");
            Object wanted = requested.opt("seg");
            if (wanted instanceof JSONArray) {
                JSONArray segments = (JSONArray) wanted;
                if (actual == null || segments.length() == 0) return false;
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject expected = segments.optJSONObject(i);
                    if (expected == null || !segmentMatches(expected, actual)) return false;
                }
            } else if (wanted instanceof JSONObject) {
                if (!segmentMatches((JSONObject) wanted, actual)) return false;
            } else return false;
        }
        // tt/udpn/ib/sb are write-only command options, not persistent WLED state.
        return true;
    }

    private static boolean segmentMatches(JSONObject expected, JSONArray actual) {
        if (actual == null) return false;
        for (int i = 0; i < actual.length(); i++) {
            JSONObject current = actual.optJSONObject(i);
            if (current == null) continue;
            if (expected.has("id") && expected.optInt("id", -999) != current.optInt("id", i)) continue;
            if (scalarsMatch(expected, current,
                    "on", "bri", "fx", "pal", "sx", "ix", "c1", "c2", "c3", "o1", "o2", "o3")
                    && colorsMatch(expected.optJSONArray("col"), current.optJSONArray("col"))) return true;
        }
        return false;
    }

    private static boolean colorsMatch(JSONArray desired, JSONArray observed) {
        if (desired == null) return true;
        if (observed == null || desired.length() > observed.length()) return false;
        for (int i = 0; i < desired.length(); i++) {
            JSONArray a = desired.optJSONArray(i), b = observed.optJSONArray(i);
            if (a == null || b == null || a.length() > b.length()) return false;
            for (int j = 0; j < a.length(); j++)
                if (a.optInt(j, -1000) != b.optInt(j, -2000)) return false;
        }
        return true;
    }

    private static boolean scalarsMatch(JSONObject wanted, JSONObject actual, String... fields) {
        for (String key : fields) {
            if (!wanted.has(key)) continue;
            if (!actual.has(key)) return false;
            if ("on".equals(key)) {
                if (wanted.optBoolean(key) != actual.optBoolean(key)) return false;
            } else if ("o1".equals(key) || "o2".equals(key) || "o3".equals(key)) {
                if (wanted.optBoolean(key) != actual.optBoolean(key)) return false;
            } else if (wanted.optInt(key, -999) != actual.optInt(key, -998)) return false;
        }
        return true;
    }
}
