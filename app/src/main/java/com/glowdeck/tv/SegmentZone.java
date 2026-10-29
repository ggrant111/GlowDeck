package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A GlowDeck zone groups physical WLED segments across separate controllers. */
final class SegmentZone {
    final String id;
    String name;
    final List<Member> members = new ArrayList<>();
    SegmentZone(String id, String name) { this.id = id; this.name = name; }
    static SegmentZone create(String name) { return new SegmentZone(UUID.randomUUID().toString(), name); }
    JSONObject toJson() throws JSONException {
        JSONArray items = new JSONArray();
        for (Member m : members) items.put(m.toJson());
        return new JSONObject().put("id", id).put("name", name).put("members", items);
    }
    static SegmentZone fromJson(JSONObject j) {
        if (j == null || j.optString("id").isEmpty() || j.optString("name").trim().isEmpty()) return null;
        SegmentZone z = new SegmentZone(j.optString("id"), j.optString("name"));
        JSONArray arr = j.optJSONArray("members");
        if (arr != null) for (int i = 0; i < arr.length() && z.members.size() < 64; i++) {
            Member m = Member.fromJson(arr.optJSONObject(i));
            if (m != null) z.members.add(m);
        }
        return z.members.isEmpty() ? null : z;
    }
    static final class Member {
        final String deviceId, lastHost, segmentName;
        final int segmentId, start, stop; // WLED zero-based, stop-exclusive
        Member(String deviceId, String lastHost, int segmentId, int start, int stop, String name) {
            this.deviceId = deviceId; this.lastHost = lastHost;
            this.segmentId = segmentId; this.start = start; this.stop = stop;
            this.segmentName = name;
        }
        static Member of(WledDevice d, WledDevice.Segment s) {
            return new Member(d.identity(), d.host, s.id, s.start, s.stop, s.label());
        }
        JSONObject toJson() throws JSONException {
            return new JSONObject().put("deviceId", deviceId).put("host", lastHost)
                    .put("segmentId", segmentId).put("start", start).put("stop", stop)
                    .put("segmentName", segmentName);
        }
        static Member fromJson(JSONObject j) {
            if (j == null) return null;
            int start = j.optInt("start", -1), stop = j.optInt("stop", -1);
            int id = j.optInt("segmentId", -1);
            if (id < 0 || start < 0 || stop <= start || j.optString("deviceId").isEmpty()) return null;
            return new Member(j.optString("deviceId"), j.optString("host"), id, start, stop,
                    j.optString("segmentName", "Segment " + (id + 1)));
        }
    }
}
