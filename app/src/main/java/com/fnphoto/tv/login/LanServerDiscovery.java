package com.fnphoto.tv.login;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LanServerDiscovery {
    public static final int DEFAULT_TV_PORT = 5666;
    private static final int[] DISCOVERY_PORTS = {DEFAULT_TV_PORT};
    private static final int CONNECT_TIMEOUT_MS = 260;
    private static final int MAX_SCAN_THREADS = 48;

    private LanServerDiscovery() {
    }

    public static final class ServerCandidate {
        public final String name;
        public final String baseUrl;
        public final String host;
        public final int port;
        public final boolean discovered;

        public ServerCandidate(String name, String baseUrl, String host, int port, boolean discovered) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.host = host;
            this.port = port;
            this.discovered = discovered;
        }
    }

    public interface Callback {
        void onComplete(List<ServerCandidate> servers);
    }

    public static void discoverAsync(Callback callback) {
        new Thread(() -> {
            List<ServerCandidate> servers = discoverBlocking();
            if (callback != null) {
                callback.onComplete(servers);
            }
        }, "fnphoto-lan-discovery").start();
    }

    public static void discoverSavedAsync(List<String> savedUrls, Callback callback) {
        new Thread(() -> {
            List<ServerCandidate> servers = discoverSavedBlocking(savedUrls);
            if (callback != null) {
                callback.onComplete(servers);
            }
        }, "fnphoto-saved-discovery").start();
    }

    public static List<ServerCandidate> discoverBlocking() {
        Set<String> hosts = new LinkedHashSet<>();
        for (String localIp : localIpv4Addresses()) {
            hosts.addAll(classCSubnetHosts(localIp));
        }
        if (hosts.isEmpty()) {
            return Collections.emptyList();
        }

        List<ServerCandidate> found = Collections.synchronizedList(new ArrayList<>());
        Set<String> foundUrls = Collections.synchronizedSet(new LinkedHashSet<>());
        CountDownLatch latch = new CountDownLatch(hosts.size() * DISCOVERY_PORTS.length);
        ExecutorService executor = Executors.newFixedThreadPool(MAX_SCAN_THREADS);

        for (String host : hosts) {
            for (int port : DISCOVERY_PORTS) {
                executor.execute(() -> {
                    try {
                        if (canConnect(host, port, CONNECT_TIMEOUT_MS)) {
                            String baseUrl = endpoint(host, port);
                            if (foundUrls.add(baseUrl)) {
                                found.add(new ServerCandidate("飞牛服务器", baseUrl, host, port, true));
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            latch.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        found.sort((a, b) -> {
            int byPort = Integer.compare(portPriority(a.port), portPriority(b.port));
            if (byPort != 0) return byPort;
            return a.host.compareTo(b.host);
        });
        return new ArrayList<>(found);
    }

    public static List<ServerCandidate> discoverSavedBlocking(List<String> savedUrls) {
        if (savedUrls == null || savedUrls.isEmpty()) {
            return Collections.emptyList();
        }

        List<ServerCandidate> found = new ArrayList<>();
        Set<String> checkedUrls = new LinkedHashSet<>();
        for (String savedUrl : savedUrls) {
            String baseUrl = normalizeServerInput(savedUrl);
            if (baseUrl.isEmpty() || !checkedUrls.add(baseUrl)) {
                continue;
            }

            HostPort hostPort = hostPort(baseUrl);
            if (hostPort == null) {
                continue;
            }
            if (canConnect(hostPort.host, hostPort.port, CONNECT_TIMEOUT_MS)) {
                found.add(new ServerCandidate(
                        "历史服务器",
                        baseUrl,
                        hostPort.host,
                        hostPort.port,
                        false
                ));
            }
        }
        return found;
    }

    public static List<String> classCSubnetHosts(String localIp) {
        String[] parts = localIp == null ? new String[0] : localIp.split("\\.");
        if (parts.length != 4) return Collections.emptyList();

        String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        List<String> hosts = new ArrayList<>(253);
        for (int i = 1; i <= 254; i++) {
            String candidate = prefix + i;
            if (!candidate.equals(localIp)) {
                hosts.add(candidate);
            }
        }
        return hosts;
    }

    public static String normalizeServerInput(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) return "";

        String scheme = "http://";
        if (raw.startsWith("http://")) {
            raw = raw.substring("http://".length());
        } else if (raw.startsWith("https://")) {
            scheme = "https://";
            raw = raw.substring("https://".length());
        }

        int slash = raw.indexOf('/');
        if (slash >= 0) {
            raw = raw.substring(0, slash);
        }

        if (hasPort(raw)) {
            return scheme + raw;
        }
        return scheme + raw + ":" + DEFAULT_TV_PORT;
    }

    public static List<String> localIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && address.isSiteLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return addresses;
    }

    private static boolean hasPort(String host) {
        if (host.startsWith("[")) {
            int closing = host.indexOf("]");
            return closing >= 0 && closing + 1 < host.length() && host.charAt(closing + 1) == ':';
        }
        return host.contains(":");
    }

    private static boolean canConnect(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String endpoint(String host, int port) {
        return "http://" + host + ":" + port;
    }

    private static HostPort hostPort(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isEmpty()) return null;
            if (port <= 0) port = DEFAULT_TV_PORT;
            return new HostPort(host, port);
        } catch (Exception ignored) {
            return null;
        }
    }

    static int[] discoveryPortsForTest() {
        return DISCOVERY_PORTS.clone();
    }

    private static int portPriority(int port) {
        if (port == 5666) return 0;
        return 1;
    }

    private static final class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
