import java.io.*;
import java.util.*;

public final class WalletService {

    public interface Notifier {
        /** @return true if the message was handed off successfully, false otherwise. */
        boolean deliver(String to, String msg);
    }

    private static WalletService instance;
    private final Set<String> seenTransactionIds = Collections.synchronizedSet(new HashSet<String>());
    private volatile boolean atmSeenLoaded = false;
    private final List<Notifier> notifiers = new ArrayList<Notifier>();

    private WalletService() {}

    public static synchronized WalletService getInstance() {
        if (instance == null) instance = new WalletService();
        return instance;
    }

    public synchronized void registerNotifier(Notifier n) {
        if (n != null && !notifiers.contains(n)) notifiers.add(n);
    }

    /** Try every registered bot until one reports success. Never throws. */
    public boolean notifyCustomer(String to, String msg) {
        List<Notifier> copy;
        synchronized (this) { copy = new ArrayList<Notifier>(notifiers); }
        for (Notifier n : copy) {
            try { if (n.deliver(to, msg)) return true; } catch (Exception ignored) {}
        }
        return false;
    }

    public void logAtm(String msg) {
        String wd = null;
        try { wd = Class_cu.g; } catch (Exception ignored) {}
        logTo(wd, msg);
    }

    /** Console + wd\logs\atm_webhook.log (bo qua file neu wd null). Never throws. */
    static void logTo(String wd, String msg) {
        try { System.out.println(msg); } catch (Exception ignored) {}
        try {
            if (wd != null) {
                File dir = new File(wd + "\\logs");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "atm_webhook.log");
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8"));
                String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                bw.write(ts + " " + msg); bw.newLine(); bw.close();
            }
        } catch (Exception ignored) {}
    }

    // ---- cau hinh chung (cauhinh.txt) ----
    // banvatpham.txt: moi dong khac rong la mot bot (game doc), nen tag chung nam rieng trong cauhinh.txt.
    public static final String CAUHINH_FILE = "cauhinh.txt";
    public static final String BOTS_FILE = "banvatpham.txt";
    /** 13 tag chung, theo thu tu chuan khi ghi cauhinh.txt. Tag rieng bot (SV, TK, MK, ...) khong bao gio dung toi. */
    static final String[] GLOBAL_TAGS = {
        "ATM", "ATM_PORT", "SEPAY_SECRET", "BANK_CODE", "BANK_STK", "BANK_NAME",
        "ADMIN_PORT", "ADMIN_PASS", "ADMIN_BIND", "ADMIN_PROXY", "ADMIN_HTTPS", "ADMIN_CERT", "ADMIN_CERT_PASS"
    };
    /** Dong ghi chu dau cauhinh.txt - KHONG duoc chua dau ngoac vuong. */
    static final String[] CAUHINH_HEADER = {
        "# Cau hinh chung: ATM, SePay, ngan hang, web admin. Moi dong mot tag.",
        "# Danh sach bot nam trong banvatpham.txt, moi dong mot bot.",
        "# Dong bat dau bang dau thang la ghi chu, khong dat dau ngoac vuong trong ghi chu."
    };
    private static final java.nio.charset.Charset LATIN1 = java.nio.charset.Charset.forName("ISO-8859-1");
    private static final java.nio.charset.Charset UTF8 = java.nio.charset.Charset.forName("UTF-8");
    /** wd -> true: doc tag chung tu cauhinh.txt; false: tam doc tu banvatpham.txt (chua tach duoc). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> cauHinhMode =
        new java.util.concurrent.ConcurrentHashMap<String, Boolean>();

    /**
     * Quyet dinh (mot lan moi tien trinh, moi thu muc) doc tag chung tu dau, va neu can thi tach
     * tag chung tu banvatpham.txt sang cauhinh.txt (co sao luu). Chay duoi khoa AdminWebServer.class
     * - cung khoa web admin dung khi ghi banvatpham.txt.
     * @return true = doc tag chung tu cauhinh.txt (co the chua ton tai -> khong co cau hinh);
     *         false = tam doc tu banvatpham.txt nhu cu (tach that bai, se thu lai lan khoi dong sau).
     */
    public static boolean ensureCauHinh(String wd) {
        if (wd == null) return false;
        Boolean m = cauHinhMode.get(wd);
        if (m != null) return m.booleanValue();
        synchronized (AdminWebServer.class) {
            m = cauHinhMode.get(wd);
            if (m != null) return m.booleanValue();
            boolean r;
            try {
                r = migrateCauHinh(wd);
            } catch (Throwable t) {
                // cauhinh.txt da co thi no la nguon chinh; chua co thi giu cach doc cu.
                r = new File(wd, CAUHINH_FILE).isFile();
                logTo(wd, "!!! Loi khi tach cau hinh (" + t.getClass().getSimpleName() + ") - "
                    + (r ? "dung cauhinh.txt" : "tam doc cau hinh tu banvatpham.txt") + " !!!");
            }
            cauHinhMode.put(wd, Boolean.valueOf(r));
            return r;
        }
    }

    /** Goi duy nhat tu ensureCauHinh (da giu khoa). KHONG bao gio ghi gia tri tag ra log. */
    private static boolean migrateCauHinh(String wd) throws IOException {
        File cfg = new File(wd, CAUHINH_FILE);
        File bots = new File(wd, BOTS_FILE);

        if (cfg.exists()) {
            // Quy tac 1: cauhinh.txt la nguon duy nhat; tag chung con sot trong banvatpham.txt bi bo qua.
            if (bots.isFile()) {
                byte[] b = java.nio.file.Files.readAllBytes(bots.toPath());
                String s = new String(b, LATIN1);
                for (String tag : GLOBAL_TAGS) {
                    if (s.indexOf("[" + tag + "]") >= 0)
                        logTo(wd, "CANH BAO: tag " + tag + " trong banvatpham.txt bi bo qua, dung cauhinh.txt");
                }
            }
            return true;
        }
        if (!bots.isFile()) return true; // khong co gi de tach -> khong co cau hinh chung

        byte[] orig = java.nio.file.Files.readAllBytes(bots.toPath());
        int bomLen = 0;
        if (orig.length >= 3 && (orig[0] & 0xFF) == 0xEF && (orig[1] & 0xFF) == 0xBB && (orig[2] & 0xFF) == 0xBF) {
            bomLen = 3;
        } else if (orig.length >= 2 && (((orig[0] & 0xFF) == 0xFF && (orig[1] & 0xFF) == 0xFE)
                || ((orig[0] & 0xFF) == 0xFE && (orig[1] & 0xFF) == 0xFF))) {
            logTo(wd, "!!! KHONG tach duoc cau hinh: banvatpham.txt dang ma UTF-16, can luu lai dang UTF-8 hoac ANSI. Sua tay roi khoi dong lai !!!");
            return false;
        }
        // ISO-8859-1: 1 byte <-> 1 char, nen ghi lai giu nguyen tung byte ngoai cac tag bi xoa
        // (tag/ngoac la ASCII, khong bao gio nam trong chuoi byte nhieu byte cua UTF-8).
        String body = new String(orig, bomLen, orig.length - bomLen, LATIN1);

        boolean any = false;
        for (String tag : GLOBAL_TAGS) if (body.indexOf("[" + tag + "]") >= 0) { any = true; break; }
        if (!any) return true; // quy tac 3: khong co tag chung nao -> mac dinh

        // Tach dong, giu nguyen ky tu xuong dong cua tung dong (\r\n, \n hoac \r).
        List<String> lines = new ArrayList<String>();
        List<String> eols = new ArrayList<String>();
        int pos = 0, n = body.length();
        while (pos < n) {
            int i = pos;
            while (i < n && body.charAt(i) != '\r' && body.charAt(i) != '\n') i++;
            lines.add(body.substring(pos, i));
            if (i >= n) { eols.add(""); pos = n; }
            else if (body.charAt(i) == '\r' && i + 1 < n && body.charAt(i + 1) == '\n') { eols.add("\r\n"); pos = i + 2; }
            else { eols.add(String.valueOf(body.charAt(i))); pos = i + 1; }
        }

        // Kiem tra + thu thap gia tri (lan xuat hien DAU TIEN theo thu tu file, giong tagValue) + vi tri can xoa.
        Map<String, String> firstVal = new HashMap<String, String>();
        Set<String> dupWarned = new HashSet<String>();
        StringBuilder out = new StringBuilder(body.length());
        for (int li = 0; li < lines.size(); li++) {
            String line = lines.get(li);
            List<int[]> spans = new ArrayList<int[]>(); // {start, end, tagIndex}
            for (int t = 0; t < GLOBAL_TAGS.length; t++) {
                String tag = GLOBAL_TAGS[t];
                String open = "[" + tag + "]", close = "[/" + tag + "]";
                int from = 0;
                while (true) {
                    int a = line.indexOf(open, from);
                    if (a < 0) break;
                    int c = line.indexOf(close, a + open.length());
                    if (c < 0) {
                        logTo(wd, "!!! KHONG tach duoc cau hinh: tag " + tag + " thieu [/" + tag + "] trong banvatpham.txt. Sua tay roi khoi dong lai !!!");
                        return false;
                    }
                    String raw = line.substring(a + open.length(), c);
                    String val = new String(raw.getBytes(LATIN1), UTF8).trim();
                    String prev = firstVal.get(tag);
                    if (prev == null) firstVal.put(tag, val);
                    else if (!prev.equals(val) && dupWarned.add(tag))
                        logTo(wd, "CANH BAO: tag " + tag + " co nhieu gia tri khac nhau, dung gia tri dau tien");
                    spans.add(new int[] { a, c + close.length(), t });
                    from = c + close.length();
                }
            }
            if (spans.isEmpty()) { out.append(line).append(eols.get(li)); continue; }
            Collections.sort(spans, new Comparator<int[]>() {
                public int compare(int[] x, int[] y) { return x[0] < y[0] ? -1 : (x[0] > y[0] ? 1 : 0); }
            });
            StringBuilder nl = new StringBuilder(line.length());
            int p = 0;
            for (int k = 0; k < spans.size(); k++) {
                int[] sp = spans.get(k);
                if (sp[0] < p) {
                    // Hai tag chung long/chong nhau: khong biet xoa the nao cho dung -> khong dong vao file.
                    logTo(wd, "!!! KHONG tach duoc cau hinh: tag " + GLOBAL_TAGS[sp[2]] + " long vao tag "
                        + GLOBAL_TAGS[spans.get(k - 1)[2]] + " trong banvatpham.txt. Sua tay roi khoi dong lai !!!");
                    return false;
                }
                nl.append(line, p, sp[0]);
                p = sp[1];
            }
            nl.append(line, p, line.length());
            String stripped = nl.toString();
            if (stripped.trim().isEmpty()) continue; // dong chi chua tag chung -> bo
            out.append(stripped).append(eols.get(li));
        }

        // Sao luu nguyen byte truoc khi dong vao bat cu thu gi.
        String ts = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        File bak = new File(wd, BOTS_FILE + ".bak-tachcauhinh-" + ts);
        int extra = 0;
        while (bak.exists()) { extra++; bak = new File(wd, BOTS_FILE + ".bak-tachcauhinh-" + ts + "-" + extra); }
        try {
            writeNewFileSynced(bak, orig);
        } catch (IOException e) {
            logTo(wd, "!!! KHONG tach duoc cau hinh: khong tao duoc ban sao luu " + bak.getName() + " (" + e.getMessage() + ") - tam doc cau hinh tu banvatpham.txt !!!");
            return false;
        }

        // Ghi cauhinh.txt: UTF-8 khong BOM, CRLF, thu tu chuan, chi tag co mat.
        StringBuilder cb = new StringBuilder();
        for (String h : CAUHINH_HEADER) cb.append(h).append("\r\n");
        int count = 0;
        for (String tag : GLOBAL_TAGS) {
            String v = firstVal.get(tag);
            if (v == null) continue;
            cb.append('[').append(tag).append(']').append(v).append("[/").append(tag).append(']').append("\r\n");
            count++;
        }
        try {
            writeFileAtomic(cfg, cb.toString().getBytes(UTF8));
        } catch (IOException e) {
            logTo(wd, "!!! KHONG ghi duoc cauhinh.txt (" + e.getMessage() + ") - banvatpham.txt giu nguyen, tam doc cau hinh tu banvatpham.txt, se thu lai lan khoi dong sau !!!");
            return false;
        }

        // Xoa tag chung khoi banvatpham.txt (giu BOM neu co, giu xuong dong goc).
        byte[] rest = out.toString().getBytes(LATIN1);
        byte[] nb = new byte[bomLen + rest.length];
        System.arraycopy(orig, 0, nb, 0, bomLen);
        System.arraycopy(rest, 0, nb, bomLen, rest.length);
        try {
            writeFileAtomic(bots, nb);
        } catch (IOException e) {
            logTo(wd, "!!! Da ghi cauhinh.txt nhung KHONG xoa duoc tag chung khoi banvatpham.txt (" + e.getMessage() + ") - dung cauhinh.txt, tag cu trong banvatpham.txt bi bo qua !!!");
            return true;
        }
        logTo(wd, "Da tach cau hinh sang cauhinh.txt (" + count + " tag), sao luu: " + bak.getName());
        return true;
    }

    /** Tao file moi (that bai neu da ton tai), fsync, doc lai so sanh. */
    private static void writeNewFileSynced(File dst, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(dst, false);
        try {
            fos.write(data);
            fos.flush();
            try { fos.getFD().sync(); } catch (Exception ignored) {}
        } finally { fos.close(); }
        if (!Arrays.equals(java.nio.file.Files.readAllBytes(dst.toPath()), data))
            throw new IOException("noi dung ghi lai khong khop");
    }

    /** Ghi tmp + fsync roi thay the dst (ATOMIC_MOVE neu duoc), doc lai so sanh. */
    private static void writeFileAtomic(File dst, byte[] data) throws IOException {
        File tmp = new File(dst.getAbsolutePath() + ".tmp-tachcauhinh");
        try {
            FileOutputStream fos = new FileOutputStream(tmp, false);
            try {
                fos.write(data);
                fos.flush();
                try { fos.getFD().sync(); } catch (Exception ignored) {}
            } finally { fos.close(); }
            try {
                java.nio.file.Files.move(tmp.toPath(), dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                java.nio.file.Files.move(tmp.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tmp.isFile()) tmp.delete();
        }
        if (!Arrays.equals(java.nio.file.Files.readAllBytes(dst.toPath()), data))
            throw new IOException("noi dung ghi lai khong khop: " + dst.getName());
    }

    /** Noi dung cauhinh.txt (UTF-8, bo BOM, bo dong ghi chu #). null neu khong co file. */
    static String readCauHinh(String wd) {
        try {
            File f = new File(wd, CAUHINH_FILE);
            if (!f.isFile()) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            try {
                String line;
                boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; if (line.startsWith("﻿")) line = line.substring(1); }
                    if (line.trim().startsWith("#")) continue;
                    sb.append(line).append("\n");
                }
            } finally { br.close(); }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ---- ATM config ----
    public static String readConfigContent() {
        String wd = null;
        try { wd = Class_cu.g; } catch (Exception ignored) {}
        return readConfigContent(wd);
    }

    /** Noi dung de tra tag chung (tagValue) cho thu muc lam viec wd. */
    public static String readConfigContent(String wd) {
        if (wd == null) return null;
        if (ensureCauHinh(wd)) return readCauHinh(wd);
        // Chua tach duoc (xem log): doc banvatpham.txt nhu truoc.
        String c1 = null;
        try { c1 = Class_cq.d(wd + "\\banvatpham.txt"); } catch (Exception ignored) {}
        if (c1 != null) return c1;
        return readFileDirect(wd + "\\banvatpham.txt");
    }

    private static String readFileDirect(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public static String tagValue(String content, String tag) {
        if (content == null) return null;
        try {
            String v = Class_bb.a(content, tag);
            if (v != null) return v.trim();
        } catch (Exception ignored) {}
        String open = "[" + tag + "]";
        String close = "[/" + tag + "]";
        int a = content.indexOf(open);
        if (a >= 0) {
            int b = content.indexOf(close, a);
            if (b >= 0) return content.substring(a + open.length(), b).trim();
        }
        return null;
    }

    public static boolean isAtmEnabled() {
        String v = tagValue(readConfigContent(), "ATM");
        return v != null && v.trim().equals("1");
    }

    public static String getBankCode() { String v = tagValue(readConfigContent(), "BANK_CODE"); return v != null ? v : ""; }
    public static String getBankStk() { String v = tagValue(readConfigContent(), "BANK_STK"); return v != null ? v : ""; }
    public static String getBankName() { String v = tagValue(readConfigContent(), "BANK_NAME"); return v != null ? v : ""; }
    public static String getSepaySecret() { String v = tagValue(readConfigContent(), "SEPAY_SECRET"); return v != null ? v : ""; }
    public static int getAtmPort() {
        String v = tagValue(readConfigContent(), "ATM_PORT");
        if (v != null) try { int p = Integer.parseInt(v.trim()); if (p > 0 && p < 65536) return p; } catch (Exception ignored) {}
        return 8089;
    }

    public static String getAdminPass() { String v = tagValue(readConfigContent(), "ADMIN_PASS"); return v != null ? v : ""; }
    public static int getAdminPort() {
        String v = tagValue(readConfigContent(), "ADMIN_PORT");
        if (v != null) try { int p = Integer.parseInt(v.trim()); if (p > 0 && p < 65536) return p; } catch (Exception ignored) {}
        return 8090;
    }

    public static String buildVietQrUrl(long amount, String code) {
        String bank = getBankCode();
        String stk = getBankStk();
        String name = getBankName();
        if (stk == null || stk.isEmpty()) return null;
        String bankPart = (bank != null && !bank.isEmpty()) ? bank + "-" + stk : stk;
        try {
            String encInfo = code != null ? java.net.URLEncoder.encode(code, "UTF-8") : "";
            String url = "https://img.vietqr.io/image/" + bankPart + "-compact2.png?addInfo=" + encInfo;
            if (amount > 0) url += "&amount=" + amount;
            if (name != null && !name.isEmpty()) url += "&accountName=" + java.net.URLEncoder.encode(name, "UTF-8");
            return url;
        } catch (Exception e) {
            return "https://img.vietqr.io/image/" + bankPart + "-compact2.png?addInfo=" + code;
        }
    }

    public String depositInfo(WalletStore.Wallet w) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nap vi: STK ").append(getBankStk());
        String bn = getBankName(); if (bn != null && !bn.isEmpty()) sb.append(" (").append(bn).append(")");
        String bc = getBankCode(); if (bc != null && !bc.isEmpty()) sb.append(" NH: ").append(bc);
        sb.append(" | ND bat buoc: ").append(w.code);
        String qr = buildVietQrUrl(0, w.code);
        if (qr != null) sb.append(" | QR: ").append(qr);
        return sb.toString();
    }

    // ---- anti-replay ----
    private void loadSeenIds() {
        if (atmSeenLoaded) return;
        synchronized (seenTransactionIds) {
            if (atmSeenLoaded) return;
            String wd = null;
            try { wd = Class_cu.g; } catch (Exception ignored) {}
            if (wd == null) { atmSeenLoaded = true; return; }
            File f = new File(wd + "\\atm_seen.log");
            if (!f.exists()) { atmSeenLoaded = true; return; }
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) seenTransactionIds.add(line);
                }
                br.close();
            } catch (Exception ignored) {}
            atmSeenLoaded = true;
        }
    }

    /** Append + fsync the seen id. @return false if it could not be durably persisted. */
    private boolean persistSeenId(String id) {
        if (id == null) return false;
        String wd = null;
        try { wd = Class_cu.g; } catch (Exception ignored) {}
        if (wd == null) return false;
        try {
            File f = new File(wd + "\\atm_seen.log");
            FileOutputStream fos = new FileOutputStream(f, true);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
            bw.write(id); bw.newLine(); bw.flush();
            try { fos.getFD().sync(); } catch (Exception ignored) {}
            bw.close();
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean isTransactionSeen(String id) {
        if (id == null) return false;
        loadSeenIds();
        synchronized (seenTransactionIds) { return seenTransactionIds.contains(id); }
    }

    /**
     * Atomically claim a transaction id: adds it to the in-memory set AND durably
     * persists it to atm_seen.log BEFORE any crediting happens.
     * @return 1 claimed (first time), 0 already seen (replay), -1 persistence failed
     *         (caller MUST NOT credit - a restart could otherwise replay it).
     */
    private int claimSeen(String id) {
        if (id == null) return -1;
        loadSeenIds();
        synchronized (seenTransactionIds) {
            if (seenTransactionIds.contains(id)) return 0;
            seenTransactionIds.add(id);
        }
        if (!persistSeenId(id)) {
            synchronized (seenTransactionIds) { seenTransactionIds.remove(id); }
            logAtm("!!! ATM SEEN-PERSIST FAILED tx=" + id + " - refusing to process, manual check required !!!");
            return -1;
        }
        return 1;
    }

    public void maybeStartAtmService(WalletStore store) {
        try {
            if (!isAtmEnabled()) return;
            AtmPaymentService.getInstance().start(getAtmPort(), getSepaySecret(), this, store);
        } catch (Exception e) {
            logAtm("ATM service start error: " + e.getMessage());
        }
    }

    /**
     * Returns 1 credited, 0 duplicate (already seen), -1 unmatched/not credited.
     * The transaction id is claimed and persisted BEFORE crediting, so a crash
     * between marking seen and saving the wallet can never double-credit after
     * restart. If crediting fails after the claim, the tx stays seen and the
     * failure is logged loudly (atm log + wallet ledger) for manual reconciliation.
     * Unmatched webhooks are also claimed, so a redelivery is answered duplicate.
     */
    public synchronized int handleAtmWebhook(WalletStore store, long amount, String content, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) return -1;
        int claim = claimSeen(transactionId);
        if (claim == 0) return 0;
        if (claim < 0) return -1;
        // From here on the tx is durably seen - even if crediting fails below.
        if (store == null || amount <= 0) {
            logAtm("ATM webhook unmatched (invalid) content=" + content + " amount=" + amount + " tx=" + transactionId + " (marked seen)");
            return -1;
        }
        WalletStore.Wallet w;
        try {
            w = store.creditByCodeInContent(content, amount, transactionId);
        } catch (Exception e) {
            logAtm("!!! ATM CREDIT FAILED AFTER SEEN-CLAIM tx=" + transactionId + " amount=" + amount + " content=" + content + " err=" + e.getMessage() + " - MANUAL RECONCILIATION REQUIRED, tx will NOT be re-credited automatically !!!");
            try { store.ledger("CREDIT_FAILED", "tx=" + transactionId + " amount=" + amount + " content=" + content + " err=" + e.getMessage() + " MANUAL_RECONCILIATION_REQUIRED"); } catch (Exception ignored) {}
            return -1;
        }
        if (w == null) {
            logAtm("ATM webhook unmatched content=" + content + " amount=" + amount + " tx=" + transactionId + " (marked seen)");
            return -1;
        }
        logAtm("ATM credited code=" + w.code + " amount=" + amount + " tx=" + transactionId + " balance=" + w.balance);
        try {
            boolean notified = notifyCustomer(w.primary, "Nap vi thanh cong +" + amount + " VND. So du: " + w.balance + " VND (ma " + w.code + ")");
            if (!notified) logAtm("ATM notify failed (no bot delivered) to=" + w.primary + " tx=" + transactionId + " - credit unaffected");
        } catch (Exception ignored) {}
        return 1;
    }
}
