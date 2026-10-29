package com.glowdeck.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Temporary physical LED identification with best-effort recovery after app death. */
final class SegmentPreviewManager {
    private static final String STORE="glowdeck_segment_preview_recovery";
    private static final String KEY="pending";
    private final SharedPreferences prefs;
    private final Map<String,JSONObject> original=new LinkedHashMap<>();
    private final Map<String,List<SegmentZone.Member>> active=new LinkedHashMap<>();
    SegmentPreviewManager(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences(STORE,Context.MODE_PRIVATE);
    }
    synchronized boolean active(){return !original.isEmpty();}
    /** Caller serializes this with other WLED commands. Never launch concurrently. */
    synchronized String begin(List<SegmentZone.Member> members,List<WledDevice> devices) {
        stop();
        Map<String,List<SegmentZone.Member>> byHost=new LinkedHashMap<>();
        for(SegmentZone.Member m:members) {
            WledDevice d=SegmentZoneController.findDevice(m,devices);
            if(d==null)return "Missing controller for "+m.segmentName;
            byHost.computeIfAbsent(d.host,k->new ArrayList<>()).add(m);
        }
        try {
            // Capture and persist BEFORE any temporary changes.
            for(Map.Entry<String,List<SegmentZone.Member>> entry:byHost.entrySet()) {
                JSONObject snapshot=WledApi.snapshot(entry.getKey());
                if(snapshot.optJSONObject("info") == null)throw new Exception("Invalid controller response");
                for(SegmentZone.Member m:entry.getValue())
                    if(!m.deviceId.startsWith("host:") && !m.deviceId.equals(
                            GlowScene.identity(snapshot.optJSONObject("info"),entry.getKey())))
                        throw new Exception("Controller identity changed: "+m.segmentName);
                original.put(entry.getKey(),snapshot);
            }
            writeRecovery();
            active.putAll(byHost);
            int count=0;
            for(Map.Entry<String,List<SegmentZone.Member>> entry:byHost.entrySet()) {
                WledApi.send(entry.getKey(),WledSegmentPlanner.preview(original.get(entry.getKey()),entry.getValue()));
                count++;
            }
            return count+" controller(s) identifying selected LEDs";
        } catch(Exception ex) {
            stop();
            return "Identification unavailable: "+ex.getMessage();
        }
    }
    synchronized String stop() {
        if(original.isEmpty())return recover();
        String problems=restoreAll(original);
        if(problems.isEmpty()) { original.clear(); active.clear(); prefs.edit().remove(KEY).commit();return "Original lighting restored"; }
        // Keep backup until next recovery; do not throw it away on partial network failure.
        original.clear(); active.clear();
        return "Some lights could not be restored: "+problems+". Retry when online.";
    }
    synchronized String recover() {
        String text=prefs.getString(KEY,"");
        if(text.isEmpty())return "No active identification";
        try {
            JSONObject j=new JSONObject(text);
            Map<String,JSONObject> pending=new LinkedHashMap<>();
            JSONArray entries=j.optJSONArray("items");
            if(entries!=null)for(int i=0;i<entries.length();i++) {
                JSONObject entry=entries.optJSONObject(i);
                if(entry!=null && entry.optJSONObject("snapshot")!=null)
                    pending.put(entry.optString("host"),entry.optJSONObject("snapshot"));
            }
            String problems=restoreAll(pending);
            if(problems.isEmpty()) {prefs.edit().remove(KEY).commit();return "Original lighting restored";}
            return "Restore pending: "+problems;
        }catch(Exception ex){return "Preview recovery unavailable: "+ex.getMessage();}
    }
    private String restoreAll(Map<String,JSONObject> prior) {
        StringBuilder errors=new StringBuilder();
        for(Map.Entry<String,JSONObject> e:prior.entrySet()) {
            try {
                JSONObject now=WledApi.snapshot(e.getKey());
                if(!GlowScene.identity(e.getValue().optJSONObject("info"),e.getKey()).equals(
                        GlowScene.identity(now.optJSONObject("info"),e.getKey())))
                    throw new Exception("device identity changed");
                WledCommandVerifier.sendAndVerify(e.getKey(),WledSegmentPlanner.restore(e.getValue()),
                        WledApi::send,WledApi::snapshot,WledApi::presets,null);
            }catch(Exception ex){if(errors.length()>0)errors.append("; ");errors.append(e.getKey()).append(" (").append(ex.getMessage()).append(")");}
        }
        return errors.toString();
    }
    private void writeRecovery()throws Exception {
        JSONArray arr=new JSONArray();
        for(Map.Entry<String,JSONObject> e:original.entrySet())
            arr.put(new JSONObject().put("host",e.getKey()).put("snapshot",e.getValue()));
        if(!prefs.edit().putString(KEY,new JSONObject().put("items",arr).toString()).commit())
            throw new Exception("Could not save preview recovery data");
    }
}
