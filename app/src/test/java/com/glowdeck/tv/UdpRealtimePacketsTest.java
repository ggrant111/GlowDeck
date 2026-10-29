package com.glowdeck.tv;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UdpRealtimePacketsTest {
    @Test public void dnrgbEncodesAbsoluteStartAndRGBAndTimeout() {
        List<byte[]> frames=UdpRealtimePackets.dnrgb(100,new int[]{0xFF112233,0xFFAA55CC},2);
        assertEquals(1,frames.size());
        assertArrayEquals(new byte[]{4,2,0,100,0x11,0x22,0x33,(byte)0xAA,0x55,(byte)0xCC},frames.get(0));
    }
    @Test public void dnrgbChunksAcross480PixelBoundaries() {
        int[] rgb=new int[500];for(int i=0;i<500;i++)rgb[i]=0xFF223344;
        List<byte[]> list=UdpRealtimePackets.dnrgb(550,rgb,2);
        assertEquals(2,list.size());
        assertEquals(4+3*480,list.get(0).length);
        assertEquals(4+3*20,list.get(1).length);
        assertEquals(2,list.get(0)[2] & 255);
        assertEquals(38,list.get(0)[3] & 255); // 550 = 0x0226
        assertEquals(4,list.get(1)[2] & 255);
        assertEquals(6,list.get(1)[3] & 255); // 1030 = 0x0406
    }
    @Test public void dnrgbRejects16BitOverflowAndPermanentHold() {
        try {UdpRealtimePackets.dnrgb(65534,new int[3],2);fail();}catch(IllegalArgumentException expected){}
        try {UdpRealtimePackets.dnrgb(0,new int[1],255);fail();}catch(IllegalArgumentException expected){}
        try {UdpRealtimePackets.dnrgb(-1,new int[1],2);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void ddpHasHeaderOffsetsAndOnlyFinalPush() {
        int[] rgb=new int[500];for(int i=0;i<500;i++)rgb[i]=0xFF112233;
        List<byte[]> list=UdpRealtimePackets.ddp(rgb,7);
        assertEquals(2,list.size());
        byte[] first=list.get(0),last=list.get(1);
        assertEquals(1450,first.length);
        assertEquals(70,last.length);
        assertEquals(0x40,first[0] & 255);
        assertEquals(0x41,last[0] & 255);
        assertEquals(7,first[1]); assertEquals(7,last[1]);
        assertEquals(0x0B,first[2]);assertEquals(1,first[3]);
        assertEquals(0,first[7] & 255);assertEquals(5,last[6] & 255);
        assertEquals(160,last[7] & 255); // 1440 = 0x05A0
        assertEquals(0x05,first[8] & 255);assertEquals(0xA0,first[9] & 255);
        assertEquals(60,last[9] & 255);
        assertEquals(0x11,last[10] & 255);
    }
    @Test public void identificationMarksTheCorrectEndpointsAndAdvancesChase() {
        int[] endpoints=UdpRealtimePackets.identifyColors(30,UdpRealtimePackets.Mode.ENDS,0);
        assertEquals(0xFF00BFFF,endpoints[0]);
        assertEquals(0xFFFF37AA,endpoints[29]);
        assertNotEquals(endpoints[0],endpoints[5]);
        int[] first=UdpRealtimePackets.identifyColors(30,UdpRealtimePackets.Mode.CHASE,0);
        int[] second=UdpRealtimePackets.identifyColors(30,UdpRealtimePackets.Mode.CHASE,1);
        assertEquals(0xFFFFFFFF,first[0]);
        assertEquals(0xFFFFFFFF,second[1]);
        assertEquals(0xFFFFB400,UdpRealtimePackets.identifyColors(1,UdpRealtimePackets.Mode.SOLID,0)[0]);
    }
    @Test public void ddpRainbowIsMovingAndNotAndroidDependent() {
        int[] first=UdpRealtimePackets.ddpRainbow(32,0);
        int[] next=UdpRealtimePackets.ddpRainbow(32,1);
        assertEquals(32,first.length);
        assertNotEquals(first[0],first[1]);
        assertNotEquals(first[0],next[0]);
    }
}
