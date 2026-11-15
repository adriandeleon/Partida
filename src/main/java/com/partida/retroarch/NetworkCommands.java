package com.partida.retroarch;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Sends commands to a running RetroArch over UDP. Requires {@code network_cmd_enable = "true"} in
 * retroarch.cfg (port {@code network_cmd_port}, default 55355).
 *
 * <p>Useful commands: QUIT, PAUSE_TOGGLE, SAVE_STATE, LOAD_STATE, STATE_SLOT_PLUS, STATE_SLOT_MINUS,
 * FULLSCREEN_TOGGLE, SCREENSHOT, RESET, "SHOW_MSG text". Queries that reply: VERSION, GET_STATUS.
 */
public final class NetworkCommands {

    public static final int DEFAULT_PORT = 55355;

    private final InetAddress host;
    private final int port;

    public NetworkCommands(int port) {
        this(InetAddress.getLoopbackAddress(), port);
    }

    public NetworkCommands(InetAddress host, int port) {
        this.host = host;
        this.port = port;
    }

    /** The UDP port RetroArch listens on. */
    public int port() {
        return port;
    }

    public static NetworkCommands fromConfig(CfgFile cfg) {
        return new NetworkCommands(cfg.getInt("network_cmd_port", DEFAULT_PORT));
    }

    public static boolean enabledIn(CfgFile cfg) {
        return cfg.getBool("network_cmd_enable", false);
    }

    /** Fire and forget. UDP gives no confirmation that RetroArch received it. */
    public void send(String command) throws IOException {
        byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(bytes, bytes.length, host, port));
        }
    }

    /** Sends a command and waits for one reply, e.g. GET_STATUS → "GET_STATUS PLAYING snes,Game Name,crc32=…". */
    public Optional<String> query(String command, Duration timeout) throws IOException {
        byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout((int) Math.max(1, timeout.toMillis()));
            socket.send(new DatagramPacket(bytes, bytes.length, host, port));
            byte[] buffer = new byte[8192];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(reply);
            } catch (SocketTimeoutException e) {
                return Optional.empty();
            }
            return Optional.of(new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8).strip());
        }
    }
}
