import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public final class AdminWebServer {
    private static AdminWebServer instance;
    private HttpServer server;
    private WalletStore store;
    private String adminPass = "";
    private int port;
    private final Map<String, Session> sessions = Collections.synchronizedMap(new HashMap<String, Session>());
    private final SecureRandom rnd = new SecureRandom();

    static final class Session { String csrf; long expiresAt; }

    private AdminWebServer() {}
    public static synchronized AdminWebServer getInstance() { if (instance == null) instance = new AdminWebServer(); return instance; }
    public synchronized boolean isRunning() { return server != null; }

    public synchronized void startIfConfigured(WalletStore walletStore) {
        this.store = walletStore;
        if (server != null) return;
        String pass = "";
        int p = 8090;
        try { pass = WalletService.getAdminPass(); p = WalletService.getAdminPort(); } catch (Exception ignored) {}
        if (pass == null || pass.trim().isEmpty()) { log("Admin web disabled (no ADMIN_PASS)"); return; }
        start(p, pass, walletStore);
    }

    public synchronized void start(int p, String pass, WalletStore walletStore) {
        this.store = walletStore;
        if (server != null) return;
        if (pass == null || pass.trim().isEmpty()) return;
        this.port = p; this.adminPass = pass;
        String bind = cfgTag("ADMIN_BIND");
        try {
            InetSocketAddress addr = bind.isEmpty() ? new InetSocketAddress(p) : new InetSocketAddress(bind, p);
            if (httpsWanted()) {
                javax.net.ssl.SSLContext ssl = buildSslContext();
                if (ssl == null) { log("!!! Admin web KHONG khoi dong: HTTPS bat nhung khong tao/doc duoc chung chi. Xem loi phia tren. (Dat [ADMIN_HTTPS]0[/ADMIN_HTTPS] neu muon chay HTTP) !!!"); return; }
                com.sun.net.httpserver.HttpsServer hs = com.sun.net.httpserver.HttpsServer.create(addr, 0);
                hs.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ssl));
                server = hs; https = true;
            } else {
                server = HttpServer.create(addr, 0); https = false;
                log("!!! WARNING: Admin web dang chay HTTP (khong ma hoa) vi [ADMIN_HTTPS]0 !!!");
            }
            server.createContext("/", new Handler());
            server.setExecutor(Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) { Thread t = new Thread(r); t.setDaemon(true); return t; }
            }));
            server.start();
            log("Admin web started on " + (bind.isEmpty() ? "*" : bind) + ":" + p + (https ? " (HTTPS)" : " (HTTP)"));
        } catch (Exception e) { log("Admin web start failed port " + p + ": " + e.getMessage()); server = null; }
    }

    public synchronized void stop() { if (server != null) { try { server.stop(0); } catch (Exception ignored) {} server = null; } }

    // ---------- HTTPS ----------
    private volatile boolean https = false;
    private static String cfgTag(String tag) { try { String v = WalletService.tagValue(WalletService.readConfigContent(), tag); return v == null ? "" : v.trim(); } catch (Exception e) { return ""; } }
    /** HTTPS mac dinh BAT; chi tat khi [ADMIN_HTTPS]0[/ADMIN_HTTPS]. */
    private static boolean httpsWanted() { return !cfgTag("ADMIN_HTTPS").equals("0"); }

    /**
     * Neu co [ADMIN_CERT]duong_dan.p12[/ADMIN_CERT] + [ADMIN_CERT_PASS] thi dung chung chi do.
     * Neu khong: tu tao chung chi tu ky admin_keystore.p12 (mat khau ngau nhien luu o admin_keystore.pass)
     * trong thu muc lam viec bang keytool cua chinh JVM dang chay.
     */
    private javax.net.ssl.SSLContext buildSslContext() {
        try {
            String certPath = cfgTag("ADMIN_CERT");
            char[] pw;
            File ksFile;
            if (!certPath.isEmpty()) {
                ksFile = new File(certPath);
                if (!ksFile.isAbsolute() && workDir() != null) ksFile = new File(workDir(), certPath);
                if (!ksFile.exists()) { log("HTTPS: khong thay file chung chi " + ksFile.getAbsolutePath()); return null; }
                pw = cfgTag("ADMIN_CERT_PASS").toCharArray();
            } else {
                String wd = workDir(); if (wd == null) { log("HTTPS: khong co thu muc lam viec"); return null; }
                ksFile = new File(wd, "admin_keystore.p12");
                File passFile = new File(wd, "admin_keystore.pass");
                if (!ksFile.exists()) {
                    // Ghi mat khau TRUOC, tao keystore vao file tam roi moi doi ten.
                    // Neu tat giua chung: lan sau chua co .p12 -> tu tao lai ca hai (khong ket).
                    writeAtomic(passFile, newToken());
                    File tmp = new File(wd, "admin_keystore.p12.tmp");
                    if (tmp.exists()) tmp.delete();
                    if (!genSelfSigned(tmp, passFile)) { tmp.delete(); return null; }
                    if (!tmp.renameTo(ksFile)) { tmp.delete(); log("HTTPS: khong doi ten duoc " + tmp.getAbsolutePath()); return null; }
                    log("HTTPS: da tao chung chi tu ky " + ksFile.getAbsolutePath());
                } else if (!passFile.exists()) {
                    log("HTTPS: thieu admin_keystore.pass -> xoa admin_keystore.p12 roi khoi dong lai de tao moi"); return null;
                }
                List<String> pl = readLines(passFile, 0);
                pw = (pl.isEmpty() ? "" : pl.get(0).trim()).toCharArray();
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            InputStream in = new FileInputStream(ksFile);
            try { ks.load(in, pw); } finally { in.close(); }
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pw);
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            try {
                Enumeration<String> al = ks.aliases();
                while (al.hasMoreElements()) {
                    java.security.cert.Certificate c = ks.getCertificate(al.nextElement());
                    if (c == null) continue;
                    byte[] d = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
                    StringBuilder sb = new StringBuilder(); for (int i = 0; i < d.length; i++) { if (i > 0) sb.append(':'); sb.append(String.format("%02X", d[i])); }
                    log("HTTPS: SHA-256 chung chi = " + sb); break;
                }
            } catch (Exception ignored) {}
            return ctx;
        } catch (Exception e) { log("HTTPS: loi nap chung chi: " + e); return null; }
    }
    private boolean genSelfSigned(File ksFile, File passFile) {
        File outLog = new File(ksFile.getAbsolutePath() + ".log");
        try {
            String home = System.getProperty("java.home");
            File kt = new File(home, "bin" + File.separator + "keytool.exe");
            if (!kt.exists()) kt = new File(home, "bin" + File.separator + "keytool");
            String exe = kt.exists() ? kt.getAbsolutePath() : "keytool";
            // Mat khau doc tu file (-storepass:file) -> khong lo tren dong lenh tien trinh.
            ProcessBuilder pb = new ProcessBuilder(exe, "-genkeypair", "-noprompt", "-alias", "admin", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650", "-storetype", "PKCS12", "-keystore", ksFile.getAbsolutePath(),
                "-storepass:file", passFile.getAbsolutePath(), "-keypass:file", passFile.getAbsolutePath(),
                "-dname", "CN=NinjaBan Admin", "-ext", "SAN=dns:localhost,ip:127.0.0.1");
            pb.redirectErrorStream(true);
            pb.redirectOutput(outLog); // ghi ra file -> waitFor co timeout that, khong bi chan khi doc pipe
            Process pr = pb.start();
            try { pr.getOutputStream().close(); } catch (Exception ignored) {} // dong stdin: keytool khong the treo cho nhap
            if (!pr.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) { pr.destroyForcibly(); log("HTTPS: keytool qua thoi gian 60s"); return false; }
            if (pr.exitValue() != 0 || !ksFile.exists()) {
                String msg = ""; for (String l : readLines(outLog, 20)) msg += l.trim() + " ";
                log("HTTPS: keytool loi (exit " + pr.exitValue() + "): " + msg.trim()); return false;
            }
            return true;
        } catch (Exception e) { log("HTTPS: khong chay duoc keytool: " + e); return false; }
        finally { try { outLog.delete(); } catch (Exception ignored) {} }
    }

    // ---------- chong do mat khau ----------
    static final int MAX_FAILS = 5;
    static final long LOCK_MS = 30L * 60 * 1000;
    static final int MAX_TRACKED = 5000;
    static final class FailInfo { int count; long lastFail; long lockedUntil; }
    private final Map<String, FailInfo> fails = new HashMap<String, FailInfo>();

    /** Chuan hoa chuoi IP tu header; null neu khong phai IP hop le (chong gia mao/chen log). */
    private static String parseIpLiteral(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            for (String o : s.split("\\.")) if (Integer.parseInt(o) > 255) return null;
            return s;
        }
        // co dau ':' -> Java parse nhu IPv6 literal, KHONG tra DNS
        if (s.indexOf(':') >= 0 && s.length() <= 45 && s.matches("[0-9a-fA-F:.]+")) {
            try { return ipKey(InetAddress.getByName(s)); } catch (Exception e) { return null; }
        }
        return null;
    }
    /** IPv4 giu nguyen; IPv6 gop theo /64 (1 nguoi thuong so huu ca dai /64 -> khong lach khoa bang doi dia chi). */
    private static String ipKey(InetAddress a) {
        if (a instanceof Inet6Address && !a.isLoopbackAddress()) {
            byte[] b = a.getAddress(); StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i += 2) { if (i > 0) sb.append(':'); sb.append(Integer.toHexString(((b[i] & 0xff) << 8) | (b[i + 1] & 0xff))); }
            return sb.append("::/64").toString();
        }
        return a.getHostAddress();
    }
    /**
     * Chi tin header IP khi ket noi den tu chinh VPS (proxy chay cung may).
     * - [ADMIN_PROXY]cloudflare[/ADMIN_PROXY]: dung CF-Connecting-IP (Cloudflare ghi de, client khong gia duoc).
     * - Mac dinh: dung phan tu CUOI cua X-Forwarded-For (do proxy them vao; phan dau client tu gui).
     * Ket noi tu ngoai vao truc tiep -> bo qua moi header.
     */
    private static String clientIp(HttpExchange ex) {
        try {
            InetAddress a = ex.getRemoteAddress().getAddress();
            if (a.isLoopbackAddress()) {
                if (cfgTag("ADMIN_PROXY").equalsIgnoreCase("cloudflare")) {
                    String cf = parseIpLiteral(ex.getRequestHeaders().getFirst("CF-Connecting-IP"));
                    if (cf != null) return cf;
                }
                String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
                if (xff != null && !xff.trim().isEmpty()) {
                    String[] parts = xff.split(",");
                    String last = parseIpLiteral(parts[parts.length - 1]);
                    if (last != null) return last;
                }
            }
            return ipKey(a);
        } catch (Exception e) { return "?"; }
    }
    /** >0 = so ms con bi khoa. */
    private long lockRemaining(String ip) {
        synchronized (fails) {
            FailInfo f = fails.get(ip); long now = System.currentTimeMillis();
            return (f != null && f.lockedUntil > now) ? f.lockedUntil - now : 0;
        }
    }
    /**
     * Giu cho 1 lan thu TRUOC khi so mat khau (chong gui song song vuot gioi han).
     * Tra ve -1 neu dang bi khoa, -2 neu bang theo doi da day (dang bi tan cong dien rong),
     * nguoc lai so lan thu con lai SAU lan nay.
     */
    private int reserveAttempt(String ip) {
        synchronized (fails) {
            long now = System.currentTimeMillis();
            FailInfo f = fails.get(ip);
            if (f == null) {
                if (fails.size() >= MAX_TRACKED) {
                    Iterator<Map.Entry<String, FailInfo>> it = fails.entrySet().iterator();
                    while (it.hasNext()) { FailInfo o = it.next().getValue(); if (o.lockedUntil < now && now - o.lastFail > LOCK_MS) it.remove(); }
                    if (fails.size() >= MAX_TRACKED) return -2; // khong cho bang phinh vo han
                }
                f = new FailInfo(); fails.put(ip, f);
            }
            if (f.lockedUntil > now) return -1;
            if (f.count > 0 && now - f.lastFail > LOCK_MS) f.count = 0; // lan sai cu da qua lau
            f.count++; f.lastFail = now;
            if (f.count >= MAX_FAILS) { f.lockedUntil = now + LOCK_MS; f.count = 0; return 0; }
            return MAX_FAILS - f.count;
        }
    }
    private void clearFails(String ip) { synchronized (fails) { fails.remove(ip); } }
    private String lockMsg(long remainMs) {
        long min = (remainMs + 59999) / 60000;
        return "IP của bạn đang bị khóa đăng nhập do nhập sai mật khẩu " + MAX_FAILS + " lần. Thử lại sau khoảng " + min + " phút.";
    }

    private void log(String m) { System.out.println(m); try { WalletService.getInstance().logAtm(m); } catch (Exception ignored) {} }
    private String workDir() { try { return Class_cu.g; } catch (Exception e) { return null; } }

    // ---------- helpers ----------
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String fmt(long v) {
        try { String s = Class_bb.a(v); if (s != null) return s; } catch (Exception ignored) {}
        return String.format("%,d", v).replace(',', '.');
    }
    static String urlEnc(String s) { try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; } }
    static Map<String, String> parseParams(String s) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (s == null) return m;
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            try {
                if (eq < 0) m.put(URLDecoder.decode(pair, "UTF-8"), "");
                else m.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {}
        }
        return m;
    }
    // multi-value params for items table
    static Map<String, List<String>> parseMulti(String s) {
        Map<String, List<String>> m = new LinkedHashMap<String, List<String>>();
        if (s == null) return m;
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            try {
                String k = eq < 0 ? URLDecoder.decode(pair, "UTF-8") : URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                List<String> l = m.get(k); if (l == null) { l = new ArrayList<String>(); m.put(k, l); } l.add(v);
            } catch (Exception ignored) {}
        }
        return m;
    }
    private String newToken() { byte[] b = new byte[32]; rnd.nextBytes(b); StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }
    private void purge() { long now = System.currentTimeMillis(); synchronized (sessions) { Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator(); while (it.hasNext()) { if (it.next().getValue().expiresAt < now) it.remove(); } } }
    private Session sessionOf(HttpExchange ex) {
        purge();
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("AdminSession")) {
                Session s = sessions.get(kv[1].trim());
                if (s != null && s.expiresAt >= System.currentTimeMillis()) return s;
                return null;
            }
        }
        return null;
    }
    private String tokenOf(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) { String[] kv = part.trim().split("=", 2); if (kv.length == 2 && kv[0].equals("AdminSession")) return kv[1].trim(); }
        return null;
    }

    static List<String> readLines(File f, int tail) {
        List<String> all = new ArrayList<String>();
        if (f == null || !f.exists()) return all;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String l; while ((l = br.readLine()) != null) all.add(l);
        } catch (Exception ignored) {} finally { if (br != null) try { br.close(); } catch (Exception ignored) {} }
        if (tail > 0 && all.size() > tail) return new ArrayList<String>(all.subList(all.size() - tail, all.size()));
        return all;
    }
    static void writeAtomic(File dst, String content) throws IOException {
        File dir = dst.getParentFile(); if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(dst.getAbsolutePath() + ".tmp");
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp, false), "UTF-8"));
        bw.write(content); bw.close();
        if (dst.exists() && !dst.delete()) { File old = new File(dst.getAbsolutePath() + ".old"); if (old.exists()) old.delete(); dst.renameTo(old); }
        if (!tmp.renameTo(dst)) {
            InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(dst, false);
            byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); in.close(); out.flush(); out.close(); tmp.delete();
        }
        File old = new File(dst.getAbsolutePath() + ".old"); if (old.exists()) old.delete();
    }
    static void backup(File src) throws IOException {
        if (!src.exists()) return;
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File bak = new File(src.getAbsolutePath() + ".bak-" + ts);
        int extra = 0; while (bak.exists()) { extra++; bak = new File(src.getAbsolutePath() + ".bak-" + ts + "-" + extra); }
        InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(bak, false);
        byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); in.close(); out.flush(); out.close();
    }
    static boolean safeName(String s) { return s != null && s.matches("[A-Za-z0-9._-]+") && !s.contains(".."); }

    private class Handler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try { route(ex); } catch (Exception e) {
                log("Admin handler error: " + e);
                try { sendHtml(ex, 500, page("Lỗi", "<div class=\"card\"><p>" + esc(String.valueOf(e.getMessage())) + "</p></div>", null, null)); } catch (Exception ignored) {}
            }
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Map<String, String> q = parseParams(ex.getRequestURI().getRawQuery());
        String method = ex.getRequestMethod();
        String body = "";
        if ("POST".equalsIgnoreCase(method)) {
            InputStream is = ex.getRequestBody(); ByteArrayOutputStream baos = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n); body = new String(baos.toByteArray(), "UTF-8");
        }
        Map<String, String> form = parseParams(body);

        if (path.equals("/") ) { redirect(ex, "/admin"); return; }
        if (path.equals("/admin/login")) {
            String ip = clientIp(ex);
            if ("GET".equalsIgnoreCase(method)) {
                long rem = lockRemaining(ip);
                sendHtml(ex, 200, loginPage(rem > 0 ? lockMsg(rem) : q.get("err"))); return;
            }
            if ("POST".equalsIgnoreCase(method)) {
                int left = reserveAttempt(ip);
                if (left == -2) { log("ADMIN_LOGIN_BUSY: bang chong do day, tu choi ip=" + ip); redirect(ex, "/admin/login?err=" + urlEnc("Hệ thống đang tạm từ chối đăng nhập do có quá nhiều lần thử sai. Thử lại sau ít phút.")); return; }
                if (left < 0) { redirect(ex, "/admin/login"); return; }
                String pw = form.get("password"); if (pw == null) pw = "";
                boolean ok;
                try { ok = MessageDigest.isEqual(pw.getBytes("UTF-8"), adminPass.getBytes("UTF-8")); } catch (Exception e) { ok = false; }
                if (!ok) {
                    try { if (store != null) store.ledger("ADMIN_LOGIN_FAIL", "ip=" + ip); } catch (Exception ignored) {}
                    log("ADMIN_LOGIN_FAIL ip=" + ip);
                    if (left == 0) {
                        try { if (store != null) store.ledger("ADMIN_LOGIN_LOCKED", "ip=" + ip + " minutes=" + (LOCK_MS / 60000)); } catch (Exception ignored) {}
                        log("ADMIN_LOGIN_LOCKED ip=" + ip + " trong " + (LOCK_MS / 60000) + " phut");
                        redirect(ex, "/admin/login"); return;
                    }
                    redirect(ex, "/admin/login?err=" + urlEnc("Sai mật khẩu. Còn " + left + " lần thử trước khi bị khóa " + (LOCK_MS / 60000) + " phút.")); return;
                }
                clearFails(ip);
                purge();
                String token = newToken(); Session s = new Session(); s.csrf = newToken(); s.expiresAt = System.currentTimeMillis() + 8L*3600*1000;
                sessions.put(token, s);
                ex.getResponseHeaders().add("Set-Cookie", "AdminSession=" + token + "; HttpOnly; Path=/; Max-Age=28800; SameSite=Lax" + (https ? "; Secure" : ""));
                redirect(ex, "/admin"); return;
            }
            return;
        }
        // everything below requires auth
        Session sess = sessionOf(ex);
        if (sess == null) { redirect(ex, "/admin/login"); return; }
        if ("POST".equalsIgnoreCase(method)) {
            String csrf = form.get("csrf");
            if (csrf == null || !MessageDigest.isEqual(csrf.getBytes("UTF-8"), sess.csrf.getBytes("UTF-8"))) {
                sendHtml(ex, 403, page("403", "<div class=\"card\"><p>Phiên làm việc hết hạn. Vui lòng tải lại trang và thử lại.</p></div>", sess, null)); return;
            }
        }
        String flash = q.get("flash"); String err = q.get("err");

        if (path.equals("/admin/logout") && "POST".equalsIgnoreCase(method)) {
            String t = tokenOf(ex); if (t != null) sessions.remove(t);
            ex.getResponseHeaders().add("Set-Cookie", "AdminSession=; HttpOnly; Path=/; Max-Age=0" + (https ? "; Secure" : ""));
            redirect(ex, "/admin/login"); return;
        } else if (path.equals("/admin")) { sendHtml(ex, 200, dashboard(sess, flash, err)); }
        else if (path.equals("/admin/wallets")) { sendHtml(ex, 200, walletsPage(sess, q.get("q"), flash, err)); }
        else if (path.equals("/admin/wallet")) { String r = walletDetail(ex, sess, q, flash, err); if (r != null) sendHtml(ex, 200, r); }
        else if (path.equals("/admin/wallet/adjust") && "POST".equalsIgnoreCase(method)) { doAdjust(ex, form); }
        else if (path.equals("/admin/wallet/unlink") && "POST".equalsIgnoreCase(method)) { doUnlink(ex, form); }
        else if (path.equals("/admin/bots")) { sendHtml(ex, 200, botsPage(sess, q, flash, err)); }
        else if (path.equals("/admin/bots/save") && "POST".equalsIgnoreCase(method)) { doBotSave(ex, form); }
        else if (path.equals("/admin/bots/delete") && "POST".equalsIgnoreCase(method)) { doBotDelete(ex, form); }
        else if (path.equals("/admin/bots/power") && "POST".equalsIgnoreCase(method)) { doPower(ex, form); }
        else if (path.equals("/admin/items")) { sendHtml(ex, 200, itemsPage(sess, q, flash, err)); }
        else if (path.equals("/admin/items/save") && "POST".equalsIgnoreCase(method)) { doItemsSave(ex, body); }
        else if (path.equals("/admin/items/create") && "POST".equalsIgnoreCase(method)) { doItemsCreate(ex, form); }
        else if (path.equals("/admin/items/delete") && "POST".equalsIgnoreCase(method)) { doItemsDelete(ex, form); }
        else if (path.equals("/admin/logs")) { sendHtml(ex, 200, logsPage(sess, flash, err)); }
        else { sendHtml(ex, 404, page("404", "<div class=\"card\"><p>Không tìm thấy trang.</p></div>", sess, flash)); }
    }

    private void sendHtml(HttpExchange ex, int code, String html) throws IOException {
        byte[] b = html.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(code, b.length);
        OutputStream os = ex.getResponseBody(); os.write(b); os.close();
    }
    private void redirect(HttpExchange ex, String loc) throws IOException {
        ex.getResponseHeaders().set("Location", loc);
        ex.sendResponseHeaders(302, -1); try { ex.getResponseBody().close(); } catch (Exception ignored) {}
    }
    private static final String CSS =
        ":root{--bg:#0F1419;--surface:#1A2230;--raised:#232E40;--border:#2E3B52;--text:#E8EDF3;--muted:#9AA7BD;--primary:#22C55E;--primary-hover:#16A34A;--danger:#EF4444;--warning:#F59E0B;--info:#38BDF8}"
        + "*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}"
        + "body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,\"Segoe UI\",Roboto,\"Helvetica Neue\",Arial,sans-serif;font-variant-numeric:tabular-nums;line-height:1.5}"
        + "a{color:var(--info);text-decoration:none}a:hover{text-decoration:underline}"
        + ".topbar{position:sticky;top:0;z-index:20;background:rgba(26,34,48,.96);border-bottom:1px solid var(--border);backdrop-filter:blur(6px)}"
        + ".topbar-in{max-width:1080px;margin:0 auto;padding:0 16px;min-height:56px;display:flex;align-items:center;gap:16px;flex-wrap:wrap}"
        + ".brand{font-weight:700;font-size:17px;color:var(--text);white-space:nowrap}.brand:hover{text-decoration:none}"
        + "nav.main{display:flex;gap:4px;flex-wrap:wrap;flex:1}"
        + "nav.main a{color:var(--muted);padding:8px 12px;border-radius:6px;font-size:14px}nav.main a:hover{color:var(--text);text-decoration:none;background:var(--raised)}"
        + "nav.main a.active{color:var(--text);background:var(--raised);box-shadow:inset 0 -2px 0 var(--primary)}"
        + ".container{max-width:1080px;margin:0 auto;padding:24px 16px 32px}"
        + "h1{font-size:22px;margin:0 0 16px}h2{font-size:18px;margin:24px 0 12px}h3{font-size:16px;margin:20px 0 10px}"
        + ".card{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:16px;margin:0 0 16px;box-shadow:0 2px 8px rgba(0,0,0,.25)}"
        + ".card.danger-zone{border-color:rgba(239,68,68,.55);background:rgba(239,68,68,.05)}"
        + ".banner{border-radius:10px;padding:12px 16px;margin:0 0 16px;border:1px solid var(--border);border-left:4px solid var(--primary);background:rgba(34,197,94,.12)}"
        + ".banner.err{border-left-color:var(--danger);background:rgba(239,68,68,.12)}"
        + ".banner.warn{border-left-color:var(--warning);background:rgba(245,158,11,.12)}"
        + ".stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:0 0 16px}"
        + ".stat{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:16px;box-shadow:0 2px 8px rgba(0,0,0,.25)}"
        + ".stat .label{color:var(--muted);font-size:13px;margin:0 0 4px}.stat .value{font-size:22px;font-weight:700}"
        + ".muted{color:var(--muted);font-size:13px}.small{font-size:13px}"
        + ".table-wrap{overflow-x:auto;border:1px solid var(--border);border-radius:10px;background:var(--surface)}"
        + "table{border-collapse:collapse;width:100%;font-size:14px}th,td{padding:10px 12px;text-align:left;border-bottom:1px solid var(--border);vertical-align:top}"
        + "thead th{background:var(--raised);color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em;white-space:nowrap}"
        + ".sticky-head thead th{position:sticky;top:0;z-index:2}"
        + "td.num,th.num{text-align:right;white-space:nowrap}.avail{font-weight:700}"
        + ".badge{display:inline-block;padding:2px 10px;border-radius:999px;font-size:12px;font-weight:600;white-space:nowrap}"
        + ".badge.info{background:rgba(56,189,248,.15);color:var(--info)}.badge.primary{background:rgba(34,197,94,.15);color:var(--primary)}.badge.muted{background:rgba(154,167,189,.15);color:var(--muted)}"
        + "button,.btn{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:8px 16px;border-radius:10px;border:1px solid var(--border);background:var(--raised);color:var(--text);font-size:14px;cursor:pointer;font-family:inherit}"
        + "button:hover,.btn:hover{background:#2B3850;text-decoration:none}button:disabled{opacity:.55;cursor:not-allowed}"
        + "button.primary,.btn.primary{background:var(--primary);border-color:var(--primary);color:#06130B;font-weight:700}button.primary:hover,.btn.primary:hover{background:var(--primary-hover)}"
        + "button.danger{background:rgba(239,68,68,.14);border-color:rgba(239,68,68,.5);color:#FCA5A5}button.danger:hover{background:rgba(239,68,68,.22)}"
        + "button.ghost,.btn.ghost{background:transparent}button.ghost:hover,.btn.ghost:hover{background:var(--raised)}"
        + "input,select,textarea{background:#0B1118;border:1px solid var(--border);color:var(--text);border-radius:6px;padding:10px 12px;font-size:16px;font-family:inherit;min-height:44px}"
        + "input:focus,select:focus,textarea:focus,button:focus-visible,a:focus-visible{outline:2px solid var(--info);outline-offset:2px}"
        + "label{display:block;font-size:13px;color:var(--muted);margin:0 0 12px}label input,label select{margin-top:4px;width:100%}"
        + "fieldset{border:1px solid var(--border);border-radius:10px;padding:16px;margin:0 0 16px}legend{padding:0 8px;color:var(--text);font-weight:600;font-size:14px}"
        + ".row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.row input{flex:1;min-width:160px}"
        + ".radio-row{display:flex;gap:12px;flex-wrap:wrap;margin:8px 0 12px}.radio-card{display:flex;align-items:center;gap:8px;background:#0B1118;border:1px solid var(--border);border-radius:10px;padding:10px 14px;color:var(--text);font-size:14px;cursor:pointer}.radio-card input{min-height:auto;width:auto}"
        + "pre.log{background:#0B1118;border:1px solid var(--border);border-radius:10px;padding:12px;font-family:ui-monospace,Consolas,Menlo,monospace;font-size:12.5px;line-height:1.55;max-height:60vh;overflow:auto;white-space:pre-wrap;word-break:break-word}"
        + ".chips{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 16px}.chip{display:inline-block;padding:8px 14px;border-radius:999px;border:1px solid var(--border);background:var(--surface);color:var(--muted);font-size:13px}.chip:hover{text-decoration:none;color:var(--text)}.chip.active{background:rgba(34,197,94,.15);border-color:var(--primary);color:var(--text);font-weight:600}"
        + ".login-wrap{max-width:380px;margin:48px auto 0}.login-wrap .card{padding:24px}"
        + ".footer{max-width:1080px;margin:0 auto;padding:0 16px 24px;color:var(--muted);font-size:12px}"
        + ".inline-form{display:inline}.empty{padding:24px;text-align:center;color:var(--muted)}"
        + "@media(max-width:640px){.topbar-in{gap:8px;padding:8px 12px}nav.main a{padding:6px 8px;font-size:13px}.container{padding:16px 12px 24px}}";
    private static final String JS =
        "<script>"
        + "function togglePw(id){var i=document.getElementById(id);if(i){i.type=i.type==='password'?'text':'password';}}"
        + "function fmtN(v){return (''+v).replace(/\\B(?=(\\d{3})+(?!\\d))/g,'.');}"
        + "function adjUpdate(){var a=document.querySelector('[data-amount]');var s=document.getElementById('adjSummary');if(!a)return;var v=a.value.replace(/[^0-9]/g,'');var d=document.querySelector('input[name=\"direction\"]:checked');var credit=!d||d.value!=='debit';var codeEl=document.getElementById('adjCode');var code=codeEl?codeEl.value:'';var btn=document.getElementById('adjSubmit');if(btn){btn.textContent=credit?'Cộng tiền':'Trừ tiền';}if(s){s.textContent=v?('Bạn sắp '+(credit?'CỘNG ':'TRỪ ')+fmtN(v)+' \\u20AB '+(credit?'vào ví ':'khỏi ví ')+code+'.'):'';}}"
        + "document.addEventListener('input',function(e){var t=e.target;if(t&&t.hasAttribute&&t.hasAttribute('data-amount')){var v=t.value.replace(/[^0-9]/g,'');t.value=v?fmtN(v):'';adjUpdate();}});"
        + "document.addEventListener('change',function(e){if(e.target&&e.target.name==='direction'){adjUpdate();}});"
        + "document.addEventListener('submit',function(e){var f=e.target;var c=f.getAttribute('data-confirm');if(c&&!window.confirm(c)){e.preventDefault();return;}if(f.id==='adjForm'){var d=f.querySelector('input[name=\"direction\"]:checked');var a=f.querySelector('[data-amount]');var codeEl=document.getElementById('adjCode');if(d&&d.value==='debit'&&a){var v=a.value.replace(/[^0-9]/g,'');if(!window.confirm('Bạn sắp TRỪ '+fmtN(v)+' \\u20AB khỏi ví '+(codeEl?codeEl.value:'')+'. Tiếp tục?')){e.preventDefault();return;}}}var amt=f.querySelector('[data-amount]');if(amt){amt.value=amt.value.replace(/[^0-9]/g,'');}var b=f.querySelector('button[type=\"submit\"],button:not([type])');if(b){setTimeout(function(){b.disabled=true;},0);}});"
        + "function addItemRow(){var t=document.getElementById('itemsTable');if(!t)return;var rows=t.querySelectorAll('tbody tr');if(!rows.length)return;var c=rows[rows.length-1].cloneNode(true);var ins=c.querySelectorAll('input');for(var i=0;i<ins.length;i++){if(ins[i].type==='checkbox'){ins[i].checked=false;}else{ins[i].value='';}}t.querySelector('tbody').appendChild(c);var cnt=document.getElementById('rowCount');if(cnt){cnt.textContent=t.querySelectorAll('tbody tr').length;}}"
        + "adjUpdate();"
        + "</script>";
    private String navLink(String href, String label, String active, String key) {
        return "<a href=\"" + href + "\"" + (key.equals(active) ? " class=\"active\"" : "") + ">" + label + "</a>";
    }
    private String page(String title, String body, Session sess, String flash) { return page(title, body, sess, flash, ""); }
    private String page(String title, String body, Session sess, String flash, String active) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>").append(esc(title)).append(" — Quản trị shop</title><style>").append(CSS).append("</style></head><body>");
        if (sess != null) {
            sb.append("<header class=\"topbar\"><div class=\"topbar-in\"><a class=\"brand\" href=\"/admin\">Quản trị shop</a><nav class=\"main\">");
            sb.append(navLink("/admin", "Tổng quan", active, "dashboard"));
            sb.append(navLink("/admin/wallets", "Ví", active, "wallets"));
            sb.append(navLink("/admin/bots", "Bot", active, "bots"));
            sb.append(navLink("/admin/items", "Giá", active, "items"));
            sb.append(navLink("/admin/logs", "Nhật ký", active, "logs"));
            sb.append("</nav><form method=\"post\" action=\"/admin/logout\" class=\"inline-form\">").append(csrf(sess)).append("<button type=\"submit\" class=\"ghost\">Đăng xuất</button></form></div></header>");
        }
        sb.append("<main class=\"container\">");
        if (flash != null) sb.append("<div class=\"banner\">").append(esc(flash)).append("</div>");
        sb.append(body).append("</main>");
        if (sess != null) sb.append("<footer class=\"footer\">Chỉ mở cổng này cho IP của người vận hành (firewall) hoặc qua HTTPS reverse proxy. Phiên đăng nhập hết hạn sau 8 giờ.</footer>");
        sb.append(JS).append("</body></html>");
        return sb.toString();
    }
    private String loginPage(String err) {
        String body = "<div class=\"login-wrap\"><div class=\"card\"><h1>Đăng nhập quản trị</h1>"
            + (err != null ? "<div class=\"banner err\">" + esc(err) + "</div>" : "")
            + "<form method=\"post\" action=\"/admin/login\"><label>Mật khẩu<input id=\"pwField\" type=\"password\" name=\"password\" autocomplete=\"current-password\"></label>"
            + "<div class=\"row\" style=\"margin-bottom:12px\"><button type=\"button\" class=\"ghost\" onclick=\"togglePw('pwField')\">Hiện / ẩn mật khẩu</button></div>"
            + "<button type=\"submit\" class=\"primary\" style=\"width:100%\">Đăng nhập</button></form>"
            + "<p class=\"muted\">Nhập sai mật khẩu " + MAX_FAILS + " lần liên tiếp sẽ bị khóa " + (LOCK_MS / 60000) + " phút. Phiên đăng nhập hết hạn sau 8 giờ.</p></div></div>";
        return page("Đăng nhập", body, null, null);
    }
    private String msgs(String flash, String err) {
        return (flash != null ? "<div class=\"banner\">" + esc(flash) + "</div>" : "") + (err != null ? "<div class=\"banner err\">" + esc(err) + "</div>" : "");
    }
    private String csrf(Session s) { return "<input type=\"hidden\" name=\"csrf\" value=\"" + esc(s.csrf) + "\">"; }
    static String money(long v) { return fmt(v) + " ₫"; }
    private String botBadge(BotLine bl) {
        BanSeller bs = null;
        try { bs = liveSeller(bl); } catch (Exception ignored) {}
        if (bs != null) {
            try { if (bs.state != BanSeller.STATE_IDLE) return "<span class=\"badge primary\">◐ Đang bán</span>"; } catch (Exception ignored) {}
        }
        String rt = botRuntime(bl);
        if (rt != null && rt.startsWith("online")) return "<span class=\"badge info\">● Đang chạy</span>";
        if (rt != null && rt.startsWith("connecting")) return "<span class=\"badge info\">◌ Đang đăng nhập</span>";
        if (rt != null && rt.startsWith("offline")) return "<span class=\"badge muted\">Đã tắt</span>";
        if (rt != null && rt.contains("no-seller")) return "<span class=\"badge muted\">Chưa có seller</span>";
        return "<span class=\"badge muted\">Chưa chạy</span>";
    }

    // ---------- dashboard ----------
    private String dashboard(Session sess, String flash, String err) {
        List<WalletStore.Wallet> ws = store != null ? store.listWallets() : new ArrayList<WalletStore.Wallet>();
        long total = 0; for (WalletStore.Wallet w : ws) total += w.balance;
        long held = store != null ? store.totalHeld() : 0;
        long todayCredit = 0;
        String wd = workDir();
        if (wd != null) {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            for (String l : readLines(new File(wd + "\\logs\\wallet_ledger.log"), 0)) {
                if (!l.startsWith(today)) continue;
                String[] parts = l.split(" \\| ");
                if (parts.length < 3 || !parts[1].trim().equals("CREDIT")) continue;
                // detail contains amount=
                int i = l.indexOf("amount="); if (i >= 0) { int j = i + 7; int k = j; while (k < l.length() && (Character.isDigit(l.charAt(k)) || l.charAt(k) == '-')) k++; try { todayCredit += Long.parseLong(l.substring(j, k)); } catch (Exception ignored) {} }
            }
        }
        boolean hook = false; try { hook = AtmPaymentService.getInstance().isRunning(); } catch (Exception ignored) {}
        File cf = configFile(); List<BotLine> allBots = cf == null ? new ArrayList<BotLine>() : parseBots(readLines(cf, 0));
        int online = 0; for (BotLine bl : allBots) { String rt = botRuntime(bl); if (rt != null && rt.startsWith("online")) online++; }
        StringBuilder b = new StringBuilder(msgs(flash, err));
        b.append("<h1>Tổng quan</h1><div class=\"stats\">");
        b.append("<div class=\"stat\"><p class=\"label\">Số ví</p><div class=\"value\">").append(ws.size()).append("</div></div>");
        b.append("<div class=\"stat\"><p class=\"label\">Tổng số dư</p><div class=\"value\">").append(esc(money(total))).append("</div></div>");
        b.append("<div class=\"stat\"><p class=\"label\">Đang giữ (hold)</p><div class=\"value\">").append(esc(money(held))).append("</div></div>");
        b.append("<div class=\"stat\"><p class=\"label\">Nạp hôm nay</p><div class=\"value\">").append(esc(money(todayCredit))).append("</div></div>");
        b.append("</div>");
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Trạng thái hệ thống</h3>");
        b.append("<p>Webhook SePay: ").append(hook ? "<span class=\"badge primary\">Đang chạy</span>" : "<span class=\"badge muted\">Đang dừng</span>").append("</p>");
        b.append("<p>Bot đang chạy: <b>").append(online).append(" / ").append(allBots.size()).append("</b></p></div>");
        b.append("<h2>Bot bán</h2>").append(botTableHtml(true));
        b.append("<p><a class=\"btn ghost\" href=\"/admin/bots\">Xem tất cả bot</a></p>");
        return page("Tổng quan", b.toString(), sess, null, "dashboard");
    }

    // ---------- bots parsing ----------
    static final class BotLine { int index; String raw, sv, tk, mk, nv, map, pos, item, chat, nick; }
    private List<BotLine> parseBots(List<String> lines) {
        List<BotLine> out = new ArrayList<BotLine>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            String tk = WalletService.tagValue(l, "TK");
            if (tk == null) continue;
            BotLine b = new BotLine(); b.index = i; b.raw = l;
            b.sv = nz(WalletService.tagValue(l, "SV")); b.tk = tk; b.mk = nz(WalletService.tagValue(l, "MK")); b.nv = nz(WalletService.tagValue(l, "NV"));
            b.map = nz(WalletService.tagValue(l, "MAP")); b.pos = nz(WalletService.tagValue(l, "POS")); b.item = nz(WalletService.tagValue(l, "ITEM")); b.chat = nz(WalletService.tagValue(l, "CHAT")); b.nick = nz(WalletService.tagValue(l, "NICK"));
            out.add(b);
        }
        return out;
    }
    static String nz(String s) { return s == null ? "" : s; }
    private File configFile() { String wd = workDir(); return wd == null ? null : new File(wd + "\\banvatpham.txt"); }

    // Server match is only established when SV parses and resolves to cd's server object.
    // If it cannot be determined, callers must not join runtime status.
    private boolean serverMatches(BotLine bl, Class_cd cd) {
        try {
            if (bl.sv == null || bl.sv.trim().isEmpty()) return false;
            int sv = Integer.parseInt(bl.sv.trim());
            Object srv = main.Midlet.b.a(Integer.valueOf(sv));
            return srv != null && srv == cd.d;
        } catch (Exception e) { return false; }
    }
    static final java.util.Set<String> KNOWN_TAGS = new HashSet<String>(Arrays.asList("SV","TK","MK","NV","MAP","POS","ITEM","CHAT","NICK"));
    // Return verbatim [TAG]...[/TAG] segments for tags outside the known nine, in original order.
    static String unknownTags(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf('[', i);
            if (open < 0) break;
            int closeBracket = raw.indexOf(']', open);
            if (closeBracket < 0) break;
            String tag = raw.substring(open + 1, closeBracket);
            if (tag.isEmpty() || tag.startsWith("/") || !tag.matches("[A-Za-z0-9_]+")) { i = closeBracket + 1; continue; }
            String closeTag = "[/" + tag + "]";
            int close = raw.indexOf(closeTag, closeBracket + 1);
            if (close < 0) { i = closeBracket + 1; continue; }
            if (!KNOWN_TAGS.contains(tag.toUpperCase())) out.append(raw.substring(open, close + closeTag.length()));
            i = close + closeTag.length();
        }
        return out.toString();
    }
    private Class_cd findCd(BotLine bl) {
        try {
            Object vec = Class_cu.a().d;
            if (vec instanceof java.util.Vector) {
                java.util.Vector v = (java.util.Vector) vec;
                for (int i = 0; i < v.size(); i++) {
                    Object o = v.elementAt(i); if (!(o instanceof Class_cd)) continue;
                    Class_cd cd = (Class_cd) o;
                    if (cd.b == null || !cd.b.equalsIgnoreCase(bl.tk)) continue;
                    if (!serverMatches(bl, cd)) continue;
                    return cd;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
    private boolean threadsAlive(Class_cd cd) {
        try {
            if (cd == null || !cd.u) return false;
            boolean sAlive = cd.s != null && cd.s.isAlive();
            boolean tAlive = cd.t != null && cd.t.isAlive();
            return sAlive || tAlive;
        } catch (Exception e) { return false; }
    }
    private BanSeller liveSeller(BotLine bl) {
        try {
            Class_cd cd = findCd(bl);
            if (cd != null && cd.p != null) return cd.p.banSeller;
        } catch (Exception ignored) {}
        return null;
    }
    private String botRuntime(BotLine bl) {
        try {
            Object vec = Class_cu.a().d;
            if (vec instanceof java.util.Vector) {
                java.util.Vector v = (java.util.Vector) vec;
                for (int i = 0; i < v.size(); i++) {
                    Object o = v.elementAt(i); if (!(o instanceof Class_cd)) continue;
                    Class_cd cd = (Class_cd) o;
                    if (cd.b == null || !cd.b.equalsIgnoreCase(bl.tk)) continue;
                    if (!serverMatches(bl, cd)) continue;
                    BanSeller bs = cd.p != null ? cd.p.banSeller : null;
                    String st = bs == null ? "no-seller" : ("state=" + bs.state);
                    String stock = bs == null ? "-" : bs.getStockCombined();
                    if (!threadsAlive(cd)) return "offline (đã tắt — bấm Bật để chạy)";
                    if (cd.h == null || cd.h.b != 1) return "connecting " + st + " stock: " + stock;
                    return "online w=" + cd.w + " " + st + " stock: " + stock;
                }
            }
        } catch (Exception ignored) {}
        return "not running (requires restart)";
    }
    private String botTableHtml() { return botTableHtml(false); }
    private String botTableHtml(boolean compact) {
        File f = configFile(); if (f == null) return "<div class=\"card empty\">Không có thư mục làm việc.</div>";
        List<BotLine> bots = parseBots(readLines(f, 0));
        if (bots.isEmpty()) return "<div class=\"card empty\">Chưa có bot nào.</div>";
        StringBuilder b = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr>");
        if (compact) b.append("<th>SV</th><th>TK</th><th>Trạng thái</th><th>Tồn kho</th>");
        else b.append("<th>SV</th><th>TK</th><th>NV</th><th>MAP</th><th>POS</th><th>ITEM</th><th>Trạng thái</th><th>Nguồn</th><th>Sửa</th><th>Xóa</th>");
        b.append("</tr></thead><tbody>");
        // need session csrf for delete forms in full mode; handled by caller via botsPage separate actions if no session here
        for (BotLine bl : bots) {
            String rt = botRuntime(bl);
            String stock = "-";
            BanSeller bs = liveSeller(bl);
            if (bs != null) { try { stock = bs.getStockCombined(); } catch (Exception ignored) {} }
            else if (rt != null) { int si = rt.indexOf("stock: "); if (si >= 0) stock = rt.substring(si + 7); }
            b.append("<tr><td class=\"num\">").append(esc(bl.sv)).append("</td><td>").append(esc(bl.tk)).append("</td>");
            if (compact) {
                b.append("<td>").append(botBadge(bl)).append("<div class=\"muted\">").append(esc(rt)).append("</div></td><td>").append(esc(stock)).append("</td>");
            } else {
                b.append("<td>").append(esc(bl.nv)).append("</td><td>").append(esc(bl.map)).append("</td><td>").append(esc(bl.pos)).append("</td><td>").append(esc(bl.item)).append("</td>");
                b.append("<td>").append(botBadge(bl)).append("<div class=\"muted\">").append(esc(rt)).append("</div></td>");
                b.append("<td>").append(botPowerForm(bl, rt)).append("</td>");
                b.append("<td><a class=\"btn ghost\" href=\"/admin/bots?edit=").append(bl.index).append("\">Sửa</a></td>");
                b.append("<td>").append(botDeleteForm(bl)).append("</td>");
            }
            b.append("</tr>");
        }
        return b.append("</tbody></table></div>").toString();
    }
    // session for bot delete forms (set by botsPage before rendering table)
    private Session botTableSession;
    private String botDeleteForm(BotLine bl) {
        if (botTableSession == null) return "";
        String confirm = "Xóa bot SV " + bl.sv + " / TK " + bl.tk + "?";
        return "<form class=\"inline-form\" method=\"post\" action=\"/admin/bots/delete\" data-confirm=\"" + esc(confirm) + "\">" + csrf(botTableSession) + "<input type=\"hidden\" name=\"index\" value=\"" + bl.index + "\"><button type=\"submit\" class=\"danger\">Xóa</button></form>";
    }
    private String botPowerForm(BotLine bl, String rt) {
        if (botTableSession == null) return "";
        boolean isOff = rt != null && rt.startsWith("offline");
        String action = isOff ? "on" : "off";
        String label = isOff ? "Bật" : "Tắt";
        String cls = isOff ? "primary" : "danger";
        String confirmAttr = "";
        if (!isOff) confirmAttr = " data-confirm=\"" + esc("Tắt bot SV " + bl.sv + " / TK " + bl.tk + "? (giao dịch đang dở sẽ bị hủy)") + "\"";
        return "<form class=\"inline-form\" method=\"post\" action=\"/admin/bots/power\"" + confirmAttr + ">" + csrf(botTableSession) + "<input type=\"hidden\" name=\"index\" value=\"" + bl.index + "\"><input type=\"hidden\" name=\"action\" value=\"" + action + "\"><button type=\"submit\" class=\"" + cls + "\">" + label + "</button></form>";
    }

    private String botsPage(Session sess, Map<String,String> q, String flash, String err) {
        File f = configFile(); List<BotLine> bots = f == null ? new ArrayList<BotLine>() : parseBots(readLines(f, 0));
        StringBuilder b = new StringBuilder(msgs(flash, err));
        b.append("<h1>Bot bán</h1>");
        b.append("<div class=\"banner warn\">Lưu ý: Thay đổi bot chỉ có hiệu lực sau khi restart tiến trình bot.</div>");
        b.append("<p><a class=\"btn primary\" href=\"/admin/bots?add=1\">Thêm bot mới</a></p>");
        botTableSession = sess;
        try { b.append(botTableHtml(false)); } finally { botTableSession = null; }
        BotLine edit = null;
        if (q.get("edit") != null) { try { int idx = Integer.parseInt(q.get("edit")); for (BotLine bl : bots) if (bl.index == idx) edit = bl; } catch (Exception ignored) {} }
        boolean addMode = edit == null && q.get("add") != null;
        if (edit == null && !addMode) return page("Bot bán", b.toString(), sess, null, "bots");
        b.append("<p><a class=\"btn ghost\" href=\"/admin/bots\">← Quay lại danh sách</a></p>");
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">").append(edit == null ? "Thêm bot" : "Sửa bot").append("</h3><form method=\"post\" action=\"/admin/bots/save\">").append(csrf(sess));
        b.append("<input type=\"hidden\" name=\"origIndex\" value=\"").append(edit == null ? -1 : edit.index).append("\">");
        b.append("<input type=\"hidden\" name=\"origSv\" value=\"").append(esc(edit == null ? "" : edit.sv)).append("\"><input type=\"hidden\" name=\"origTk\" value=\"").append(esc(edit == null ? "" : edit.tk)).append("\">");
        final BotLine editF = edit;
        java.util.function.Function<String,String> val = (k) -> {
            if (editF == null) return "";
            if (k.equals("sv")) return editF.sv; if (k.equals("tk")) return editF.tk; if (k.equals("mk")) return editF.mk; if (k.equals("nv")) return editF.nv; if (k.equals("map")) return editF.map; if (k.equals("pos")) return editF.pos; if (k.equals("item")) return editF.item; if (k.equals("chat")) return editF.chat; if (k.equals("nick")) return editF.nick; return "";
        };
        b.append("<fieldset><legend>Tài khoản</legend>");
        b.append("<label>SV<input name=\"sv\" value=\"").append(esc(val.apply("sv"))).append("\"></label>");
        b.append("<label>TK<input name=\"tk\" value=\"").append(esc(val.apply("tk"))).append("\"></label>");
        b.append("<label>MK<div class=\"row\"><input id=\"mkField\" type=\"password\" name=\"mk\" value=\"").append(esc(val.apply("mk"))).append("\"><button type=\"button\" class=\"ghost\" onclick=\"togglePw('mkField')\">Hiện / ẩn</button></div></label>");
        b.append("<label>NV<input name=\"nv\" value=\"").append(esc(val.apply("nv"))).append("\"></label></fieldset>");
        b.append("<fieldset><legend>Vị trí bán</legend>");
        b.append("<label>MAP<input name=\"map\" placeholder=\"23:6\" value=\"").append(esc(val.apply("map"))).append("\"></label>");
        b.append("<label>POS<input name=\"pos\" placeholder=\"445:120\" value=\"").append(esc(val.apply("pos"))).append("\"></label></fieldset>");
        b.append("<fieldset><legend>Hàng &amp; chat</legend>");
        b.append("<label>ITEM<input name=\"item\" value=\"").append(esc(val.apply("item"))).append("\"></label>");
        b.append("<label>CHAT<input name=\"chat\" value=\"").append(esc(val.apply("chat"))).append("\"></label>");
        b.append("<label>NICK<input name=\"nick\" value=\"").append(esc(val.apply("nick"))).append("\"></label></fieldset>");
        b.append("<button type=\"submit\" class=\"primary\">Lưu bot</button></form></div>");
        return page("Bot bán", b.toString(), sess, null, "bots");
    }

    private void doBotSave(HttpExchange ex, Map<String,String> form) throws IOException {
        String sv=form.get("sv"), tk=form.get("tk"), mk=form.get("mk"), nv=nz(form.get("nv")), map=form.get("map"), pos=form.get("pos"), item=form.get("item"), chat=nz(form.get("chat")), nick=nz(form.get("nick"));
        int origIndex = -1; try { origIndex = Integer.parseInt(nz(form.get("origIndex"))); } catch (Exception ignored) {}
        String modeSuffix = origIndex >= 0 ? "&edit=" + origIndex : "&add=1";
        String errMsg = validateBot(sv, tk, mk, nv, map, pos, item, chat, nick);
        if (errMsg != null) { redirect(ex, "/admin/bots?err=" + urlEnc(errMsg) + modeSuffix); return; }
        if (!safeName(item.trim())) { redirect(ex, "/admin/bots?err=" + urlEnc("Tên ITEM chưa hợp lệ.") + modeSuffix); return; }
        File f = configFile(); if (f == null) { redirect(ex, "/admin/bots?err=" + urlEnc("Không có thư mục làm việc.") + modeSuffix); return; }
        synchronized (AdminWebServer.class) {
            List<String> lines = readLines(f, 0);
            List<BotLine> bots = parseBots(lines);
            for (BotLine bl : bots) { if (bl.index != origIndex && bl.sv.equals(sv.trim()) && bl.tk.equals(tk.trim())) { redirect(ex, "/admin/bots?err=" + urlEnc("Đã có bot SV " + sv.trim() + " / TK " + tk.trim() + " — hãy Sửa thay vì Thêm mới.") + modeSuffix); return; } }
            StringBuilder line = new StringBuilder();
            line.append("[SV]").append(sv.trim()).append("[/SV][TK]").append(tk.trim()).append("[/TK][MK]").append(mk.trim()).append("[/MK][NV]").append(nv.trim()).append("[/NV][MAP]").append(map.trim()).append("[/MAP][POS]").append(pos.trim()).append("[/POS]");
            if (!nick.trim().isEmpty()) line.append("[NICK]").append(nick.trim()).append("[/NICK]");
            line.append("[ITEM]").append(item.trim()).append("[/ITEM]");
            if (!chat.trim().isEmpty()) line.append("[CHAT]").append(chat.trim()).append("[/CHAT]");
            // Preserve any unrecognized tags already on the edited line verbatim.
            if (origIndex >= 0 && origIndex < lines.size()) line.append(unknownTags(lines.get(origIndex)));
            // Create/validate the items file BEFORE writing banvatpham.txt so a
            // failure here leaves the config file unchanged.
            String wd = workDir(); if (wd == null) { redirect(ex, "/admin/bots?err=" + urlEnc("Không có thư mục làm việc.") + modeSuffix); return; }
            File itemFile = new File(wd + "\\items\\" + item.trim() + ".txt");
            boolean createdItemFile = false;
            try {
                if (!itemFile.exists()) {
                    if (itemFile.getParentFile() != null) itemFile.getParentFile().mkdirs();
                    writeAtomic(itemFile, "# Format: id:don_gia:so_luong_toi_da:ma_vp\r\n");
                    createdItemFile = true;
                }
            } catch (Exception e) { redirect(ex, "/admin/bots?err=" + urlEnc("Không tạo được file hàng (chưa thay đổi cấu hình): " + e.getMessage()) + modeSuffix); return; }
            try {
                backup(f);
                if (origIndex >= 0 && origIndex < lines.size()) lines.set(origIndex, line.toString()); else lines.add(line.toString());
                StringBuilder content = new StringBuilder(); for (String l : lines) content.append(l).append("\r\n");
                writeAtomic(f, content.toString());
            } catch (Exception e) {
                if (createdItemFile) { try { itemFile.delete(); } catch (Exception ignored) {} }
                redirect(ex, "/admin/bots?err=" + urlEnc("Ghi thất bại (chưa thay đổi cấu hình): " + e.getMessage()) + modeSuffix); return;
            }
        }
        redirect(ex, "/admin/bots?flash=" + urlEnc("Đã lưu bot SV " + sv.trim() + " / " + tk.trim() + ". Hiệu lực sau khi restart tiến trình bot."));
    }
    private String validateBot(String sv, String tk, String mk, String nv, String map, String pos, String item, String chat, String nick) {
        if (sv == null || tk == null || mk == null || map == null || pos == null || item == null) return "Thiếu trường bắt buộc.";
        if (sv.trim().isEmpty() || tk.trim().isEmpty() || mk.trim().isEmpty() || map.trim().isEmpty() || pos.trim().isEmpty() || item.trim().isEmpty()) return "SV, TK, MK, MAP, POS, ITEM là bắt buộc.";
        try { Integer.parseInt(sv.trim()); } catch (Exception e) { return "SV phải là số."; }
        if (!map.trim().contains(":")) return "MAP phải có dạng 23:6.";
        if (!pos.trim().contains(":")) return "POS phải có dạng 445:120.";
        // reject tags injection in ALL fields (including NV/CHAT/NICK)
        String[] vals = {sv, tk, mk, nv, map, pos, item, chat, nick};
        for (String v : vals) if (v != null && (v.contains("[") || v.contains("]") || v.contains("\n") || v.contains("\r"))) return "Các trường không được chứa dấu ngoặc vuông hoặc xuống dòng.";
        return null;
    }
    private void doBotDelete(HttpExchange ex, Map<String,String> form) throws IOException {
        int idx = -1; try { idx = Integer.parseInt(nz(form.get("index"))); } catch (Exception ignored) {}
        File f = configFile(); if (f == null || idx < 0) { redirect(ex, "/admin/bots?err=" + urlEnc("Không hợp lệ.")); return; }
        String deletedInfo = "";
        synchronized (AdminWebServer.class) {
            List<String> lines = readLines(f, 0);
            if (idx >= lines.size()) { redirect(ex, "/admin/bots?err=" + urlEnc("Chỉ số không hợp lệ.")); return; }
            for (BotLine bl : parseBots(lines)) if (bl.index == idx) deletedInfo = " SV " + bl.sv + " / " + bl.tk;
            try { backup(f); lines.remove(idx); StringBuilder c = new StringBuilder(); for (String l : lines) c.append(l).append("\r\n"); writeAtomic(f, c.toString()); }
            catch (Exception e) { redirect(ex, "/admin/bots?err=" + urlEnc("Ghi thất bại: " + e.getMessage())); return; }
        }
        redirect(ex, "/admin/bots?flash=" + urlEnc("Đã xóa bot" + deletedInfo + ". Hiệu lực sau khi restart."));
    }
    private void doPower(HttpExchange ex, Map<String,String> form) throws IOException {
        int idx = -1; try { idx = Integer.parseInt(nz(form.get("index"))); } catch (Exception ignored) {}
        String action = nz(form.get("action"));
        File f = configFile();
        BotLine target = null;
        if (f != null && idx >= 0) for (BotLine bl : parseBots(readLines(f, 0))) if (bl.index == idx) target = bl;
        if (target == null || (!action.equals("on") && !action.equals("off"))) { redirect(ex, "/admin/bots?err=" + urlEnc("Không hợp lệ.")); return; }
        synchronized (AdminWebServer.class) {
            Class_cd cd = findCd(target);
            if (cd == null) { redirect(ex, "/admin/bots?err=" + urlEnc("Bot SV " + target.sv + " / " + target.tk + " chưa được load (cần restart tiến trình bot).")); return; }
            BanSeller bs = (cd.p != null) ? cd.p.banSeller : null;
            if (action.equals("on")) {
                if (threadsAlive(cd)) { redirect(ex, "/admin/bots?flash=" + urlEnc("Bot SV " + target.sv + " / " + target.tk + " đang chạy rồi.")); return; }
                try {
                    // Ngat that: Class_bg (cd.e) moi la ket noi socket (close + reset);
                    // Class_be (cd.g) chi la wait/notify, goi nham se khong off.
                    try { if (cd.e != null) cd.e.c(); } catch (Exception ignored) {}
                    cd.u = true;
                    if (cd.s == null || !cd.s.isAlive()) { try { cd.s = new Thread(new Class_ce(cd)); } catch (Exception ignored) {} }
                    if (cd.t == null || !cd.t.isAlive()) { try { cd.t = new Thread(new Class_cf(cd)); } catch (Exception ignored) {} }
                    try { if (cd.s != null && !cd.s.isAlive()) cd.s.start(); } catch (Exception ignored) {}
                    try { if (cd.t != null && !cd.t.isAlive()) cd.t.start(); } catch (Exception ignored) {}
                    try { cd.m.d = 1; cd.m.c = Class_bb.b(); } catch (Exception ignored) {}
                } catch (Exception e) { redirect(ex, "/admin/bots?err=" + urlEnc("Bật thất bại: " + e.getMessage())); return; }
                try { if (store != null) store.ledger("BOT_POWER_ON", "sv=" + target.sv + " tk=" + target.tk); } catch (Exception ignored) {}
                redirect(ex, "/admin/bots?flash=" + urlEnc("Đã BẬT bot SV " + target.sv + " / " + target.tk + ". Đang đăng nhập..."));
            } else {
                boolean midTrade = bs != null && bs.state != BanSeller.STATE_IDLE;
                try {
                    if (midTrade) { try { cd.l.b(); } catch (Exception ignored) {} }
                    if (bs != null) { try { bs.reset(); } catch (Exception ignored) {} bs.withdrawing = false; }
                    cd.u = false;
                    try { Class_bb.b(500L); } catch (Exception ignored) {}
                    try { if (cd.e != null) cd.e.c(); } catch (Exception ignored) {}
                    try { cd.m.d = -1; } catch (Exception ignored) {}
                    try { cd.a("WEB: Admin tat bot, da ngat ket noi", false); } catch (Exception ignored) {}
                } catch (Exception e) { redirect(ex, "/admin/bots?err=" + urlEnc("Tắt thất bại: " + e.getMessage())); return; }
                try { if (store != null) store.ledger("BOT_POWER_OFF", "sv=" + target.sv + " tk=" + target.tk + (midTrade ? " midTrade=1" : "")); } catch (Exception ignored) {}
                redirect(ex, "/admin/bots?flash=" + urlEnc("Đã TẮT bot SV " + target.sv + " / " + target.tk + "." + (midTrade ? " (đang giữa giao dịch — hold đã giải phóng)" : "")));
            }
        }
    }

    // ---------- wallets ----------
    private String walletsPage(Session sess, String query, String flash, String err) {
        List<WalletStore.Wallet> ws = store != null ? store.listWallets() : new ArrayList<WalletStore.Wallet>();
        StringBuilder b = new StringBuilder(msgs(flash, err));
        b.append("<h1>Ví khách hàng</h1>");
        b.append("<div class=\"card\"><form method=\"get\" action=\"/admin/wallets\"><div class=\"row\"><input name=\"q\" placeholder=\"Tìm theo mã ví, nhân vật chính hoặc liên kết...\" value=\"").append(esc(nz(query))).append("\"><button type=\"submit\" class=\"primary\">Tìm</button></div></form></div>");
        String ql = query == null ? "" : query.trim().toLowerCase();
        StringBuilder rows = new StringBuilder(); int count = 0;
        for (WalletStore.Wallet w : ws) {
            if (!ql.isEmpty()) {
                boolean hit = w.code.toLowerCase().contains(ql) || w.primary.toLowerCase().contains(ql);
                if (!hit) for (String s : w.linked) if (s.toLowerCase().contains(ql)) hit = true;
                if (!hit) continue;
            }
            count++;
            long held = 0; if (store != null) for (WalletStore.Hold h : store.holdsOf(w.code)) held += h.amount;
            rows.append("<tr><td><a href=\"/admin/wallet?code=").append(urlEnc(w.code)).append("\">").append(esc(w.code)).append("</a></td><td>").append(esc(w.primary)).append("</td><td>").append(esc(join(w.linked))).append("</td><td class=\"num\">").append(esc(money(w.balance))).append("</td><td class=\"num avail\">").append(esc(money(w.balance - held))).append("</td><td class=\"num\">").append(esc(money(held))).append("</td></tr>");
        }
        b.append("<p class=\"muted\">Tìm thấy ").append(count).append(" ví.</p>");
        if (count == 0) b.append("<div class=\"card empty\">Không tìm thấy ví nào. Thử từ khóa khác.</div>");
        else b.append("<div class=\"table-wrap\"><table><thead><tr><th>Mã ví</th><th>Chủ chính</th><th>Liên kết</th><th class=\"num\">Số dư</th><th class=\"num\">Khả dụng</th><th class=\"num\">Đang giữ</th></tr></thead><tbody>").append(rows).append("</tbody></table></div>");
        return page("Ví", b.toString(), sess, null, "wallets");
    }
    static String join(List<String> l) { StringBuilder sb = new StringBuilder(); for (int i=0;i<l.size();i++){ if(i>0) sb.append(", "); sb.append(l.get(i)); } return sb.toString(); }

    private String walletDetail(HttpExchange ex, Session sess, Map<String,String> q, String flash, String err) throws IOException {
        String code = q.get("code");
        WalletStore.Wallet found = null;
        if (store != null && code != null) for (WalletStore.Wallet w : store.listWallets()) if (w.code.equalsIgnoreCase(code)) found = w;
        if (found == null) { redirect(ex, "/admin/wallets?err=" + urlEnc("Không tìm thấy ví.")); return null; }
        List<WalletStore.Hold> holds = store.holdsOf(found.code);
        long held = 0; for (WalletStore.Hold h : holds) held += h.amount;
        StringBuilder b = new StringBuilder(msgs(flash, err));
        b.append("<p><a href=\"/admin/wallets\">← Quay lại danh sách ví</a></p><h1>Ví ").append(esc(found.code)).append("</h1>");
        b.append("<div class=\"stats\">");
        b.append("<div class=\"stat\"><p class=\"label\">Số dư</p><div class=\"value\">").append(esc(money(found.balance))).append("</div></div>");
        b.append("<div class=\"stat\"><p class=\"label\">Khả dụng</p><div class=\"value\">").append(esc(money(found.balance-held))).append("</div></div>");
        b.append("<div class=\"stat\"><p class=\"label\">Đang giữ</p><div class=\"value\">").append(esc(money(held))).append("</div></div></div>");
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Thông tin ví</h3>");
        b.append("<p>Mã ví: <b>").append(esc(found.code)).append("</b></p><p>Chủ chính: ").append(esc(found.primary)).append("</p>");
        b.append("<p>Liên kết:</p>");
        if (found.linked.isEmpty()) b.append("<p class=\"muted\">Chưa có nhân vật liên kết.</p>");
        for (String s : found.linked) {
            String confirm = "Hủy liên kết '" + s + "'?";
            b.append("<div class=\"row\" style=\"margin-bottom:8px\"><span style=\"flex:1\">").append(esc(s)).append("</span>");
            b.append("<form class=\"inline-form\" method=\"post\" action=\"/admin/wallet/unlink\" data-confirm=\"").append(esc(confirm)).append("\">").append(csrf(sess)).append("<input type=\"hidden\" name=\"primary\" value=\"").append(esc(found.primary)).append("\"><input type=\"hidden\" name=\"target\" value=\"").append(esc(s)).append("\"><button type=\"submit\" class=\"danger\">Hủy liên kết</button></form></div>");
        }
        b.append("</div>");
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Đang giữ (hold)</h3>");
        if (holds.isEmpty()) b.append("<p class=\"muted\">Không có khoản giữ nào.</p>");
        else { b.append("<div class=\"table-wrap\"><table><thead><tr><th>Mã giữ</th><th class=\"num\">Số tiền</th></tr></thead><tbody>"); for (WalletStore.Hold h : holds) b.append("<tr><td>").append(esc(h.id)).append("</td><td class=\"num\">").append(esc(money(h.amount))).append("</td></tr>"); b.append("</tbody></table></div>"); }
        b.append("</div>");
        b.append("<div class=\"card danger-zone\"><h3 style=\"margin-top:0\">Khu vực nguy hiểm</h3>");
        b.append("<h3>Điều chỉnh số dư</h3><form id=\"adjForm\" method=\"post\" action=\"/admin/wallet/adjust\">").append(csrf(sess)).append("<input id=\"adjCode\" type=\"hidden\" name=\"code\" value=\"").append(esc(found.code)).append("\">");
        b.append("<label>Số tiền<input name=\"amount\" data-amount inputmode=\"numeric\" placeholder=\"VD: 500000\"></label>");
        b.append("<div class=\"radio-row\"><label class=\"radio-card\"><input type=\"radio\" name=\"direction\" value=\"credit\" checked> Cộng tiền</label><label class=\"radio-card\"><input type=\"radio\" name=\"direction\" value=\"debit\"> Trừ tiền</label></div>");
        b.append("<label>Lý do<input name=\"reason\" required placeholder=\"Nhập lý do điều chỉnh\"></label>");
        b.append("<p id=\"adjSummary\" class=\"small\"></p><button id=\"adjSubmit\" type=\"submit\" class=\"primary\">Cộng tiền</button></form>");
        b.append("</div>");
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Lịch sử sổ cái</h3><pre class=\"log\">");
        String wd = workDir();
        if (wd != null) for (String l : readLines(new File(wd + "\\logs\\wallet_ledger.log"), 0)) if (l.contains(found.code)) b.append(esc(l)).append("\n");
        b.append("</pre></div>");
        return page("Ví " + found.code, b.toString(), sess, null, "wallets");
    }
    private void doAdjust(HttpExchange ex, Map<String,String> form) throws IOException {
        String code = form.get("code"); String reason = form.get("reason");
        // strip thousand separators if JS formatting remained / user typed dots
        String amtRaw = nz(form.get("amount")).trim().replace(".", "").replace(" ", "");
        long amt = 0; try { amt = Long.parseLong(amtRaw); } catch (Exception e) { redirect(ex, "/admin/wallet?code=" + urlEnc(nz(code)) + "&err=" + urlEnc("Số tiền chưa hợp lệ. Nhập số nguyên dương, VD 500000.")); return; }
        if (amt <= 0) { redirect(ex, "/admin/wallet?code=" + urlEnc(nz(code)) + "&err=" + urlEnc("Số tiền chưa hợp lệ. Nhập số nguyên dương, VD 500000.")); return; }
        if (reason == null || reason.trim().isEmpty()) { redirect(ex, "/admin/wallet?code=" + urlEnc(nz(code)) + "&err=" + urlEnc("Vui lòng nhập lý do điều chỉnh.")); return; }
        boolean debit = "debit".equals(form.get("direction"));
        long delta = debit ? -amt : amt;
        String errMsg = store == null ? "Không có kho ví." : store.adminAdjust(code, delta, reason);
        if (errMsg != null) redirect(ex, "/admin/wallet?code=" + urlEnc(nz(code)) + "&err=" + urlEnc(errMsg));
        else redirect(ex, "/admin/wallet?code=" + urlEnc(nz(code)) + "&flash=" + urlEnc((debit ? "Đã trừ " : "Đã cộng ") + fmt(amt) + " ₫ " + (debit ? "khỏi" : "vào") + " ví " + nz(code) + "."));
    }
    private void doUnlink(HttpExchange ex, Map<String,String> form) throws IOException {
        String primary = form.get("primary"), target = form.get("target");
        // find code for redirect
        String code = ""; if (store != null) { WalletStore.Wallet w = store.findByChar(primary); if (w != null) code = w.code; }
        String errMsg = store == null ? "Không có kho ví." : store.unlink(primary, target);
        if (errMsg != null) redirect(ex, "/admin/wallet?code=" + urlEnc(code) + "&err=" + urlEnc(errMsg));
        else redirect(ex, "/admin/wallet?code=" + urlEnc(code) + "&flash=" + urlEnc("Đã hủy liên kết '" + nz(target) + "'."));
    }

    // ---------- items ----------
    /** Dem so bot dang dung tung file gia (ten file khong co ".txt"); doc banvatpham.txt 1 lan cho ca trang. */
    private Map<String, Integer> botCountsByItemFile() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        File cf = configFile();
        if (cf == null) return counts;
        for (BotLine bl : parseBots(readLines(cf, 0))) {
            String item = bl.item == null ? "" : bl.item.trim();
            if (item.isEmpty()) continue;
            Integer c = counts.get(item);
            counts.put(item, Integer.valueOf(c == null ? 1 : c.intValue() + 1));
        }
        return counts;
    }
    private static int botCountOf(Map<String, Integer> counts, String fileName) {
        if (fileName == null) return 0;
        String base = fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - 4) : fileName;
        Integer c = counts.get(base);
        return c == null ? 0 : c.intValue();
    }
    private String itemsPage(Session sess, Map<String,String> q, String flash, String err) {
        String wd = workDir(); StringBuilder b = new StringBuilder(msgs(flash, err));
        b.append("<h1>Bảng giá</h1>");
        Map<String, Integer> botCounts = botCountsByItemFile();
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Tạo file giá mới</h3>");
        b.append("<form method=\"post\" action=\"/admin/items/create\">").append(csrf(sess));
        b.append("<div class=\"row\"><input name=\"name\" placeholder=\"VD: sv7_vk\" required><button type=\"submit\" class=\"primary\">Tạo file</button></div>");
        b.append("<p class=\"muted small\">Tên file viết thường, không dấu (chỉ chữ cái, số, dấu chấm, gạch dưới, gạch ngang). File được tạo rỗng và mở ngay để bạn nhập giá.</p>");
        b.append("</form></div>");
        b.append("<div class=\"chips\">");
        File dir = wd == null ? null : new File(wd + "\\items");
        List<String> files = new ArrayList<String>();
        if (dir != null && dir.exists()) { File[] fs = dir.listFiles(); if (fs != null) for (File f : fs) if (f.getName().endsWith(".txt")) files.add(f.getName()); }
        Collections.sort(files);
        String sel = q.get("file");
        for (String fn : files) {
            int using = botCountOf(botCounts, fn);
            b.append("<a class=\"chip").append(fn.equals(sel) ? " active" : "").append("\" href=\"/admin/items?file=").append(urlEnc(fn)).append("\">").append(esc(fn));
            if (using > 0) b.append(" · <b>").append(using).append(" bot</b>");
            b.append("</a>");
        }
        b.append("</div>");
        if (files.isEmpty()) b.append("<div class=\"card empty\">Chưa có file giá nào.</div>");
        if (sel != null && safeName(sel) && sel.endsWith(".txt")) {
            File f = new File(dir, sel);
            b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Sửa ").append(esc(sel)).append("</h3><form method=\"post\" action=\"/admin/items/save\">").append(csrf(sess)).append("<input type=\"hidden\" name=\"file\" value=\"").append(esc(sel)).append("\">");
            b.append("<div class=\"table-wrap\"><table id=\"itemsTable\" class=\"sticky-head\"><thead><tr><th>ID</th><th>Đơn giá ₫</th><th>Tối đa</th><th>Mã VP</th><th>Xóa</th></tr></thead><tbody>");
            int row = 0;
            for (String l : readLines(f, 0)) {
                l = l.trim(); if (l.isEmpty() || l.startsWith("#")) continue;
                String[] p = l.split(":", -1);
                b.append("<tr><td><input name=\"id\" value=\"").append(esc(p.length>0?p[0].trim():"")).append("\"></td><td><input name=\"price\" value=\"").append(esc(p.length>1?p[1].trim():"")).append("\"></td><td><input name=\"maxQty\" value=\"").append(esc(p.length>2?p[2].trim():"")).append("\"></td><td><input name=\"code\" value=\"").append(esc(p.length>3?p[3].trim():"")).append("\"></td><td><input type=\"checkbox\" name=\"del\" value=\"").append(row).append("\"></td></tr>");
                row++;
            }
            for (int i = 0; i < 3; i++) { b.append("<tr><td><input name=\"id\"></td><td><input name=\"price\"></td><td><input name=\"maxQty\"></td><td><input name=\"code\"></td><td></td></tr>"); row++; }
            b.append("</tbody></table></div><p class=\"muted\">Tổng số dòng: <span id=\"rowCount\">").append(row).append("</span>. Thêm dòng: dùng dòng trống bên dưới hoặc nút Thêm dòng. Xóa: tick ô Xóa.</p><div class=\"row\"><button type=\"button\" class=\"ghost\" onclick=\"addItemRow()\">Thêm dòng</button><button type=\"submit\" class=\"primary\">Lưu giá</button></div></form></div>");
            int using = botCountOf(botCounts, sel);
            b.append("<div class=\"card danger-zone\"><h3 style=\"margin-top:0\">Khu vực nguy hiểm</h3>");
            b.append("<p>").append(using == 0
                ? "Hiện không có bot nào dùng file này."
                : "Đang có <b>" + using + " bot</b> dùng file này.").append("</p>");
            if (using > 0) b.append("<p class=\"muted small\">Xóa file sẽ KHÔNG dừng các bot đó: chúng vẫn chạy bằng bảng giá đang giữ trong bộ nhớ cho tới khi restart tiến trình bot. Nếu restart mà file đã mất, bot sẽ không có bảng giá.</p>");
            String confirm = "Xóa file giá " + sel + "? Thao tác này sẽ tạo bản sao .bak rồi xóa file.";
            b.append("<form class=\"inline-form\" method=\"post\" action=\"/admin/items/delete\" data-confirm=\"").append(esc(confirm)).append("\">").append(csrf(sess));
            b.append("<input type=\"hidden\" name=\"file\" value=\"").append(esc(sel)).append("\">");
            b.append("<button type=\"submit\" class=\"danger\">Xóa file</button></form></div>");
        }
        return page("Bảng giá", b.toString(), sess, null, "items");
    }
    private void doItemsSave(HttpExchange ex, String body) throws IOException {
        Map<String, List<String>> multi = parseMulti(body);
        Map<String,String> form = parseParams(body);
        String file = form.get("file");
        if (file == null || !safeName(file) || !file.endsWith(".txt")) { redirect(ex, "/admin/items?err=" + urlEnc("File không hợp lệ.")); return; }
        List<String> ids = multi.get("id"), prices = multi.get("price"), maxs = multi.get("maxQty"), codes = multi.get("code"), dels = multi.get("del");
        if (ids == null) { redirect(ex, "/admin/items?err=" + urlEnc("Không có dòng nào.")); return; }
        Set<Integer> delSet = new HashSet<Integer>(); if (dels != null) for (String d : dels) try { delSet.add(Integer.parseInt(d)); } catch (Exception ignored) {}
        StringBuilder content = new StringBuilder("# Format: id:don_gia:so_luong_toi_da:ma_vp\r\n");
        Set<String> seenCodes = new HashSet<String>();
        int dataRow = -1; // index among non-blank? del values refer to row order in form (all rows incl blank extras) - form rows are in order, del checkbox value was original row index
        // del values correspond to sequential row index in the submitted table (including blank rows in same order)
        for (int i = 0; i < ids.size(); i++) {
            String id = i < ids.size() ? ids.get(i).trim() : "";
            String pr = prices != null && i < prices.size() ? prices.get(i).trim() : "";
            String mx = maxs != null && i < maxs.size() ? maxs.get(i).trim() : "";
            String cd = codes != null && i < codes.size() ? codes.get(i).trim() : "";
            if (delSet.contains(i)) continue;
            if (id.isEmpty() && pr.isEmpty() && mx.isEmpty() && cd.isEmpty()) continue;
            if (id.isEmpty() || pr.isEmpty() || mx.isEmpty() || cd.isEmpty()) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Dòng " + (i+1) + " chưa điền đủ.")); return; }
            long idv, prv, mxv;
            try { idv = Long.parseLong(id); prv = Long.parseLong(pr); mxv = Long.parseLong(mx); } catch (Exception e) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Dòng " + (i+1) + " phải là số.")); return; }
            if (idv <= 0 || idv > 32767) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Dòng " + (i+1) + ": ID phải là số dương.")); return; }
            if (prv < 0) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Dòng " + (i+1) + ": đơn giá phải >= 0.")); return; }
            if (mxv <= 0) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Dòng " + (i+1) + ": tối đa phải > 0.")); return; }
            if (!seenCodes.add(cd.toLowerCase())) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Trùng mã VP " + cd)); return; }
            if (cd.contains(":") || cd.contains("\n")) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Mã VP không hợp lệ.")); return; }
            content.append(idv).append(":").append(prv).append(":").append(mxv).append(":").append(cd).append("\r\n");
        }
        String wd = workDir(); if (wd == null) { redirect(ex, "/admin/items?err=" + urlEnc("Không có thư mục làm việc.")); return; }
        File f = new File(wd + "\\items\\" + file);
        String base = file.substring(0, file.length() - 4);
        int reloaded = 0, deferred = 0, skippedNoItemFile = 0;
        synchronized (AdminWebServer.class) {
            try { if (f.exists()) backup(f); writeAtomic(f, content.toString()); }
            catch (Exception e) { redirect(ex, "/admin/items?file=" + urlEnc(file) + "&err=" + urlEnc("Ghi thất bại: " + e.getMessage())); return; }
            // trigger reload
            try {
                Object vec = Class_cu.a().d;
                if (vec instanceof java.util.Vector) {
                    java.util.Vector v = (java.util.Vector) vec;
                    for (int i = 0; i < v.size(); i++) {
                        Object o = v.elementAt(i); if (!(o instanceof Class_cd)) continue;
                        Class_cd cdx = (Class_cd) o; if (cdx.p == null || cdx.p.banSeller == null) continue;
                        BanSeller bs = cdx.p.banSeller;
                        if (bs.getCurrentItemFile() == null) { skippedNoItemFile++; continue; }
                        if (base.equals(bs.getCurrentItemFile())) {
                            if (bs.state == BanSeller.STATE_IDLE) { if (bs.reloadConfig()) reloaded++; }
                            else { bs.requestReload(); deferred++; }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        String flashMsg = "Đã lưu giá " + file + ". Tải lại ngay: " + reloaded + " bot • hoãn (đang bán): " + deferred + " bot • bỏ qua (không có file hàng): " + skippedNoItemFile + " bot.";
        redirect(ex, "/admin/items?file=" + urlEnc(file) + "&flash=" + urlEnc(flashMsg));
    }
    private void doItemsCreate(HttpExchange ex, Map<String,String> form) throws IOException {
        String name = form.get("name");
        if (name == null || name.trim().isEmpty()) {
            redirect(ex, "/admin/items?err=" + urlEnc("Vui lòng nhập tên file giá.")); return;
        }
        String base = name.trim();
        if (base.endsWith(".txt")) base = base.substring(0, base.length() - 4);
        if (!safeName(base)) {
            redirect(ex, "/admin/items?err=" + urlEnc("Tên file không hợp lệ. Chỉ dùng chữ cái, số, dấu chấm (.), gạch dưới (_), gạch ngang (-); không dùng dấu cách hay dấu tiếng Việt. Ví dụ: sv7_vk")); return;
        }
        String file = base + ".txt";
        String wd = workDir();
        if (wd == null) { redirect(ex, "/admin/items?err=" + urlEnc("Không có thư mục làm việc.")); return; }
        File f = new File(wd + "\\items\\" + file);
        synchronized (AdminWebServer.class) {
            if (f.exists()) {
                redirect(ex, "/admin/items?err=" + urlEnc("File " + file + " đã tồn tại — không ghi đè. Chọn tên khác hoặc mở file trong danh sách để sửa.")); return;
            }
            try {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                writeAtomic(f, "# Format: id:don_gia:so_luong_toi_da:ma_vp\r\n");
            } catch (Exception e) {
                redirect(ex, "/admin/items?err=" + urlEnc("Tạo file thất bại: " + e.getMessage())); return;
            }
        }
        redirect(ex, "/admin/items?file=" + urlEnc(file) + "&flash=" + urlEnc("Đã tạo file giá " + file + " (rỗng). Nhập giá bên dưới rồi bấm Lưu giá."));
    }
    private void doItemsDelete(HttpExchange ex, Map<String,String> form) throws IOException {
        String file = form.get("file");
        if (file == null || !safeName(file) || !file.endsWith(".txt")) {
            redirect(ex, "/admin/items?err=" + urlEnc("File không hợp lệ.")); return;
        }
        String wd = workDir();
        if (wd == null) { redirect(ex, "/admin/items?err=" + urlEnc("Không có thư mục làm việc.")); return; }
        File f = new File(wd + "\\items\\" + file);
        if (!f.exists() || !f.isFile()) {
            redirect(ex, "/admin/items?err=" + urlEnc("Không tìm thấy file " + file + ".")); return;
        }
        int using = botCountOf(botCountsByItemFile(), file);
        synchronized (AdminWebServer.class) {
            try { backup(f); }
            catch (Exception e) { redirect(ex, "/admin/items?err=" + urlEnc("Sao lưu thất bại nên chưa xóa file " + file + ": " + e.getMessage())); return; }
            if (!f.delete()) {
                redirect(ex, "/admin/items?err=" + urlEnc("Không xóa được file " + file + " (đã có bản sao .bak).")); return;
            }
        }
        String flashMsg = "Đã xóa file giá " + file + " (đã tạo bản sao lưu .bak-* trong thư mục items).";
        if (using > 0) flashMsg += " CẢNH BÁO: " + using + " bot đang dùng file này — bot vẫn chạy bằng bảng giá cũ trong bộ nhớ cho tới khi restart tiến trình bot.";
        redirect(ex, "/admin/items?flash=" + urlEnc(flashMsg));
    }

    // ---------- logs ----------
    private String logsPage(Session sess, String flash, String err) {
        String wd = workDir(); StringBuilder b = new StringBuilder(msgs(flash, err));
        b.append("<h1>Nhật ký</h1><p><a class=\"btn ghost\" href=\"/admin/logs\">Tải lại</a></p>");
        b.append("<div class=\"card\"><h3 style=\"margin-top:0\">Sổ cái ví</h3><pre class=\"log\">");
        if (wd != null) for (String l : readLines(new File(wd + "\\logs\\wallet_ledger.log"), 200)) b.append(esc(l)).append("\n");
        b.append("</pre></div><div class=\"card\"><h3 style=\"margin-top:0\">Webhook SePay</h3><pre class=\"log\">");
        if (wd != null) for (String l : readLines(new File(wd + "\\logs\\atm_webhook.log"), 200)) b.append(esc(l)).append("\n");
        b.append("</pre></div>");
        return page("Nhật ký", b.toString(), sess, null, "logs");
    }
}
