package com.fnphoto.tv.login;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 局域网内飞牛 NAS 服务发现。
 *
 * <p>两阶段策略：
 * <ol>
 *   <li><b>端口探测</b>：48 线程并发 TCP 探测候选 IP × {5666, 8000, 5000, 80}，
 *       任一端口连通即记为候选（每 host 命中一个端口就停止）。</li>
 *   <li><b>指纹验证</b>：候选 IP 通过 HTTP GET <code>/v/api/v1/sys/config</code> 验证
 *       响应是否为飞牛 NAS（要求 <code>code=0</code> 且 <code>data</code> 含
 *       <code>name/deviceName/serverName/hostname</code> 字段），剔除路由器、
 *       摄像头、其他 HTTP 服务等误报。</li>
 * </ol>
 *
 * <p>端口优先级：5666（飞牛旧版默认）→ 8000（新版默认）→ 5000（部分旧版）→ 80（标准 HTTP）。
 *
 * <p>单次发现最大耗时 6 秒（端口探测）+ 单 host 指纹验证 1.5 秒。
 */
public final class LanServerDiscovery {
    public static final int DEFAULT_TV_PORT = 5666;

    /** 按优先级排序的探测端口列表（高位优先）。 */
    private static final int[] DISCOVERY_PORTS = {5666, 8000, 5000, 80};

    /** TCP 握手单连接超时（毫秒）。 */
    private static final int CONNECT_TIMEOUT_MS = 260;

    /** 并发探测线程数。 */
    private static final int MAX_SCAN_THREADS = 48;

    /** 端口扫描阶段总超时（秒）。 */
    private static final int SCAN_TIMEOUT_SECONDS = 6;

    /** 单 host 指纹验证超时（毫秒）。 */
    private static final int FINGERPRINT_TIMEOUT_MS = 1500;

    /** 指纹端点路径。 */
    private static final String FINGERPRINT_PATH = "/v/api/v1/sys/config";

    private static final String TAG = "FnLanDiscovery";

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

