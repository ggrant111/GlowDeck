package com.glowdeck.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A shared, versioned local store. Preserves legacy v1.4 Activity preferences on upgrade. */
final class GlowDeckRepository {
    static final String SHARED_FILE = "glowdeck_shared_v15";
    private final SharedPreferences shared;
    private final SharedPreferences legacy;

    GlowDeckRepository(Context context, SharedPreferences legacy) {
        this.shared = context.getApplicationContext().getSharedPreferences(SHARED_FILE, Context.MODE_PRIVATE);
        this.legacy = legacy;
        migrateLegacy();
    }

    private void migrateLegacy() {
        SharedPreferences.Editor editor = shared.edit();
        boolean changes = false;
        for (String key : new String[]{"devices", "custom_groups_v12"}) {
            if (!shared.contains(key) && legacy.contains(key)) {
                editor.putString(key, legacy.getString(key, "[]"));
                changes = true;
            }
        }
        if (changes) editor.apply();
    }

    String readDevices() { return shared.getString("devices", null); }
    String readGroups() { return shared.getString("custom_groups_v12", "[]"); }

    void saveDevices(String json) {
        shared.edit().putString("devices", json).apply();
        legacy.edit().putString("devices", json).apply(); // downgrade-friendly during beta
    }

    void saveGroups(String json) {
        shared.edit().putString("custom_groups_v12", json).apply();
        legacy.edit().putString("custom_groups_v12", json).apply();
    }

    List<GlowScene> loadScenes() {
        List<GlowScene> scenes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            JSONObject stored = new JSONObject(shared.getString("scenes_v16", "{}"));
            if (stored.optInt("schema", 1) != 1) return scenes;
            JSONArray items = stored.optJSONArray("items");
            if (items == null) return scenes;
            for (int i = 0; i < items.length() && scenes.size() < 50; i++) {
                GlowScene scene = GlowScene.fromJson(items.optJSONObject(i));
                if (scene != null && ids.add(scene.id)) scenes.add(scene);
            }
        } catch (JSONException ignored) { }
        return scenes;
    }

    void saveScenes(List<GlowScene> scenes) {
        JSONArray list = new JSONArray();
        for (GlowScene scene : scenes)
            try { list.put(scene.toJson()); } catch (JSONException ignored) { }
        JSONObject output = new JSONObject();
        try { output.put("schema", 1).put("items", list); } catch (JSONException ignored) { }
        shared.edit().putString("scenes_v16", output.toString()).apply();
    }

    List<Favorite> loadFavorites() {
        List<Favorite> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            JSONObject store = new JSONObject(shared.getString("favorites_v15", "{}"));
            if (store.optInt("schema", 1) != 1) return items;
            JSONArray list = store.optJSONArray("items");
            if (list == null) return items;
            for (int i = 0; i < list.length() && items.size() < 50; i++) {
                Favorite item = Favorite.fromJson(list.optJSONObject(i));
                if (item != null && ids.add(item.id)) items.add(item);
            }
        } catch (JSONException ignored) { /* Invalid data must not crash lighting control. */ }
        return items;
    }

    void saveFavorites(List<Favorite> favorites) {
        JSONArray list = new JSONArray();
        for (Favorite f : favorites) {
            try { list.put(f.toJson()); } catch (JSONException ignored) { }
        }
        JSONObject out = new JSONObject();
        try { out.put("schema", 1).put("items", list); } catch (JSONException ignored) { }
        shared.edit().putString("favorites_v15", out.toString()).apply();
    }
}
