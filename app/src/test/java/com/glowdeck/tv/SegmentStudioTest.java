package com.glowdeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class SegmentStudioTest {
    private JSONObject snapshot(int total,int maxseg) throws Exception {
        return new JSONObject().put("info",new JSONObject().put("mac","112233445566")
                        .put("leds",new JSONObject().put("count",total).put("maxseg",maxseg)))
                .put("state",new JSONObject().put("on",true).put("bri",123)
                        .put("seg",new JSONArray().put(new JSONObject().put("id",0)
                                .put("n","Original").put("start",0).put("stop",total)
                                .put("fx",12).put("pal",3).put("col",new JSONArray()
                                    .put(new JSONArray().put(10).put(20).put(30))))));
    }
    @Test public void rangeSplitsWithoutLosingOutsideLEDs() throws Exception {
        JSONObject snap=snapshot(400,16);
        JSONObject cmd=WledSegmentPlanner.splitToCreate(snap,100,200,"Center");
        JSONArray seg=cmd.getJSONArray("seg");
        assertEquals(3,seg.length());
        assertEquals(0,seg.getJSONObject(0).getInt("start"));
        assertEquals(100,seg.getJSONObject(0).getInt("stop"));
        assertEquals(100,seg.getJSONObject(1).getInt("start"));
        assertEquals(200,seg.getJSONObject(1).getInt("stop"));
        assertEquals(200,seg.getJSONObject(2).getInt("start"));
        assertEquals(400,seg.getJSONObject(2).getInt("stop"));
        for(int i=0;i<seg.length();i++)assertEquals(12,seg.getJSONObject(i).getInt("fx"));
        assertTrue(cmd.getJSONObject("udpn").getBoolean("nn"));
    }
    @Test public void rangeBoundariesAndCapacityAreValidated() throws Exception {
        for(int[] range:new int[][]{{-1,10},{100,100},{0,401}}){
            try{WledSegmentPlanner.splitToCreate(snapshot(400,16),range[0],range[1],"bad");fail();}
            catch(IllegalArgumentException expected){}
        }
        try{WledSegmentPlanner.splitToCreate(snapshot(400,1),100,200,"no slots");fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void overlappingResizeIsRejected() throws Exception {
        JSONObject snap=snapshot(400,16);
        JSONArray segments=snap.getJSONObject("state").getJSONArray("seg");
        segments.getJSONObject(0).put("stop",200);
        segments.put(new JSONObject().put("id",1).put("start",200).put("stop",400));
        try{WledSegmentPlanner.resize(snap,0,100,250);fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void previewMapsPhysicalLEDsToSegmentRelativeIndices() throws Exception {
        JSONObject a=snapshot(400,16);
        SegmentZone.Member m=new SegmentZone.Member("mac:112233445566","192.168.1.10",0,100,200,"Center");
        JSONArray command=WledSegmentPlanner.preview(a,Arrays.asList(m)).getJSONArray("seg");
        assertEquals(100,command.getJSONObject(0).getJSONArray("i").getInt(0));
        assertEquals(200,command.getJSONObject(0).getJSONArray("i").getInt(1));
        assertEquals("FFD044",command.getJSONObject(0).getJSONArray("i").getString(2));
    }
    @Test public void previewHandlesAnotherControllerLED25To55() throws Exception {
        JSONObject b=snapshot(100,16);
        SegmentZone.Member member=new SegmentZone.Member("mac:112233445566","192.168.1.20",0,24,55,"Cabinets");
        JSONArray indices=WledSegmentPlanner.preview(b,Arrays.asList(member))
                .getJSONArray("seg").getJSONObject(0).getJSONArray("i");
        assertEquals(24,indices.getInt(0));assertEquals(55,indices.getInt(1));
    }
    @Test public void zoneSerializationPreservesIdentityAndBounds() throws Exception {
        SegmentZone z=SegmentZone.create("Entertainment");
        z.members.add(new SegmentZone.Member("mac:aa","192.168.1.10",2,100,200,"Wall"));
        z.members.add(new SegmentZone.Member("mac:bb","192.168.1.20",1,24,55,"Cabinets"));
        SegmentZone out=SegmentZone.fromJson(z.toJson());
        assertNotNull(out);assertEquals(2,out.members.size());assertEquals(24,out.members.get(1).start);
    }
    @Test public void staleBoundariesAreRejectedBeforeAnyCommand() throws Exception {
        SegmentZone.Member m=new SegmentZone.Member("mac:112233445566","192.168.1.10",0,101,200,"Wrong");
        assertFalse(WledSegmentPlanner.matches(m,snapshot(400,16)));
    }
    @Test public void zonePayloadChangesOnlyRequestedSegmentWithoutChangingEffect() throws Exception {
        JSONObject snap=snapshot(200,16);
        SegmentZone z=SegmentZone.create("Control");
        z.members.add(new SegmentZone.Member("mac:112233445566","192.168.1.10",0,0,200,"Segment 1"));
        WledDevice d=new WledDevice("192.168.1.10","Mock");d.applySnapshot(snap);
        final JSONObject[] sent={null};
        SegmentZoneController.Result result=SegmentZoneController.execute(z,Arrays.asList(d),
                new JSONObject(),null,0,0xFFFF6600,host->snap,(host,patch)->sent[0]=patch);
        assertEquals(1,result.confirmed);assertNotNull(sent[0]);
        JSONObject target=sent[0].getJSONArray("seg").getJSONObject(0);
        assertFalse(target.has("fx"));assertEquals(0,target.getInt("id"));
        assertEquals(255,target.getJSONArray("col").getJSONArray(0).getInt(0));
    }
    @Test public void restoreReleasesFrozenSegmentAndRestoresPriorEffect() throws Exception {
        JSONObject original=snapshot(400,16);
        JSONObject patch=WledSegmentPlanner.restore(original);
        assertEquals(123,patch.getInt("bri"));
        assertEquals(12,patch.getJSONArray("seg").getJSONObject(0).getInt("fx"));
        assertFalse(patch.getJSONArray("seg").getJSONObject(0).getBoolean("frz"));
    }
}
