package com.glowdeck.tv;

import java.util.ArrayList;
import java.util.List;

/** Pure WLED realtime protocol encoder. DDP RGB24 port 4048; DNRGB port 21324. */
final class UdpRealtimePackets {
    static final int DDP_PORT = 4048;
    static final int WLED_REALTIME_PORT = 21324;
    static final int MAX_RGB_PIXELS = 480; // 1440 data bytes, safely below Ethernet MTU
    private UdpRealtimePackets() {}

    /** WLED UDP DNRGB (protocol 4): byte0 mode, byte1 timeout in seconds, bytes2/3 LED start, RGB triplets. */
    static List<byte[]> dnrgb(int firstLed, int[] argb, int timeoutSeconds) {
        if (argb == null || argb.length == 0 || firstLed < 0 || firstLed > 65535 ||
                (long) firstLed + argb.length > 65536L)
            throw new IllegalArgumentException("DNRGB range must fit in 16-bit LED coordinates");
        if (timeoutSeconds < 1 || timeoutSeconds > 254)
            throw new IllegalArgumentException("Realtime timeout must be 1–254 seconds");
        List<byte[]> packets = new ArrayList<>();
        for (int offset = 0; offset < argb.length; offset += MAX_RGB_PIXELS) {
            int count = Math.min(MAX_RGB_PIXELS, argb.length - offset);
            int start = firstLed + offset;
            byte[] packet = new byte[4 + 3 * count];
            packet[0] = 4;
            packet[1] = (byte) timeoutSeconds;
            packet[2] = (byte) (start >>> 8);
            packet[3] = (byte) start;
            for (int i = 0; i < count; i++) putRgb(packet, 4 + 3 * i, argb[offset + i]);
            packets.add(packet);
        }
        return packets;
    }

    /** DDP v1 RGB24, 10 byte header. The final chunk has PUSH; offsets are byte offsets. */
    static List<byte[]> ddp(int[] argb, int sequence) {
        if (argb == null || argb.length == 0) throw new IllegalArgumentException("Empty DDP frame");
        if (argb.length > 65536) throw new IllegalArgumentException("DDP test is capped at 65536 LEDs");
        List<byte[]> packets = new ArrayList<>();
        for (int offset = 0; offset < argb.length; offset += MAX_RGB_PIXELS) {
            int count = Math.min(MAX_RGB_PIXELS, argb.length - offset);
            byte[] packet = new byte[10 + 3 * count];
            boolean last = offset + count == argb.length;
            packet[0] = (byte) (last ? 0x41 : 0x40); // v1 plus PUSH on final packet
            packet[1] = (byte) (sequence & 0x0F);
            packet[2] = 0x0B; // RGB24
            packet[3] = 1; // display ID
            long byteOffset = (long) offset * 3;
            packet[4] = (byte) (byteOffset >>> 24);
            packet[5] = (byte) (byteOffset >>> 16);
            packet[6] = (byte) (byteOffset >>> 8);
            packet[7] = (byte) byteOffset;
            int length = count * 3;
            packet[8] = (byte) (length >>> 8);
            packet[9] = (byte) length;
            for (int i = 0; i < count; i++) putRgb(packet, 10 + 3 * i, argb[offset + i]);
            packets.add(packet);
        }
        return packets;
    }

    static int[] identifyColors(int count, Mode mode, long frame) {
        if (count < 1 || count > 65536) throw new IllegalArgumentException("Invalid LED count");
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            switch (mode) {
                case ENDS:
                    colors[i] = i == 0 ? 0xFF00BFFF : i == count - 1 ? 0xFFFF37AA : 0xFF302400;
                    break;
                case CHASE:
                    int head = (int) (frame % count);
                    int distance = (head - i + count) % count;
                    colors[i] = distance == 0 ? 0xFFFFFFFF : distance < 5 ? 0xFFFFA500 : 0xFF261400;
                    break;
                default:
                    colors[i] = 0xFFFFB400;
            }
        }
        return colors;
    }

    static int[] ddpRainbow(int count, long frame) {
        if (count < 1 || count > 65536) throw new IllegalArgumentException("Invalid LED count");
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            float hue = (float) (((i * 360.0 / Math.max(1, count)) + frame * 7) % 360.0);
            // Pure Java HSV -> RGB for JVM tests, no android.graphics dependency.
            float h = hue / 60f, c = 0.72f, x = c * (1 - Math.abs(h % 2 - 1));
            float r = 0, g = 0, b = 0;
            if (h < 1) {r = c;g = x;} else if (h < 2) {r = x;g = c;}
            else if (h < 3) {g = c;b = x;} else if (h < 4) {g = x;b = c;}
            else if (h < 5) {r = x;b = c;} else {r = c;b = x;}
            colors[i] = 0xFF000000 | ((int) (255 * (r + 0.10f)) << 16)
                    | ((int) (255 * (g + 0.10f)) << 8) | (int) (255 * (b + 0.10f));
        }
        return colors;
    }

    private static void putRgb(byte[] dest, int index, int argb) {
        dest[index] = (byte) (argb >>> 16);
        dest[index + 1] = (byte) (argb >>> 8);
        dest[index + 2] = (byte) argb;
    }

    enum Mode { SOLID, CHASE, ENDS, DDP_RAINBOW_TEST }
}
