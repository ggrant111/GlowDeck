package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Executes one requested multi-device scene using WLED's JSON state API. No UI dependencies. */
final class SceneExecutor {
    interface Reader { JSONObject snapshot(String host) throws Exception; }
    interface Sender { void send(String host, JSONObject patch) throws Exception; }

    static final class Result {
        int succeeded;
        int missing;
        int failed;
        int unconfirmed;
        int skippedSegments;
        final List<String> problems = new ArrayList<>();
        String summary(int total) {
            return succeeded + "/" + total + " lights confirmed" +
                    (missing > 0 ? " · " + missing + " missing" : "") +
                    (failed > 0 ? " · " + failed + " failed" : "") +
                    (unconfirmed > 0 ? " · " + unconfirmed + " not confirmed" : "") +
                    (skippedSegments > 0 ? " · " + skippedSegments + " unsupported segments/effects" : "");
        }
    }

    static Result execute(GlowScene scene, List<WledDevice> available, Reader reader, Sender sender) {
        Result result = new Result();
        for (GlowScene.Target target : scene.targets) {
            WledDevice device = findDevice(target, available);
            if (device == null) {
                result.missing++;
                result.problems.add(target.name + ": controller not found (re-scan devices)");
                continue;
            }
            try {
                JSONObject snapshot = reader.snapshot(device.host);
                // Verify identity before applying to avoid controlling a different device after DHCP reassignment.
                String verified = GlowScene.identity(snapshot.optJSONObject("info"), device.host);
                if (!target.deviceId.startsWith("host:") && !verified.equals(target.deviceId)) {
                    result.failed++;
                    result.problems.add(target.name + ": device identity changed; recapture this light");
                    continue;
                }
                int[] skipped = {0};
                JSONObject patch = payload(scene, target, snapshot, skipped);
                sender.send(device.host, patch);
                result.succeeded++;
                result.skippedSegments += skipped[0];
                if (skipped[0] > 0) result.problems.add(target.name + ": " + skipped[0] + " segments/effects unavailable");
            } catch (WledCommandVerifier.UnconfirmedException ex) {
                result.unconfirmed++;
                result.problems.add(target.name + ": request sent but not confirmed (" + ex.getMessage() + ")");
            } catch (Exception ex) {
                result.failed++;
                result.problems.add(target.name + ": " + (ex.getMessage() == null ? "request failed" : ex.getMessage()));
            }
        }
        return result;
    }

    static WledDevice findDevice(GlowScene.Target target, List<WledDevice> devices) {
        for (WledDevice d : devices) if (target.deviceId.equals(d.identity())) return d;
        for (WledDevice d : devices) if (target.lastHost.equalsIgnoreCase(d.host) &&
                (target.deviceId.startsWith("host:") || d.identity().startsWith("host:"))) return d;
        return null;
    }

    /** Never reapply saved physical bounds, segment additions, selection, or controller settings. */
    static JSONObject payload(GlowScene scene, GlowScene.Target target, JSONObject current, int[] skipped)
            throws JSONException {
        JSONObject desired = target.state;
        JSONObject patch = new JSONObject()
                .put("on", desired.optBoolean("on", false))
                .put("bri", Math.max(1, Math.min(255, desired.optInt("bri", 128))))
                .put("tt", Math.max(0, Math.min(600, (scene.transitionMs + 50) / 100)))
                .put("udpn", new JSONObject().put("nn", true));
        JSONArray source = desired.optJSONArray("seg");
        JSONArray currentSegments = current.optJSONObject("state") == null ? null :
                current.optJSONObject("state").optJSONArray("seg");
        if (source == null) return patch;
        JSONArray apply = new JSONArray();
        JSONArray effects = current.optJSONArray("effects");
        JSONArray palettes = current.optJSONArray("palettes");
        for (int i = 0; i < source.length(); i++) {
            JSONObject original = source.optJSONObject(i);
            if (original == null) { skipped[0]++; continue; }
            int id = original.optInt("id", -1);
            if (!existingSegment(currentSegments, id)) { skipped[0]++; continue; }
            JSONObject part = new JSONObject().put("id", id);
            for (String key : new String[]{"on", "bri", "fx", "pal", "sx", "ix", "c1", "c2", "c3", "o1", "o2", "o3", "col"})
                if (original.has(key)) part.put(key, original.get(key));
            String fx = original.optString("fxName", "");
            if (!fx.isEmpty()) {
                int mapped = indexByName(effects, fx);
                if (mapped < 0) { skipped[0]++; continue; }
                part.put("fx", mapped);
            }
            String palette = original.optString("palName", "");
            if (!palette.isEmpty()) {
                int mapped = indexByName(palettes, palette);
                if (mapped < 0) { skipped[0]++; continue; }
                part.put("pal", mapped);
            }
            apply.put(part);
        }
        // If none of the saved segment looks can be applied, do not silently change only power/brightness.
        if (source.length() > 0 && apply.length() == 0)
            throw new JSONException("No compatible WLED segments or effects");
        if (apply.length() > 0) patch.put("seg", apply);
        return patch;
    }

    private static boolean existingSegment(JSONArray segments, int id) {
        if (segments == null || id < 0) return false;
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment != null && segment.optInt("id", i) == id &&
                    segment.optInt("stop", 1) > segment.optInt("start", 0)) return true;
        }
        return false;
    }
    private static int indexByName(JSONArray catalog, String name) {
        if (catalog == null) return -1;
        for (int i = 0; i < catalog.length(); i++)
            if (name.equalsIgnoreCase(catalog.optString(i, ""))) return i;
        return -1;
    }
}
