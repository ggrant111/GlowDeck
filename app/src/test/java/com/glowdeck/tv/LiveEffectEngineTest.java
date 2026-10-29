package com.glowdeck.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveEffectEngineTest {
    private static LiveEffectEngine.Settings config(LiveEffectEngine.Effect effect){
        return new LiveEffectEngine.Settings(effect,50,100,0xFFFF0000,0xFF0000FF);
    }
    @Test public void allEffectsRenderAndStayWithinLedRange(){
        for(LiveEffectEngine.Effect effect:LiveEffectEngine.Effect.values()){
            int[] first=LiveEffectEngine.render(101,0,config(effect));
            int[] later=LiveEffectEngine.render(101,700,config(effect));
            assertEquals(101,first.length);
            assertEquals(101,later.length);
            assertNotEquals("Effect should animate: "+effect,java.util.Arrays.hashCode(first),java.util.Arrays.hashCode(later));
            for(int color:first)assertEquals(0xFF000000,color&0xFF000000);
        }
    }
    @Test public void solidEndpointsAndWipeNeverWriteOutsideSelectedFrame(){
        int[] data=LiveEffectEngine.render(31,200,config(LiveEffectEngine.Effect.COLOR_WIPE));
        assertEquals(31,data.length);
        assertEquals(0xFFFF0000,data[0]);
        assertEquals(0xFF0000FF,data[30]);
        int[] untouched=LiveEffectEngine.render(31,0,config(LiveEffectEngine.Effect.COLOR_WIPE));
        assertEquals(0xFF0000FF,untouched[0]);
    }
    @Test public void brightnessClampsAndScalesRgb(){
        LiveEffectEngine.Settings settings=new LiveEffectEngine.Settings(LiveEffectEngine.Effect.PULSE,50,50,0xFFFFFFFF,0xFFFFFFFF);
        int[] result=LiveEffectEngine.render(2,0,settings);
        assertEquals(0xFF808080,result[0]);
        assertEquals(0xFF808080,result[1]);
        assertEquals(100,new LiveEffectEngine.Settings(LiveEffectEngine.Effect.CHASE,1000,200,0,0).speed);
        assertEquals(100,new LiveEffectEngine.Settings(LiveEffectEngine.Effect.CHASE,1000,200,0,0).brightness);
    }
    @Test public void identicalInputYieldsIdenticalOutput(){
        LiveEffectEngine.Settings s=config(LiveEffectEngine.Effect.SPARKLE);
        assertArrayEquals(LiveEffectEngine.render(120,911,s),LiveEffectEngine.render(120,911,s));
    }
    @Test public void rejectsZeroAndExcessiveLedCounts(){
        try{LiveEffectEngine.render(0,1,config(LiveEffectEngine.Effect.CHASE));fail();}catch(IllegalArgumentException ok){}
        try{LiveEffectEngine.render(65537,1,config(LiveEffectEngine.Effect.CHASE));fail();}catch(IllegalArgumentException ok){}
    }
    @Test public void streamingPayloadUsesAbsoluteSegmentOffset(){
        int[] pixels=LiveEffectEngine.render(31,0,config(LiveEffectEngine.Effect.RAINBOW_FLOW));
        java.util.List<byte[]> packet=UdpRealtimePackets.dnrgb(24,pixels,2);
        assertEquals(1,packet.size());assertEquals(4,packet.get(0)[0]);assertEquals(24,packet.get(0)[3]);
        assertEquals(31*3+4,packet.get(0).length);
    }
}
