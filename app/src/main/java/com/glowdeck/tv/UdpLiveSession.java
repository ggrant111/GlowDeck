package com.glowdeck.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Local-only foreground UDP streaming: 12s identifiers or bounded live effects.
 * UDP is not acknowledged: successful send means only that packets left this Android device.
 * Saves recovery snapshots before starting; restores after WLED's 2s realtime timeout.
 */
final class UdpLiveSession {
    private static final String PREFS = "glowdeck_udp_live_recovery";
    private static final String KEY = "pending";
    private static final int MAX_PREVIEW_LEDS = 4096; // prevent runaway load on Fire tablets
    private final SharedPreferences prefs;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> repeating;
    private UdpRealtimeTransport transport;
    private final Map<String, JSONObject> original = new LinkedHashMap<>();
    private final Map<String, List<SegmentZone.Member>> targets = new LinkedHashMap<>();
    private final Map<String, Integer> realtimePorts = new LinkedHashMap<>();
    private volatile long packetCount = 0;
    private volatile String sendError = "";
    private boolean running = false;
    private UdpRealtimePackets.Mode currentMode;
    private volatile LiveEffectEngine.Settings liveSettings;
    private volatile long startedMillis;
    private volatile int liveSeconds = 0;

    synchronized boolean updateLive(LiveEffectEngine.Settings next) {
        if (!running || liveSettings == null || next == null) return false;
        liveSettings = next; return true;
    }
    synchronized boolean isLiveEffect() { return running && liveSettings != null; }
    int secondsRemaining() {
        if (!active()) return 0;
        return Math.max(0, liveSeconds - (int)((System.currentTimeMillis()-startedMillis)/1000));
    }
    synchronized String startLive(List<SegmentZone.Member> members, List<WledDevice> devices,
                                  LiveEffectEngine.Settings settings) {
        if (settings == null) return "Choose a live effect";
        return startInternal(members,devices,null,settings);
    }

