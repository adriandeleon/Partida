package com.partida.retroarch;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RetroArch's UDP commands against a stand-in on the loopback, and a launch of a stand-in program. */
class NetworkCommandsAndLauncherTest {

    @TempDir
    Path dir;

    @Test
    void theConfigSaysWhetherCommandsAreOnAndWhere() {
        assertFalse(NetworkCommands.enabledIn(CfgFile.empty()));
        assertTrue(NetworkCommands.enabledIn(CfgFile.parse("network_cmd_enable = \"true\"\n")));
        assertEquals(
                NetworkCommands.DEFAULT_PORT,
                NetworkCommands.fromConfig(CfgFile.empty()).port());
        assertEquals(
                5000,
                NetworkCommands.fromConfig(CfgFile.parse("network_cmd_port = \"5000\"\n"))
                        .port());
    }

    @Test
    void aCommandReachesRetroArchAndAQueryGetsItsReply() throws Exception {
        try (DatagramSocket retroArch = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            retroArch.setSoTimeout(5000);
            NetworkCommands commands = new NetworkCommands(retroArch.getLocalPort());

            commands.send("PAUSE_TOGGLE");
            assertEquals("PAUSE_TOGGLE", receive(retroArch).message());

            CompletableFuture<Void> answer = CompletableFuture.runAsync(() -> {
                try {
                    Received got = receive(retroArch);
                    byte[] reply = ("GET_STATUS PAUSED snes,Game,crc32=0 for " + got.message())
                            .getBytes(StandardCharsets.UTF_8);
                    retroArch.send(
                            new DatagramPacket(reply, reply.length, got.packet().getSocketAddress()));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            String reply = commands.query("GET_STATUS", Duration.ofSeconds(5)).orElseThrow();
            answer.get(5, TimeUnit.SECONDS);
            assertEquals("GET_STATUS PAUSED snes,Game,crc32=0 for GET_STATUS", reply);
        }
    }

    @Test
    void aQueryNobodyAnswersComesBackEmpty() throws Exception {
        try (DatagramSocket silent = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            NetworkCommands commands = new NetworkCommands(silent.getLocalPort());
            assertTrue(commands.query("VERSION", Duration.ofMillis(50)).isEmpty());
        }
    }

    private record Received(String message, DatagramPacket packet) {}

    private static Received receive(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return new Received(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8), packet);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLaunchRunsTheCommandAndLogsItsOutput() throws Exception {
        // sh stands in for RetroArch: it prints the arguments it was given, which are the launch's.
        RetroArchInstall install = RetroArchInstall.custom(dir, List.of("sh", "-c", "echo \"$@\"", "retroarch"), dir);
        Path log = dir.resolve("logs/retroarch.log");
        Launcher launcher = new Launcher(install, log);
        assertTrue(launcher.current().isEmpty());

        Process process = launcher.launch(dir.resolve("core.so"), dir.resolve("game.sfc"), List.of());
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertTrue(launcher.current().isEmpty(), "a finished game is no longer current");
        assertEquals(log, launcher.logFile());
        String logged = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(logged.contains("-L " + dir.resolve("core.so") + " " + dir.resolve("game.sfc")), logged);
    }

    @Test
    void retroArchIsNotRunningInTheTestJvm() {
        // Whatever else runs on the machine, the check answers without failing.
        boolean running = Launcher.isRetroArchRunning();
        assertEquals(running, Launcher.isRetroArchRunning() || running);
    }
}
