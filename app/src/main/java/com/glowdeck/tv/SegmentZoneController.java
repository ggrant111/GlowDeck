package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independently targets validated segment IDs across any number of WLED controllers. */
final class SegmentZoneController {
    interface Reader { JSONObject read(String host) throws Exception; }
    interface Sender { void send(String host, JSONObject patch) throws Exception; }
    static final class Result {
        int confirmed, failed, unconfirmed, skipped;
        final List<String> problems=new ArrayList<>();
        String summary(){return confirmed+" confirmed, "+failed+" failed, "+unconfirmed+
                " unconfirmed, "+skipped+" missing/changed";}
    }
    static Result execute(SegmentZone zone,List<WledDevice> devices,JSONObject settings,
                          String effectName,int slot,Integer color,Reader reader,Sender sender) {
        Result result=new Result();
        Map<String,List<SegmentZone.Member>> grouped=new LinkedHashMap<>();
        for(SegmentZone.Member m:zone.members) {
            WledDevice device=findDevice(m,devices);
            if(device==null) { result.skipped++;result.problems.add(m.segmentName+": controller not found");continue; }
            grouped.computeIfAbsent(device.host,k->new ArrayList<>()).add(m);
        }
        for(Map.Entry<String,List<SegmentZone.Member>> item:grouped.entrySet()) {
            String host=item.getKey();
            try {
                JSONObject snapshot=reader.read(host);
                JSONArray patches=new JSONArray();
                for(SegmentZone.Member m:item.getValue()) {
                    if(!WledSegmentPlanner.matches(m,snapshot)) {
                        result.skipped++;result.problems.add(m.segmentName+": identity/range changed; reselect it");continue;
                    }
                    JSONObject patch=new JSONObject(settings.toString()).put("id",m.segmentId);
                    if(effectName!=null) {
                        int fx=findName(snapshot.optJSONArray("effects"),effectName);
                        if(fx<0) { result.skipped++;result.problems.add(m.segmentName+": effect not supported");continue; }
                        patch.put("fx",fx);
                    }
                    if(color!=null) {
                        WledSegmentPlanner.Part part=WledSegmentPlanner.find(snapshot,m.segmentId);
                        JSONArray cols=WledEffectColors.withSlot(part.data.optJSONArray("col"),slot,color);
                        patch.put("col",cols); // Preserve effect and other color channels
                    }
                    patches.put(patch);
                }
                if(patches.length()==0)continue;
                JSONObject body=new JSONObject().put("seg",patches).put("udpn",new JSONObject().put("nn",true));
                if(settings.optBoolean("on",false) || color!=null || effectName!=null)
                    body.put("on",true);
                sender.send(host,body);
                result.confirmed+=patches.length();
            } catch(WledCommandVerifier.UnconfirmedException ex) {
                result.unconfirmed+=item.getValue().size();
                result.problems.add(host+": requested but not confirmed");
            } catch(Exception ex) {
                result.failed+=item.getValue().size();
                result.problems.add(host+": "+(ex.getMessage()==null?"connection failed":ex.getMessage()));
            }
        }
        return result;
    }
    static int findName(JSONArray a,String name) {
        if(a==null)return -1;
        for(int i=0;i<a.length();i++)if(name.equalsIgnoreCase(a.optString(i)))return i;
        return -1;
    }
    static WledDevice findDevice(SegmentZone.Member m,List<WledDevice> devices) {
        for(WledDevice d:devices)if(m.deviceId.equals(d.identity()))return d;
        if(m.deviceId.startsWith("host:"))
            for(WledDevice d:devices)if(m.lastHost.equalsIgnoreCase(d.host))return d;
        return null;
    }
}