    /**
     * 局域网扫描入口。两阶段：端口探测 → 指纹验证。
     */
    public static List<ServerCandidate> discoverBlocking() {
        Set<String> hosts = collectCandidateHosts();
        if (hosts.isEmpty()) {
            Log.w(TAG, "no candidate hosts (no local IPv4)");
            return Collections.emptyList();
        }
        Log.i(TAG, "scanning " + hosts.size() + " hosts x " + DISCOVERY_PORTS.length + " ports");

        // 第一阶段：端口探测。每个 host × port 组合分配一个探测任务。
        // 命中后该 host 取消后续探测（节约线程）。
        Map<String, Integer> hostToPort = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(hosts.size() * DISCOVERY_PORTS.length);
        ExecutorService executor = Executors.newFixedThreadPool(MAX_SCAN_THREADS);

        for (String host : hosts) {
            for (int port : DISCOVERY_PORTS) {
                executor.execute(() -> {
                    try {
                        if (hostToPort.containsKey(host)) {
                            // 该 host 已经被其他端口命中，跳过。
                            return;
                        }
                        if (canConnect(host, port, CONNECT_TIMEOUT_MS)) {
                            Integer prev = hostToPort.putIfAbsent(host, port);
                            if (prev == null) {
                                Log.d(TAG, "TCP open " + host + ":" + port);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            latch.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        Log.i(TAG, "phase1 done: " + hostToPort.size() + " candidates");

        if (hostToPort.isEmpty()) {
            return Collections.emptyList();
        }

        // 第二阶段：指纹验证。
        List<ServerCandidate> verified = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : hostToPort.entrySet()) {
            String host = entry.getKey();
            int port = entry.getValue();
            String baseUrl = endpoint(host, port);
            String serverName = fingerprintProbe(baseUrl);
            if (serverName != null) {
                String displayName = serverName.isEmpty() ? "飞牛服务器" : serverName;
                verified.add(new ServerCandidate(displayName, baseUrl, host, port, true));
                Log.i(TAG, "verified " + baseUrl + " -> " + displayName);
            } else {
                Log.d(TAG, "fingerprint miss " + baseUrl);
            }
        }

        verified.sort((a, b) -> {
            int byPort = Integer.compare(portPriority(a.port), portPriority(b.port));
            if (byPort != 0) return byPort;
            return a.host.compareTo(b.host);
        });
        return verified;
    }

    /**
     * 历史服务器探测：直接对已保存的 URL 做指纹验证，不再扫描端口。
     * 用于「上次登录过但本次扫描时没开」的服务器（探测失败即丢弃）。
     */
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

            // 先 TCP 探测连通性（剔除已下线的历史服务器）。
            if (!canConnect(hostPort.host, hostPort.port, CONNECT_TIMEOUT_MS)) {
                continue;
            }
            // 指纹验证为可选增强：拿到真名替换「历史服务器」标签；拿不到就用默认标签。
            String serverName = fingerprintProbe(baseUrl);
            String displayName = (serverName != null && !serverName.isEmpty())
                    ? serverName : "历史服务器";
            found.add(new ServerCandidate(
                    displayName,
                    baseUrl,
                    hostPort.host,
                    hostPort.port,
                    false
            ));
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

    /**
     * 收集候选 IP 列表：本机所有私网 IPv4 所在 C 段。
     * 若本机没拿到私网 IP（少见，例如 TV 没接网线），尝试一组常见家用网关地址的 C 段兜底。
     */
    private static Set<String> collectCandidateHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        List<String> localIps = localIpv4Addresses();
        for (String localIp : localIps) {
            hosts.addAll(classCSubnetHosts(localIp));
        }
        if (hosts.isEmpty()) {
            // 兜底：常见家用网关
            String[] gateways = {"192.168.1.1", "192.168.0.1", "192.168.31.1", "10.0.0.1"};
            for (String gw : gateways) {
                hosts.addAll(classCSubnetHosts(gw));
            }
        }
        return hosts;
    }

    /**
     * 对 baseUrl 做指纹验证。命中飞牛 NAS 返回服务器名，否则返回 null。
     * 验证规则：HTTP 200 + body 包含 <code>"code":0</code> + <code>data</code>
     * 含 <code>name/deviceName/serverName/hostname</code> 之一。
     */
    static String fingerprintProbe(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) return null;
        String url = baseUrl + FINGERPRINT_PATH;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(FINGERPRINT_TIMEOUT_MS);
            conn.setReadTimeout(FINGERPRINT_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "feiniu-album-tv/discovery");
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            InputStream in = conn.getInputStream();
            try {
                byte[] buf = new byte[2048];
                int len = in.read(buf);
                if (len <= 0) return null;
                String body = new String(buf, 0, len, "UTF-8");
                return parseFeiNiuSysConfig(body);
            } finally {
                try { in.close(); } catch (IOException ignored) {}
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 从 <code>/v/api/v1/sys/config</code> 响应里抽取服务器名。
     * 飞牛响应形如 <code>{"code":0,"data":{"name":"fnNAS","deviceName":"..."}}</code>。
     */
    static String parseFeiNiuSysConfig(String body) {
        if (body == null || body.isEmpty()) return null;
        // 必须是飞牛 NAS：code=0 必有
        int codeIdx = body.indexOf("\"code\"");
        if (codeIdx < 0) return null;
        int code0 = body.indexOf("\"0\"", codeIdx);
        int code0b = body.indexOf(":0", codeIdx);
        // 部分飞牛响应 code 是数字 0：{"code":0,...}；也有字符串"0"的形式
        if (code0 < 0 && code0b < 0) return null;
        if (!body.contains("\"data\"")) return null;

        // 抽取服务器名字段
        String[] keys = {"name", "deviceName", "serverName", "hostname"};
        for (String key : keys) {
            String value = extractJsonString(body, key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";  // 是飞牛但没拿到名字
    }

    /** 极简 JSON 字段抽取。 */
    static String extractJsonString(String body, String key) {
        String quotedKey = "\"" + key + "\"";
        int idx = body.indexOf(quotedKey);
        if (idx < 0) return null;
        int colon = body.indexOf(':', idx + quotedKey.length());
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < body.length() && Character.isWhitespace(body.charAt(p))) p++;
        if (p >= body.length() || body.charAt(p) != '"') return null;
        p++;
        StringBuilder sb = new StringBuilder();
        while (p < body.length()) {
            char c = body.charAt(p);
            if (c == '\\' && p + 1 < body.length()) {
                char next = body.charAt(p + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (p + 5 < body.length()) {
                            String hex = body.substring(p + 2, p + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ignored) {}
                            p += 4;
                        }
                        break;
                    default: sb.append(next); break;
                }
                p += 2;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                p++;
            }
        }
        return null;
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
            java.net.URI uri = java.net.URI.create(baseUrl);
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
        for (int i = 0; i < DISCOVERY_PORTS.length; i++) {
            if (DISCOVERY_PORTS[i] == port) return i;
        }
        return DISCOVERY_PORTS.length + 1;
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