    UdpLiveSession(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized boolean active() { return running; }
    long sentPackets() { return packetCount; }
    String lastError() { return sendError; }

    synchronized String start(List<SegmentZone.Member> members, List<WledDevice> devices,
                              UdpRealtimePackets.Mode mode) {
        return startInternal(members,devices,mode,null);
    }

    private synchronized String startInternal(List<SegmentZone.Member> members, List<WledDevice> devices,
                                  UdpRealtimePackets.Mode mode, LiveEffectEngine.Settings settings) {
        if (running) return "Stop the current UDP stream first";
        if (!prefs.getString(KEY, "").isEmpty()) {
            String result = recover();
            if (!"No UDP preview to restore".equals(result) && !"Original lighting restored".equals(result))
                return result;
        }
        if (members == null || members.isEmpty()) return "Select a segment first";
        original.clear(); targets.clear(); realtimePorts.clear(); packetCount = 0; sendError = "";
        currentMode = mode;
        liveSettings = settings;
        liveSeconds = settings == null ? 12 : 90; // auto-stop safely; user can restart intentionally
        try {
            for (SegmentZone.Member member : members) {
                WledDevice device = SegmentZoneController.findDevice(member, devices);
                if (device == null) throw new IllegalArgumentException("Missing WLED device: " + member.segmentName);
                targets.computeIfAbsent(device.host, k -> new ArrayList<>()).add(member);
            }
            if (mode == UdpRealtimePackets.Mode.DDP_RAINBOW_TEST && targets.size() != 1)
                throw new IllegalArgumentException("DDP test controls one whole WLED strip at a time");
            for (Map.Entry<String, List<SegmentZone.Member>> entry : targets.entrySet()) {
                JSONObject snapshot = WledApi.snapshot(entry.getKey());
                JSONObject info = snapshot.optJSONObject("info");
                if (info == null) throw new IllegalArgumentException("No WLED controller info");
                if (info.optBoolean("live", false))
                    throw new IllegalArgumentException(entry.getKey() + " already has a realtime sender. Stop it first.");
                int total = WledSegmentPlanner.ledCount(snapshot);
                if (total < 1 || total > MAX_PREVIEW_LEDS)
                    throw new IllegalArgumentException("UDP preview supports 1–" + MAX_PREVIEW_LEDS + " LEDs per device");
                for (SegmentZone.Member member : entry.getValue()) {
                    if (!member.deviceId.startsWith("host:") &&
                            !member.deviceId.equals(GlowScene.identity(info, entry.getKey())))
                        throw new IllegalArgumentException("Controller identity changed: " + member.segmentName);
                    if (!WledSegmentPlanner.matches(member, snapshot) &&
                            !(member.segmentName.startsWith("LEDs ") &&
                                    rangeInsidePhysicalSegment(member, snapshot)))
                        throw new IllegalArgumentException("LED boundaries changed: " + member.segmentName);
                    if (member.start < 0 || member.stop > total || member.stop <= member.start)
                        throw new IllegalArgumentException("Invalid LED range: " + member.segmentName);
                }
                int configuredPort=info.optInt("udpport",UdpRealtimePackets.WLED_REALTIME_PORT);
                realtimePorts.put(entry.getKey(), configuredPort>=1 && configuredPort<=65535 ?
                        configuredPort : UdpRealtimePackets.WLED_REALTIME_PORT);
                original.put(entry.getKey(), snapshot);
            }
            if (!storeRecovery()) throw new IllegalStateException("Cannot save recovery backup");
            transport = new UdpRealtimeTransport();
            running = true;
            startedMillis = System.currentTimeMillis();
            repeating = timer.scheduleAtFixedRate(new Runnable() {
                long frame = 0;
                @Override public void run() {
                    if (!active() || secondsRemaining() <= 0) return;
                    try { sendFrame(frame++); }
                    catch (Exception e) {
                        sendError = e.getMessage() == null ? "UDP send failed" : e.getMessage();
                        // UDP has no acknowledgment; UI timeout/Stop handles restoration.

                    }
                }
            }, 0, settings != null ? 67 : mode == UdpRealtimePackets.Mode.DDP_RAINBOW_TEST ? 100 : 125,
                    TimeUnit.MILLISECONDS); // Live 15fps; DDP 10fps; locator 8fps.
            return settings != null ?
                    "Live " + settings.effect + " streaming to " + targets.size() +
                            " controller(s) · UDP 21324 · max 90 seconds" :
                    mode == UdpRealtimePackets.Mode.DDP_RAINBOW_TEST ?
                    "DDP test sending to entire strip · UDP 4048 · stops after 12 seconds" :
                    "UDP segment identifier streaming · UDP 21324 · stops after 12 seconds";
        } catch (Exception e) {
            if (transport != null) { transport.close(); transport = null; }
            original.clear(); targets.clear(); realtimePorts.clear(); running = false;liveSettings=null;
            // If startup failed before sending any realtime packets, the saved state is unchanged.
            if (packetCount == 0) prefs.edit().remove(KEY).commit();
            return "Cannot start UDP: " + e.getMessage();
        }
    }

    private static boolean rangeInsidePhysicalSegment(SegmentZone.Member member, JSONObject snapshot) {
        WledSegmentPlanner.Part part = WledSegmentPlanner.find(snapshot, member.segmentId);
        return part != null && part.start <= member.start && part.stop >= member.stop;
    }

    private void sendFrame(long frame) throws Exception {
        UdpRealtimeTransport sender = transport;
        if (sender == null) return;
        for (Map.Entry<String, JSONObject> entry : original.entrySet()) {
            String host = entry.getKey();
            LiveEffectEngine.Settings settings = liveSettings;
            if (settings != null) {
                for (SegmentZone.Member member : targets.get(host)) {
                    int[] pixels = LiveEffectEngine.render(member.stop-member.start,
                            Math.max(0,System.currentTimeMillis()-startedMillis),settings);
                    List<byte[]> packets = UdpRealtimePackets.dnrgb(member.start,pixels,2);
                    sender.send(host,realtimePorts.getOrDefault(host,UdpRealtimePackets.WLED_REALTIME_PORT),packets);
                    packetCount += packets.size();
                }
            } else if (currentMode == UdpRealtimePackets.Mode.DDP_RAINBOW_TEST) {
                int[] pixels = UdpRealtimePackets.ddpRainbow(WledSegmentPlanner.ledCount(entry.getValue()), frame);
                List<byte[]> packets = UdpRealtimePackets.ddp(pixels, 1 + (int) (frame % 15));
                sender.send(host, UdpRealtimePackets.DDP_PORT, packets);
                packetCount += packets.size();
            } else {
                for (SegmentZone.Member member : targets.get(host)) {
                    int[] pixels = UdpRealtimePackets.identifyColors(member.stop - member.start, currentMode, frame);
                    List<byte[]> packets = UdpRealtimePackets.dnrgb(member.start, pixels, 2);
                    sender.send(host, realtimePorts.getOrDefault(host,UdpRealtimePackets.WLED_REALTIME_PORT), packets);
                    packetCount += packets.size();
                }
            }
        }
    }

    synchronized String stop() {
        boolean wasActive = running;
        running = false;
        liveSettings = null;
        if (repeating != null) { repeating.cancel(false); repeating = null; }
        if (transport != null) { transport.close(); transport = null; }
        if (!wasActive && original.isEmpty()) return recover();
        // DNRGB uses a 2s receiver timeout. DDP uses WLED's configured realtime timeout.
        // Restore checks live mode separately, with a bounded retry window.
        if (wasActive) try { Thread.sleep(2300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String problem = restore(original);
        original.clear(); targets.clear(); realtimePorts.clear();
        if (problem.isEmpty()) {
            prefs.edit().remove(KEY).commit();
            return "Original lighting restored";
        }
        return "Restore pending for " + problem + " · retry after reconnecting";
    }

    synchronized String recover() {
        if (running) return "UDP preview still active";
        String text = prefs.getString(KEY, "");
        if (text.isEmpty()) return "No UDP preview to restore";
        try {
            JSONObject root = new JSONObject(text);
            JSONArray list = root.optJSONArray("items");
            Map<String, JSONObject> pending = new LinkedHashMap<>();
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null && item.optJSONObject("snapshot") != null)
                    pending.put(item.optString("host"), item.getJSONObject("snapshot"));
            }
            if (pending.isEmpty()) return "Recovery backup is empty";
            String problem = restore(pending);
            if (problem.isEmpty()) {
                prefs.edit().remove(KEY).commit();
                return "Original lighting restored";
            }
            return "UDP restore pending for " + problem;
        } catch (Exception e) { return "Could not recover UDP preview: " + e.getMessage(); }
    }

    private String restore(Map<String, JSONObject> snapshots) {
        StringBuilder failed = new StringBuilder();
        for (Map.Entry<String, JSONObject> entry : snapshots.entrySet()) {
            try {
                JSONObject current = WledApi.snapshot(entry.getKey());
                if (!GlowScene.identity(entry.getValue().optJSONObject("info"), entry.getKey()).equals(
                        GlowScene.identity(current.optJSONObject("info"), entry.getKey())))
                    throw new IllegalStateException("Identity changed");
                // Never overwrite an active realtime stream, including another sender that
                // started after ours. Give WLED a few seconds to exit our timed stream.
                for (int attempt = 0; attempt < 8 && current.optJSONObject("info") != null &&
                        current.getJSONObject("info").optBoolean("live", false); attempt++) {
                    Thread.sleep(650);
                    current = WledApi.snapshot(entry.getKey());
                }
                if (current.optJSONObject("info") != null && current.getJSONObject("info").optBoolean("live", false))
                    throw new IllegalStateException("Controller still receiving realtime data");
                WledCommandVerifier.sendAndVerify(entry.getKey(), WledSegmentPlanner.restore(entry.getValue()),
                        WledApi::send, WledApi::snapshot, WledApi::presets, null);
            } catch (Exception ex) {
                if (failed.length() > 0) failed.append(", ");
                failed.append(entry.getKey()).append(" (").append(ex.getMessage()).append(")");
            }
        }
        return failed.toString();
    }

    private boolean storeRecovery() throws Exception {
        JSONArray list = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : original.entrySet())
            list.put(new JSONObject().put("host", entry.getKey()).put("snapshot", entry.getValue()));
        return prefs.edit().putString(KEY, new JSONObject().put("items", list).toString()).commit();
    }

    void shutdown() { timer.shutdownNow(); }
}
