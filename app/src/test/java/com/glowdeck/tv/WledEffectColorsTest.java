package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WledEffectColorsTest {
    @Test public void metadataControlsWhichSlotsAreAvailable() {
        assertArrayEquals(new String[]{"Fx", "Bg", "Cs"}, WledEffectColors.labels(null));
        assertArrayEquals(new String[]{"Fx", "Bg", "Cs"}, WledEffectColors.labels("old metadata"));
        assertArrayEquals(new String[]{"Fx", "Bg", null}, WledEffectColors.labels("!,!;!,!;!;1"));
        assertArrayEquals(new String[]{null, "Bg", null}, WledEffectColors.labels("!,!;,!;!;1"));
        assertArrayEquals(new String[]{null, null, null}, WledEffectColors.labels("!,!;;!;1"));
        assertArrayEquals(new String[]{"Fx", null, "Custom"}, WledEffectColors.labels("!,!;!,,Custom;!;1"));
    }

    @Test public void changingOneColorPreservesAllOtherSlotsAndWhiteChannels() throws Exception {
        JSONArray source = new JSONArray("[[10,20,30,70],[40,50,60,80],[90,100,110,120]]");
        JSONArray patch = WledEffectColors.withSlot(source, 1, 0x11AAEE);
        assertEquals(10, patch.getJSONArray(0).getInt(0));
        assertEquals(70, patch.getJSONArray(0).getInt(3));
        assertEquals(17, patch.getJSONArray(1).getInt(0));
        assertEquals(170, patch.getJSONArray(1).getInt(1));
        assertEquals(238, patch.getJSONArray(1).getInt(2));
        assertEquals(80, patch.getJSONArray(1).getInt(3));
        assertEquals(90, patch.getJSONArray(2).getInt(0));
        assertEquals(120, patch.getJSONArray(2).getInt(3));
        assertEquals(40, source.getJSONArray(1).getInt(0));
    }

    @Test public void independentRapidSlotEditsDoNotOverwritePriorEdit() throws Exception {
        JSONArray first = WledEffectColors.withSlot(new JSONArray("[[1,2,3],[4,5,6],[7,8,9]]"), 0, 0xFF0000);
        JSONArray second = WledEffectColors.withSlot(first, 1, 0x00FF00);
        assertEquals(255, second.getJSONArray(0).getInt(0));
        assertEquals(255, second.getJSONArray(1).getInt(1));
        assertEquals(7, second.getJSONArray(2).getInt(0));
    }

    @Test public void savedSceneColorEditDoesNotForceSolidOrChangePalette() throws Exception {
        JSONObject state = new JSONObject().put("on", true).put("bri", 128)
                .put("seg", new JSONArray().put(new JSONObject().put("id", 0)
                        .put("fx", 24).put("fxName", "Colorful effect")
                        .put("pal", 4).put("palName", "Colors Only")
                        .put("col", new JSONArray("[[5,6,7],[8,9,10],[11,12,13]]"))));
        GlowScene.Target target = new GlowScene.Target("mac:abc", "localhost", "Test", state);
        GlowScene.Target changed = target.withColor(2, 0x445566);
        JSONObject part = changed.state.getJSONArray("seg").getJSONObject(0);
        assertEquals(24, part.getInt("fx"));
        assertEquals("Colorful effect", part.getString("fxName"));
        assertEquals(4, part.getInt("pal"));
        assertEquals(8, part.getJSONArray("col").getJSONArray(1).getInt(0));
        assertEquals(68, part.getJSONArray("col").getJSONArray(2).getInt(0));
        assertEquals(11, state.getJSONArray("seg").getJSONObject(0).getJSONArray("col").getJSONArray(2).getInt(0));
    }
}
