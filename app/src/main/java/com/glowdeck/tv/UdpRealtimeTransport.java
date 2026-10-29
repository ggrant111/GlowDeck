package com.glowdeck.tv;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

/** Android supports the standard Java unicast UDP socket APIs with INTERNET permission. */
final class UdpRealtimeTransport implements Closeable {
    private final DatagramSocket socket;
    UdpRealtimeTransport() throws IOException { socket = new DatagramSocket(); }
    void send(String host, int port, List<byte[]> packets) throws IOException {
        InetAddress address = InetAddress.getByName(host);
        for (byte[] packet : packets) socket.send(new DatagramPacket(packet, packet.length, address, port));
    }
    @Override public void close() { socket.close(); }
}
