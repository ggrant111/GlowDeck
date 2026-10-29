package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class FavoriteActionTest {
    @Test public void serializationRoundTripAndValidation() throws Exception {
        Favorite favorite = Favorite.create("Bedroom 25%", "@g:bedroom", Favorite.BRIGHTNESS, 64, "", -1);
        Favorite restored = Favorite.fromJson(favorite.toJson());
        assertEquals(favorite.id, restored.id);
        assertEquals("@g:bedroom", restored.target);
        assertEquals(Favorite.BRIGHTNESS, restored.action);
        assertEquals(64, restored.value);
        assertEquals("Bedroom 25%", restored.name);
        assertNull(Favorite.fromJson(new JSONObject().put("id", "x").put("name", "bad")
                .put("target", "*").put("action", Favorite.BRIGHTNESS).put("value", 0)));
        assertNull(Favorite.fromJson(new JSONObject().put("id", "x").put("name", "bad")
                .put("target", "*").put("action", "toggle").put("value", 1)));
    }

    @Test public void powerNeverAccidentallyToggles() throws Exception {
        WledDevice d = new WledDevice("192.168.1.71", "Fireplace");
        Favorite on = Favorite.create("On", d.host, Favorite.POWER, 1, "", -1);
        Favorite off = Favorite.create("Off", d.host, Favorite.POWER, 0, "", -1);
        assertTrue(ActionDispatcher.payload(on, d).getBoolean("on"));
        assertFalse(ActionDispatcher.payload(off, d).getBoolean("on"));
        assertEquals("{\"on\":true}", ActionDispatcher.payload(on, d).toString());
    }

    @Test public void brightnessAndColorAreExplicit() throws Exception {
        WledDevice d = new WledDevice("192.168.1.71", "Fireplace");
        JSONObject brightness = ActionDispatcher.payload(Favorite.create("50%", "*", Favorite.BRIGHTNESS,
                128, "", -1), d);
        assertEquals(128, brightness.getInt("bri"));
        assertTrue(brightness.getBoolean("on"));
        d.segments.add(new WledDevice.Segment(0, "", true));
        d.segments.add(new WledDevice.Segment(3, "", true));
        JSONObject red = ActionDispatcher.payload(Favorite.create("Red", d.host, Favorite.COLOR,
                0xFF0022, "", -1), d);
        JSONArray segments = red.getJSONArray("seg");
        assertEquals(2, segments.length());
        assertEquals(3, segments.getJSONObject(1).getInt("id"));
        JSONArray rgb = segments.getJSONObject(0).getJSONArray("col").getJSONArray(0);
        assertEquals(255, rgb.getInt(0));
        assertEquals(0, rgb.getInt(1));
        assertEquals(34, rgb.getInt(2));
<<<<<<< HEAD
        assertEquals(0, segments.getJSONObject(0).getInt("fx"));
=======
        assertFalse(segments.getJSONObject(0).has("fx"));
        assertEquals(0, segments.getJSONObject(0).getJSONArray("col").getJSONArray(1).getInt(0));
>>>>>>> 62916f5714b46463127f168628eceeae27726df4
    }

    @Test public void presetsMapByNameAcrossDevices() throws Exception {
        WledDevice a = new WledDevice("one", "A");
        WledDevice b = new WledDevice("two", "B");
        a.presets.add(new WledDevice.Preset(4, "Cozy"));
        b.presets.add(new WledDevice.Preset(33, "cozy"));
        Favorite favorite = Favorite.create("Cozy", "*", Favorite.PRESET, 0, "Cozy", 4);
        assertEquals(4, ActionDispatcher.payload(favorite, a).getInt("ps"));
        assertEquals(33, ActionDispatcher.payload(favorite, b).getInt("ps"));
        b.presets.clear();
        assertNull(ActionDispatcher.payload(favorite, b));
    }

    @Test public void partialFailureDoesNotAbortOtherLights() throws Exception {
        WledDevice a = new WledDevice("one", "A");
        WledDevice b = new WledDevice("two", "B");
        WledDevice c = new WledDevice("three", "C");
        Favorite fav = Favorite.create("All Off", "*", Favorite.POWER, 0, "", -1);
        ActionDispatcher.Result result = ActionDispatcher.execute(fav, Arrays.asList(a, b, c),
                (host, patch) -> { if (host.equals("two")) throw new Exception("offline"); });
        assertEquals(2, result.successful);
        assertEquals(0, result.unsupported);
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.get(0).contains("B"));
    }

    @Test public void singleDevicePresetCanStartBeforeCatalogLoads() throws Exception {
        WledDevice d = new WledDevice("192.168.1.71", "Fireplace");
        Favorite f = Favorite.create("Cozy", d.host, Favorite.PRESET, 0, "Cozy", 9);
        assertEquals(9, ActionDispatcher.payload(f, d).getInt("ps"));
        d.loadedPresets = true;
        assertNull(ActionDispatcher.payload(f, d));
    }
}
