package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class GlowSceneTest {
    private static JSONObject sample(String mac, int fx, int pal) throws Exception {
        return new JSONObject()
                .put("info", new JSONObject().put("mac", mac))
                .put("effects", new JSONArray().put("Solid").put("Rainbow").put("Fire 2012"))
                .put("palettes", new JSONArray().put("Default").put("Ocean").put("Party"))
                .put("state", new JSONObject().put("on", true).put("bri", 97)
                        .put("seg", new JSONArray().put(new JSONObject()
                                .put("id", 0).put("start", 0).put("stop", 80)
                                .put("fx", fx).put("pal", pal).put("sx", 121)
                                .put("col", new JSONArray().put(new JSONArray().put(40).put(30).put(20))))));
    }

    @Test public void captureExcludesStructuralStateAndPreservesEffectNames() throws Exception {
        WledDevice device = new WledDevice("192.168.1.71", "Fireplace");
        GlowScene.Target t = GlowScene.Target.capture(device, sample("abcd", 2, 1));
        assertEquals("mac:abcd", t.deviceId);
        assertEquals(97, t.state.getInt("bri"));
        JSONObject segment = t.state.getJSONArray("seg").getJSONObject(0);
        assertEquals("Fire 2012", segment.getString("fxName"));
        assertEquals("Ocean", segment.getString("palName"));
        assertFalse(segment.has("start"));
        assertFalse(segment.has("stop"));
    }

    @Test public void sceneRoundTripAndIndependentEditing() throws Exception {
        WledDevice device = new WledDevice("one", "A");
        GlowScene.Target initial = GlowScene.Target.capture(device, sample("abc", 1, 2));
        GlowScene scene = GlowScene.create("Movie Night", 700);
        scene.targets.add(initial);
        GlowScene again = GlowScene.fromJson(scene.toJson());
        assertNotNull(again);
        assertEquals(scene.id, again.id);
        assertEquals("Movie Night", again.name);
        assertEquals(1, again.targets.size());
        assertEquals("Rainbow", again.targets.get(0).state.getJSONArray("seg").getJSONObject(0).getString("fxName"));
        GlowScene.Target edited = initial.withBrightness(200).withColor(0xFFAA11);
        assertEquals(200, edited.state.getInt("bri"));
        assertEquals(97, initial.state.getInt("bri"));
<<<<<<< HEAD
        assertEquals("Solid", edited.state.getJSONArray("seg").getJSONObject(0).getString("fxName"));
=======
        assertEquals("Rainbow", edited.state.getJSONArray("seg").getJSONObject(0).getString("fxName"));
        assertEquals(1, edited.state.getJSONArray("seg").getJSONObject(0).getInt("fx"));
>>>>>>> 62916f5714b46463127f168628eceeae27726df4
        assertEquals(255, edited.state.getJSONArray("seg").getJSONObject(0).getJSONArray("col").getJSONArray(0).getInt(0));
    }

    @Test public void remapsEffectAndPaletteNamesAcrossFirmwareCatalogs() throws Exception {
        WledDevice device = new WledDevice("one", "A");
        GlowScene.Target target = GlowScene.Target.capture(device, sample("abc", 2, 1));
        GlowScene scene = GlowScene.create("One", 1500);
        scene.targets.add(target);
        JSONObject current = sample("abc", 0, 0)
                .put("effects", new JSONArray().put("Solid").put("Fire 2012").put("Rainbow"))
                .put("palettes", new JSONArray().put("Party").put("Default").put("Ocean"));
        int[] skipped = {0};
        JSONObject patch = SceneExecutor.payload(scene, target, current, skipped);
        assertEquals(1, patch.getJSONArray("seg").getJSONObject(0).getInt("fx"));
        assertEquals(2, patch.getJSONArray("seg").getJSONObject(0).getInt("pal"));
        assertEquals(15, patch.getInt("tt"));
        assertTrue(patch.getJSONObject("udpn").getBoolean("nn"));
        assertFalse(patch.getJSONArray("seg").getJSONObject(0).has("start"));
        assertEquals(0, skipped[0]);
    }

    @Test public void noCompatibleSegmentFailsSafelyWithoutSending() throws Exception {
        WledDevice device = new WledDevice("one", "A");
        GlowScene.Target target = GlowScene.Target.capture(device, sample("abc", 2, 1));
        GlowScene scene = GlowScene.create("One", 0);
        scene.targets.add(target);
        JSONObject current = sample("abc", 0, 0).put("effects", new JSONArray().put("Solid"));
        try {
            SceneExecutor.payload(scene, target, current, new int[]{0});
            fail("Unsupported effect should not silently apply partial look");
        } catch (org.json.JSONException expected) {
            assertTrue(expected.getMessage().contains("compatible"));
        }
    }

    @Test public void sceneFailureDoesNotPreventOtherControllers() throws Exception {
        WledDevice good = new WledDevice("one", "Good");
        WledDevice bad = new WledDevice("two", "Bad");
        JSONObject lookGood = sample("abcd", 1, 1);
        JSONObject lookBad = sample("cdef", 1, 1);
        good.applySnapshot(lookGood);
        bad.applySnapshot(lookBad);
        GlowScene scene = GlowScene.create("Party", 700);
        scene.targets.add(GlowScene.Target.capture(good, lookGood));
        scene.targets.add(GlowScene.Target.capture(bad, lookBad));
        SceneExecutor.Result result = SceneExecutor.execute(scene, Arrays.asList(good, bad),
                host -> host.equals("one") ? lookGood : lookBad,
                (host, patch) -> { if (host.equals("two")) throw new Exception("offline"); });
        assertEquals(1, result.succeeded);
        assertEquals(1, result.failed);
        assertEquals(0, result.missing);
    }

    @Test public void missingIdentityCannotControlRandomDeviceWithReusedIp() throws Exception {
        WledDevice device = new WledDevice("one", "Wrong device");
        device.applySnapshot(sample("different", 1, 1));
        GlowScene scene = GlowScene.create("Old", 700);
        WledDevice previous = new WledDevice("one", "Previous");
        scene.targets.add(GlowScene.Target.capture(previous, sample("old", 1, 1)));
        SceneExecutor.Result result = SceneExecutor.execute(scene, Collections.singletonList(device),
                host -> sample("different", 1, 1), (host, patch) -> fail("Should not control another device"));
        assertEquals(0, result.succeeded);
        assertEquals(1, result.missing);
    }

    @Test public void deviceIdUsedWhenAvailableAndLongTransitionClamped() throws Exception {
        JSONObject source = sample("abcd", 1, 1);
        source.getJSONObject("info").put("deviceId", "WLED-ABC");
        WledDevice d = new WledDevice("one", "A");
        d.applySnapshot(source);
        GlowScene.Target target = GlowScene.Target.capture(d, source);
        assertEquals("wled:wled-abc", target.deviceId);
        assertSame(d, SceneExecutor.findDevice(target, Collections.singletonList(d)));
        GlowScene scene = GlowScene.create("Fade", 999999);
        scene.targets.add(target);
        assertEquals(600, SceneExecutor.payload(scene, target, source, new int[]{0}).getInt("tt"));
    }
}
