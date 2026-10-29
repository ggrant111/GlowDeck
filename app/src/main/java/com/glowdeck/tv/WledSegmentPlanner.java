package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure, testable WLED segment planning and narrow, segment-ID-targeted payloads. */
final class WledSegmentPlanner {
    static final class Part {
        final int id, start, stop;
        final JSONObject data;
        Part(int id, int start, int stop, JSONObject data) {
            this.id=id; this.start=start; this.stop=stop; this.data=data;
        }
    }
    static List<Part> parts(JSONObject snapshot) {
        List<Part> out = new ArrayList<>();
        JSONObject state = snapshot.optJSONObject("state");
        JSONArray segments = state == null ? null : state.optJSONArray("seg");
        if (segments == null) return out;
        for (int i=0; i<segments.length(); i++) {
            JSONObject seg=segments.optJSONObject(i);
            if (seg==null) continue;
            int start=seg.optInt("start",0), stop=seg.optInt("stop",0);
            if (stop<=start) continue;
            out.add(new Part(seg.optInt("id",i),start,stop,seg));
        }
        return out;
    }
    static int maxSegments(JSONObject snapshot) {
        JSONObject info=snapshot.optJSONObject("info");
        JSONObject leds=info==null?null:info.optJSONObject("leds");
        return Math.max(1,leds==null?1:leds.optInt("maxseg",1));
    }
    static int ledCount(JSONObject snapshot) {
        JSONObject info=snapshot.optJSONObject("info");
        JSONObject leds=info==null?null:info.optJSONObject("leds");
        return leds==null?0:leds.optInt("count",0);
    }
    static Part find(JSONObject snapshot,int id) {
        for(Part p:parts(snapshot)) if(p.id==id) return p;
        return null;
    }
    static void validateRange(JSONObject snapshot, int start, int stop) {
        int count=ledCount(snapshot);
        if(start<0 || stop<=start || stop>count) throw new IllegalArgumentException(
                "LED range must be within 1–"+count+" and cannot be empty");
    }
    static JSONObject patch(JSONArray segs) throws JSONException {
        return new JSONObject().put("seg",segs).put("udpn",new JSONObject().put("nn",true));
    }
    static JSONObject copySettings(JSONObject seg,int id,int start,int stop,String name) throws JSONException {
        JSONObject out = new JSONObject().put("id",id).put("start",start).put("stop",stop)
                .put("n",name).put("sel",false);
        for(String k:new String[]{"on","bri","fx","pal","sx","ix","col","rev","mi","grp","spc","of","c1","c2","c3","o1","o2","o3"})
            if(seg.has(k)) out.put(k,seg.get(k));
        return out;
    }
    /** Split exactly one parent segment, preserving untouched left and right slices.
     * Reject overlaps with more than one existing part, and respect maxseg. */
    static JSONObject splitToCreate(JSONObject snapshot,int start,int stop,String name) throws JSONException {
        validateRange(snapshot,start,stop);
        List<Part> existing=parts(snapshot);
        Part parent=null;
        for(Part part:existing) {
            if(start<part.stop && stop>part.start) {
                if(parent!=null || start<part.start || stop>part.stop)
                    throw new IllegalArgumentException("Range overlaps multiple segments. Resize existing segments first.");
                parent=part;
            }
        }
        if(parent==null) throw new IllegalArgumentException("Range is not in an existing segment. Create a parent range in WLED first.");
        if(start==parent.start && stop==parent.stop)
            throw new IllegalArgumentException("That entire range is already a segment.");
        int needed=(start>parent.start?1:0)+(stop<parent.stop?1:0);
        Set<Integer> used=new HashSet<>();
        for(Part p:existing) used.add(p.id);
        List<Integer> free=new ArrayList<>();
        for(int i=0;i<maxSegments(snapshot);i++) if(!used.contains(i))free.add(i);
        if(free.size()<needed)throw new IllegalArgumentException("Not enough free segment slots (need "+needed+").");
        JSONArray edits=new JSONArray();
        int cursor=0;
        if(start>parent.start) {
            edits.put(copySettings(parent.data,parent.id,parent.start,start,parent.data.optString("n","Left")));
            edits.put(copySettings(parent.data,free.get(cursor++),start,stop,name));
        } else edits.put(copySettings(parent.data,parent.id,start,stop,name));
        if(stop<parent.stop) edits.put(copySettings(parent.data,free.get(cursor),stop,parent.stop,
                parent.data.optString("n","Right")));
        return patch(edits);
    }
    static JSONObject rename(JSONObject snapshot,int segmentId,String name) throws JSONException {
        if(find(snapshot,segmentId)==null)throw new IllegalArgumentException("Segment no longer exists");
        return patch(new JSONArray().put(new JSONObject().put("id",segmentId).put("n",name)));
    }
    static JSONObject resize(JSONObject snapshot,int segmentId,int start,int stop) throws JSONException {
        validateRange(snapshot,start,stop);
        Part own=find(snapshot,segmentId);
        if(own==null)throw new IllegalArgumentException("Segment no longer exists");
        for(Part p:parts(snapshot)) if(p.id!=segmentId && start<p.stop && stop>p.start)
            throw new IllegalArgumentException("Range overlaps "+p.data.optString("n","segment "+p.id));
        return patch(new JSONArray().put(new JSONObject().put("id",segmentId).put("start",start).put("stop",stop)));
    }
    static JSONObject delete(JSONObject snapshot,int segmentId) throws JSONException {
        if(find(snapshot,segmentId)==null)throw new IllegalArgumentException("Segment no longer exists");
        if(parts(snapshot).size()<=1)throw new IllegalArgumentException("Cannot remove the last segment");
        return patch(new JSONArray().put(new JSONObject().put("id",segmentId).put("stop",0)));
    }
    static JSONObject change(JSONObject snapshot,int segmentId,JSONObject settings) throws JSONException {
        if(find(snapshot,segmentId)==null)throw new IllegalArgumentException("Segment no longer exists");
        JSONObject s=new JSONObject(settings.toString()).put("id",segmentId);
        return patch(new JSONArray().put(s));
    }
    static JSONObject preview(JSONObject snapshot,List<SegmentZone.Member> members) throws JSONException {
        JSONArray edits=new JSONArray();
        for(Part part:parts(snapshot)) {
            JSONArray indices=new JSONArray();
            for(SegmentZone.Member m:members) {
                int begin=Math.max(m.start,part.start),end=Math.min(m.stop,part.stop);
                if(begin<end) indices.put(begin-part.start).put(end-part.start).put("FFD044");
            }
            if(indices.length()>0) edits.put(new JSONObject().put("id",part.id).put("on",true).put("i",indices));
        }
        if(edits.length()==0) throw new IllegalArgumentException("Selected LEDs are not covered by an active WLED segment.");
        return new JSONObject().put("on",true).put("bri",160).put("seg",edits)
                .put("udpn",new JSONObject().put("nn",true));
    }
    /** Reapply only prior lighting properties, not network/controller settings or WLED preset files. */
    static JSONObject restore(JSONObject snapshot) throws JSONException {
        JSONObject state=snapshot.optJSONObject("state");
        if(state==null)throw new IllegalArgumentException("Missing saved lighting state");
        JSONArray edits=new JSONArray();
        for(Part p:parts(snapshot)) {
            JSONObject part=new JSONObject().put("id",p.id).put("frz",false);
            for(String k:new String[]{"on","bri","fx","pal","sx","ix","col","rev","mi","grp","spc","of","sel"})
                if(p.data.has(k)) part.put(k,p.data.get(k));
            edits.put(part);
        }
        return new JSONObject().put("on",state.optBoolean("on",true))
                .put("bri",state.optInt("bri",128)).put("seg",edits)
                .put("udpn",new JSONObject().put("nn",true));
    }
    static boolean matches(SegmentZone.Member m,JSONObject snapshot) {
        String identity=GlowScene.identity(snapshot.optJSONObject("info"),m.lastHost);
        if(!m.deviceId.startsWith("host:") && !m.deviceId.equals(identity))return false;
        Part p=find(snapshot,m.segmentId);
        return p!=null && p.start==m.start && p.stop==m.stop;
    }
}
