package com.glowdeck.tv;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class WledApi {
    private WledApi() {}

    static JSONObject snapshot(String host) throws Exception {
        JSONObject j = new JSONObject(get(host, "/json"));
        if (j.optJSONObject("state") == null || j.optJSONObject("info") == null) {
            throw new Exception("No WLED JSON state/info returned");
        }
        return j;
    }

    static JSONObject presets(String host) throws Exception {
        return new JSONObject(get(host, "/presets.json"));
    }

    static JSONObject nodes(String host) throws Exception {
        return new JSONObject(get(host, "/json/nodes"));
    }

    static JSONObject send(String host, JSONObject patch) throws Exception {
        String raw = request(host, "/json/state", "POST", patch.toString());
        if (raw.isEmpty()) return new JSONObject();
        return new JSONObject(raw);
    }

    private static String get(String host, String path) throws Exception {
        return request(host, path, "GET", null);
    }

    private static String request(String host, String path, String method, String body) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + host + path).openConnection();
            c.setConnectTimeout(2600);
            c.setReadTimeout(2800);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            c.setUseCaches(false);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            }
            int code = c.getResponseCode();
            InputStream input = code < 400 ? c.getInputStream() : c.getErrorStream();
            StringBuilder result = new StringBuilder();
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) result.append(line);
                }
            }
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " " + result);
            return result.toString();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static JSONObject object(String key, Object value) {
        JSONObject j = new JSONObject();
        try { j.put(key, value); } catch (JSONException ignored) {}
        return j;
    }
}
