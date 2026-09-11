package com.fnphoto.tv.login;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 登录页「手机扫码登录」使用的轻量 HTTP 服务。
 *
 * 工作流程：
 * 1. 电视端在登录页调用 {@link #start()} 绑定一个局域网端口，生成配对码与二维码 URL。
 * 2. 手机扫码后用浏览器打开该 URL，服务返回一个表单页（NAS 地址 / 账号 / 密码）。
 * 3. 手机提交表单，服务校验配对码后通过 {@link CredentialListener} 回调主线程，
 *    由 {@link com.fnphoto.tv.LoginActivity} 自动填表并走 WebSocket 登录。
 * 4. 登录结果通过 {@link #setResult(Result)} 写回，手机端轮询 /status 展示。
 *
 * 仅监听局域网，不依赖任何外部库，避免新增 Maven 依赖。
 */
public final class PairingHttpServer {

    public interface CredentialListener {
        void onCredentials(String nasUrl, String user, String pass);
    }

    public enum Result { WAITING, RECEIVED, SUCCESS, FAILED }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pairing-http-worker");
        t.setDaemon(true);
        return t;
    });

    private ServerSocket serverSocket;
    private int boundPort = -1;
    private String lanIp;
    private String pairingCode;
    private volatile CredentialListener listener;
    private volatile Result result = Result.WAITING;
    private volatile String resultMessage = "";
    private volatile boolean running;

    public void setCredentialListener(CredentialListener l) { this.listener = l; }

    public String getQrUrl() {
        if (lanIp == null || boundPort <= 0) return null;
        return "http://" + lanIp + ":" + boundPort + "/?code=" + pairingCode;
    }

    public String getLanIp() { return lanIp; }
    public String getPairingCode() { return pairingCode; }

    public void setResult(Result r, String msg) {
        this.result = r;
        this.resultMessage = msg == null ? "" : msg;
    }

    /** 启动服务；成功返回 true。 */
    public synchronized boolean start() {
        if (running) return true;
        lanIp = pickLanIpv4();
        if (lanIp == null) return false;
        pairingCode = String.format("%04d", new Random().nextInt(10000));
        result = Result.WAITING;
        resultMessage = "";
        try {
            serverSocket = new ServerSocket(0);
            serverSocket.setReuseAddress(true);
            boundPort = serverSocket.getLocalPort();
        } catch (IOException e) {
            return false;
        }
        running = true;
        new Thread(this::acceptLoop, "pairing-http-accept").start();
        return true;
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        serverSocket = null;
        boundPort = -1;
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            Socket client;
            try {
                client = serverSocket.accept();
            } catch (IOException e) {
                if (!running) break;
                continue;
            }
            final Socket c = client;
            workers.execute(() -> handle(c));
        }
    }

    private void handle(Socket client) {
        try {
            client.setSoTimeout(8000);
            InputStream is = client.getInputStream();
            OutputStream os = client.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) { write(os, 400, "Bad Request", ""); return; }
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) { write(os, 400, "Bad Request", ""); return; }
            String method = parts[0];
            String rawPath = parts[1];

            // 读 headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(),
                            line.substring(idx + 1).trim());
                }
            }

            String path = rawPath;
            Map<String, String> query = new HashMap<>();
            int q = rawPath.indexOf('?');
            if (q >= 0) {
                path = rawPath.substring(0, q);
                parseQuery(rawPath.substring(q + 1), query);
            }

            int contentLength = 0;
            String cl = headers.get("content-length");
            if (cl != null) {
                try { contentLength = Integer.parseInt(cl); } catch (NumberFormatException ignored) {}
            }
            char[] bodyBuf = new char[contentLength];
            if (contentLength > 0) {
                int read = 0;
                while (read < contentLength) {
                    int n = reader.read(bodyBuf, read, contentLength - read);
                    if (n < 0) break;
                    read += n;
                }
            }
            String body = new String(bodyBuf, 0, contentLength);

            if ("GET".equals(method) && ("/".equals(path) || "/index".equals(path))) {
                write(os, 200, "OK", buildFormPage(query.getOrDefault("code", "")));
            } else if ("POST".equals(method) && "/submit".equals(path)) {
                Map<String, String> form = new HashMap<>();
                parseQuery(body, form);
                handleSubmit(os, form);
            } else if ("GET".equals(method) && "/status".equals(path)) {
                write(os, 200, "OK", buildStatusJson());
            } else if ("GET".equals(method) && "/ping".equals(path)) {
                write(os, 200, "OK", "{\"ok\":true}");
            } else {
                write(os, 404, "Not Found", "Not Found");
            }
        } catch (IOException ignored) {
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void handleSubmit(OutputStream os, Map<String, String> form) {
        String code = form.getOrDefault("code", "").trim();
        String nas = form.getOrDefault("nas", "").trim();
        String user = form.getOrDefault("user", "").trim();
        String pass = form.getOrDefault("pass", "");
        if (code.isEmpty() || !code.equals(pairingCode)) {
            write(os, 403, "Forbidden", page("配对码不正确", "请核对电视屏幕上的 4 位配对码后重试。"));
            return;
        }
        if (nas.isEmpty() || user.isEmpty()) {
            write(os, 400, "Bad Request", page("信息不完整", "请填写 NAS 地址、账号和密码。"));
            return;
        }
        result = Result.RECEIVED;
        resultMessage = "已收到凭证，正在登录…";
        final CredentialListener l = listener;
        final String fNas = nas, fUser = user, fPass = pass;
        if (l != null) {
            mainHandler.post(() -> l.onCredentials(fNas, fUser, fPass));
        }
        write(os, 200, "OK", page("已发送到电视", "凭证已收到，请在电视屏幕上查看登录结果。"));
    }

    // ---------- HTML ----------

    private String buildFormPage(String code) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">"
                + "<title>飞牛相册 · 扫码登录</title>"
                + "<style>"
                + "*{box-sizing:border-box;margin:0;padding:0}"
                + "body{font-family:-apple-system,system-ui,sans-serif;background:#10141c;color:#e8edf5;padding:22px 18px 40px}"
                + "h1{font-size:22px;margin-bottom:4px}"
                + ".sub{color:#8a93a5;font-size:13px;margin-bottom:20px}"
                + "label{display:block;font-size:13px;color:#aeb6c4;margin:14px 0 6px}"
                + "input{width:100%;font-size:17px;padding:13px 14px;border:1px solid #2a3140;border-radius:12px;background:#161b26;color:#e8edf5;outline:none}"
                + "input:focus{border-color:#3aa6ff}"
                + "button{margin-top:24px;width:100%;padding:15px;border:0;border-radius:12px;background:linear-gradient(90deg,#3aa6ff,#2d7fd6);color:#fff;font-size:17px;font-weight:600}"
                + "button:disabled{opacity:.5}"
                + ".note{margin-top:18px;font-size:12px;color:#6c7689;line-height:1.6}"
                + "#status{margin-top:16px;padding:14px;border-radius:12px;background:#161b26;font-size:14px;display:none}"
                + "</style></head><body>"
                + "<h1>飞牛相册</h1><div class=\"sub\">扫码登录 · 把登录信息推送到电视</div>"
                + "<form id=\"f\" action=\"/submit\" method=\"POST\">"
                + "<label>配对码（电视屏幕上显示）</label>"
                + "<input name=\"code\" inputmode=\"numeric\" pattern=\"[0-9]*\" maxlength=\"4\" value=\"" + esc(code) + "\" placeholder=\"4 位数字\">"
                + "<label>NAS 地址</label>"
                + "<input name=\"nas\" autocomplete=\"off\" placeholder=\"如 192.168.1.10:5666 或 https://nas.example.com\">"
                + "<label>账号</label>"
                + "<input name=\"user\" autocomplete=\"username\" placeholder=\"登录账号\">"
                + "<label>密码</label>"
                + "<input name=\"pass\" type=\"password\" autocomplete=\"current-password\" placeholder=\"登录密码\">"
                + "<button type=\"submit\" id=\"btn\">发送到电视</button>"
                + "</form>"
                + "<div id=\"status\"></div>"
                + "<div class=\"note\">说明：手机与电视需在同一局域网。提交后请在电视屏幕查看登录结果。"
                + "NAS 地址格式与电视上手动填写一致，不带端口时默认 5666。</div>"
                + "<script>"
                + "(function(){"
                + "var f=document.getElementById('f'),b=document.getElementById('btn'),s=document.getElementById('status');"
                + "var polling=false;"
                + "function setStatus(msg){if(msg){s.style.display='block';s.textContent=msg;}}"
                + "function poll(){polling=true;fetch('/status').then(function(r){return r.text();})"
                + ".then(function(t){try{var j=JSON.parse(t);setStatus(j.message||'');"
                + "if(j.status==='SUCCESS'){b.textContent='登录成功';polling=false;return;}"
                + "if(j.status==='FAILED'){b.textContent='登录失败，可重试';b.disabled=false;polling=false;return;}"
                + "setTimeout(poll,2000);}catch(e){setTimeout(poll,2000);}})"
                + ".catch(function(){setTimeout(poll,2000);});}"
                + "f.addEventListener('submit',function(e){"
                + "e.preventDefault();b.disabled=true;b.textContent='发送中…';"
                + "var params=new URLSearchParams(new FormData(f));"
                + "fetch('/submit',{method:'POST',body:params}).then(function(r){return r.text();})"
                + ".then(function(){b.textContent='已发送，等待结果…';setTimeout(poll,600);})"
                + ".catch(function(){b.textContent='网络错误';b.disabled=false;});"
                + "});"
                + "})();"
                + "</script></body></html>";
    }

    private String buildStatusJson() {
        return "{\"status\":\"" + result.name() + "\",\"message\":\"" + esc(resultMessage) + "\"}";
    }

    private String page(String title, String body) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body>"
                + "<h2>" + esc(title) + "</h2><p>" + esc(body) + "</p>"
                + "<p><a href=\"/\">返回</a></p></body></html>";
    }

    private void write(OutputStream os, int code, String reason, String body) {
        byte[] data = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        sb.append("Content-Type: text/html; charset=utf-8\r\n");
        sb.append("Content-Length: ").append(data.length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("\r\n");
        try {
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.write(data);
            os.flush();
        } catch (IOException ignored) {}
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void parseQuery(String q, Map<String, String> out) {
        if (q == null || q.isEmpty()) return;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            String k, v;
            if (eq < 0) { k = pair; v = ""; }
            else { k = pair.substring(0, eq); v = pair.substring(eq + 1); }
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {}
            out.put(k, v);
        }
    }

    private static String pickLanIpv4() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                if (!ni.isUp() || ni.isVirtual() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                for (InetAddress a : Collections.list(addrs)) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")
                                || (ip.startsWith("172.") && isPrivate172(ip))) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isPrivate172(String ip) {
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }
}
