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
        try { System.out.println(msg); } catch (Exception ignored) {}
        try {
            String wd = Class_cu.g;
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

    // ---- ATM config ----
    public static String readConfigContent() {
        String wd = null;
        try { wd = Class_cu.g; } catch (Exception ignored) {}
        if (wd == null) return null;
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
