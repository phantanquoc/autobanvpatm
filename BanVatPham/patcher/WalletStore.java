import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class WalletStore {

    public static final class Wallet {
        public String code;
        public String primary;
        public long balance;
        public final List<String> linked = new ArrayList<String>();
        Wallet(String code, String primary, long balance) {
            this.code = code; this.primary = primary; this.balance = balance;
        }
        public boolean hasChar(String name) {
            if (name == null) return false;
            if (primary != null && primary.equalsIgnoreCase(name)) return true;
            for (String s : linked) if (s.equalsIgnoreCase(name)) return true;
            return false;
        }
    }

    public static final class Hold {
        public final String id;
        public final String walletCode;
        public final long amount;
        Hold(String id, String walletCode, long amount) { this.id = id; this.walletCode = walletCode; this.amount = amount; }
    }

    private static final class Invite {
        String walletCode; String inviter; String target; long expiresAt;
    }

    private final List<Wallet> wallets = new ArrayList<Wallet>();
    private final Map<String, Hold> holds = new HashMap<String, Hold>();
    private final Map<String, Invite> invites = new HashMap<String, Invite>();
    private final Random rnd = new Random();
    private String workDir;
    private boolean loaded = false;

    public synchronized void load(String wd) {
        if (loaded) { if (wd != null) workDir = wd; return; }
        workDir = wd;
        loaded = true;
        wallets.clear();
        if (wd == null || wd.isEmpty()) return;
        File f = new File(wd + "\\wallets.txt");
        if (!f.exists()) return;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 3) continue;
                Wallet w = new Wallet(p[0].trim(), p[1].trim(), 0L);
                try { w.balance = Long.parseLong(p[2].trim()); } catch (Exception e) { w.balance = 0L; }
                if (p.length >= 4 && !p[3].trim().isEmpty()) {
                    for (String s : p[3].split(",")) { s = s.trim(); if (!s.isEmpty()) w.linked.add(s); }
                }
                if (!w.code.isEmpty() && !w.primary.isEmpty()) wallets.add(w);
            }
        } catch (Exception e) {
            System.err.println("WalletStore load error: " + e.getMessage());
        } finally { if (br != null) try { br.close(); } catch (Exception ignored) {} }
    }

    public synchronized void save() {
        if (workDir == null || workDir.isEmpty()) return;
        try {
            File dir = new File(workDir);
            if (!dir.exists()) dir.mkdirs();
            File tmp = new File(workDir + "\\wallets.txt.tmp");
            File dst = new File(workDir + "\\wallets.txt");
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp, false), "UTF-8"));
            for (Wallet w : wallets) {
                StringBuilder sb = new StringBuilder();
                sb.append(w.code).append("|").append(w.primary).append("|").append(w.balance).append("|");
                for (int i = 0; i < w.linked.size(); i++) { if (i > 0) sb.append(","); sb.append(w.linked.get(i)); }
                bw.write(sb.toString()); bw.newLine();
            }
            bw.close();
            if (dst.exists() && !dst.delete()) {
                // fallback: overwrite via rename may still work on some FS; try replace
                File bak = new File(workDir + "\\wallets.txt.old");
                if (bak.exists()) bak.delete();
                dst.renameTo(bak);
            }
            if (!tmp.renameTo(dst)) {
                // last resort copy
                copyFile(tmp, dst); tmp.delete();
            }
            File bak = new File(workDir + "\\wallets.txt.old");
            if (bak.exists()) bak.delete();
        } catch (Exception e) {
            System.err.println("WalletStore save error: " + e.getMessage());
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst, false);
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close(); out.flush(); out.close();
    }

    public synchronized void ledger(String type, String detail) {
        try {
            if (workDir == null || workDir.isEmpty()) return;
            File dir = new File(workDir + "\\logs");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "wallet_ledger.log");
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8"));
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            bw.write(ts + " | " + type + " | " + (detail != null ? detail : ""));
            bw.newLine(); bw.close();
        } catch (Exception ignored) {}
    }

    private String newCodeLocked() {
        for (int i = 0; i < 100; i++) {
            String c = String.format("VI%06d", rnd.nextInt(1000000));
            if (findByCodeLocked(c) == null) return c;
        }
        return "VI" + String.format("%06d", (System.currentTimeMillis() % 1000000L));
    }

    private Wallet findByCodeLocked(String code) {
        if (code == null) return null;
        for (Wallet w : wallets) if (w.code.equalsIgnoreCase(code.trim())) return w;
        return null;
    }

    private static Wallet copyWallet(Wallet w) {
        Wallet c = new Wallet(w.code, w.primary, w.balance);
        c.linked.addAll(w.linked);
        return c;
    }

    /** Snapshot copy of all wallets; callers may freely read/mutate the copies. */
    public synchronized List<Wallet> listWallets() {
        List<Wallet> out = new ArrayList<Wallet>();
        for (Wallet w : wallets) out.add(copyWallet(w));
        return out;
    }

    /** Snapshot copy of active holds for a wallet code. */
    public synchronized List<Hold> holdsOf(String code) {
        List<Hold> out = new ArrayList<Hold>();
        if (code == null) return out;
        for (Hold h : holds.values()) if (h.walletCode.equalsIgnoreCase(code.trim())) out.add(new Hold(h.id, h.walletCode, h.amount));
        return out;
    }

    /** Total amount currently held across all wallets. */
    public synchronized long totalHeld() {
        long total = 0L;
        for (Hold h : holds.values()) total += h.amount;
        return total;
    }

    public synchronized Wallet findByCode(String code) { return findByCodeLocked(code); }

    public synchronized Wallet findByChar(String name) {
        if (name == null) return null;
        for (Wallet w : wallets) if (w.hasChar(name)) return w;
        return null;
    }

    public synchronized Wallet getOrCreate(String name) {
        Wallet w = findByChar(name);
        if (w != null) return w;
        w = new Wallet(newCodeLocked(), name, 0L);
        wallets.add(w);
        save();
        ledger("CREATE", "code=" + w.code + " primary=" + name + " balance=0");
        return w;
    }

    /** Find wallet whose deposit code appears (case-insensitive) in content; returns wallet or null. */
    public synchronized Wallet findByCodeInContent(String content) {
        if (content == null) return null;
        String lc = content.toLowerCase();
        for (Wallet w : wallets) if (lc.contains(w.code.toLowerCase())) return w;
        return null;
    }

    public synchronized long available(Wallet w) {
        if (w == null) return 0L;
        long held = 0L;
        for (Hold h : holds.values()) if (h.walletCode.equals(w.code)) held += h.amount;
        return w.balance - held;
    }

    public synchronized boolean placeHold(String holdId, Wallet w, long amount) {
        if (holdId == null || w == null || amount <= 0) return false;
        if (holds.containsKey(holdId)) return false;
        if (available(w) < amount) return false;
        holds.put(holdId, new Hold(holdId, w.code, amount));
        ledger("HOLD", "hold=" + holdId + " code=" + w.code + " amount=" + amount + " available=" + available(w));
        return true;
    }

    public synchronized boolean captureHold(String holdId) {
        if (holdId == null) return false;
        Hold h = holds.remove(holdId);
        if (h == null) return false;
        Wallet w = findByCodeLocked(h.walletCode);
        if (w == null) return false;
        w.balance -= h.amount;
        save();
        ledger("CAPTURE", "hold=" + holdId + " code=" + w.code + " amount=" + h.amount + " balance=" + w.balance);
        return true;
    }

    public synchronized boolean releaseHold(String holdId) {
        if (holdId == null) return false;
        Hold h = holds.remove(holdId);
        if (h == null) return false;
        ledger("RELEASE", "hold=" + holdId + " code=" + h.walletCode + " amount=" + h.amount);
        return true;
    }

    public synchronized void ledgerCaptureIntent(String holdId) {
        Hold h = holds.get(holdId);
        if (h == null) return;
        ledger("CAPTURE_INTENT", "hold=" + holdId + " code=" + h.walletCode + " amount=" + h.amount);
    }

    public synchronized Wallet creditByCodeInContent(String content, long amount, String txId) {
        if (amount <= 0) return null;
        Wallet w = findByCodeInContent(content);
        if (w == null) return null;
        w.balance += amount;
        save();
        ledger("CREDIT", "code=" + w.code + " primary=" + w.primary + " amount=" + amount + " balance=" + w.balance + " tx=" + txId);
        return w;
    }

    /**
     * Manual admin credit/debit. Returns error string or null on success.
     * Debit is limited to available balance (balance - active holds) so holds stay covered.
     */
    public synchronized String adminAdjust(String code, long delta, String reason) {
        if (code == null || code.trim().isEmpty()) return "Thieu ma vi.";
        if (delta == 0L) return "So tien dieu chinh phai khac 0.";
        if (reason == null || reason.trim().isEmpty()) return "Bat buoc nhap ly do dieu chinh.";
        Wallet w = findByCodeLocked(code.trim());
        if (w == null) return "Khong tim thay vi " + code.trim() + ".";
        String cleanReason = reason.trim().replace('|', '/').replace('\n', ' ').replace('\r', ' ');
        if (delta < 0L) {
            long avail = available(w);
            if (-delta > avail) return "So tien rut vuot qua so du kha dung (" + avail + " VND, dang giu cho don mua).";
        }
        w.balance += delta;
        if (w.balance < 0L) { w.balance -= delta; return "So du khong duoc am."; }
        save();
        if (delta > 0L) ledger("ADMIN_CREDIT", "code=" + w.code + " primary=" + w.primary + " amount=" + delta + " balance=" + w.balance + " reason=" + cleanReason);
        else ledger("ADMIN_DEBIT", "code=" + w.code + " primary=" + w.primary + " amount=" + (-delta) + " balance=" + w.balance + " reason=" + cleanReason);
        return null;
    }

    // Linking
    public synchronized String invite(String inviterName, String target) {
        Wallet w = getOrCreate(inviterName);
        if (!w.primary.equalsIgnoreCase(inviterName)) return "Chi tai khoan chinh moi duoc moi lien ket.";
        if (target == null || target.trim().isEmpty()) return "Thieu ten nhan vat can lien ket.";
        target = target.trim();
        if (w.hasChar(target)) return target + " da thuoc vi nay roi.";
        Wallet other = findByChar(target);
        if (other != null && other != w) return target + " da co vi khac.";
        Invite inv = new Invite();
        inv.walletCode = w.code; inv.inviter = inviterName; inv.target = target;
        inv.expiresAt = System.currentTimeMillis() + 15L * 60L * 1000L;
        invites.put(target.toLowerCase(), inv);
        ledger("LINK_INVITE", "code=" + w.code + " inviter=" + inviterName + " target=" + target);
        return null;
    }

    /** Returns error string or null on success. */
    public synchronized String confirmLink(String confirmer) {
        if (confirmer == null) return "Khong co loi moi lien ket.";
        Invite inv = invites.get(confirmer.toLowerCase());
        if (inv == null) return "Khong co loi moi lien ket cho ban.";
        if (System.currentTimeMillis() >= inv.expiresAt) {
            invites.remove(confirmer.toLowerCase());
            return "Loi moi lien ket da het han (15 phut).";
        }
        Wallet w = findByCodeLocked(inv.walletCode);
        if (w == null) { invites.remove(confirmer.toLowerCase()); return "Khong tim thay vi."; }
        // if confirmer created own wallet on an earlier interaction, merge: only allow if that wallet is empty & unused?
        Wallet own = findByChar(confirmer);
        if (own != null && own != w) {
            if (own.balance != 0 || !own.linked.isEmpty()) return "Ban da co vi rieng co so du/lien ket, khong the lien ket.";
            wallets.remove(own);
        }
        if (!w.hasChar(confirmer)) w.linked.add(confirmer);
        invites.remove(confirmer.toLowerCase());
        save();
        ledger("LINK", "code=" + w.code + " linked=" + confirmer + " by=" + inv.inviter);
        return null;
    }

    public synchronized String unlink(String requester, String target) {
        Wallet w = findByChar(requester);
        if (w == null) return "Ban chua co vi.";
        if (!w.primary.equalsIgnoreCase(requester)) return "Chi tai khoan chinh moi duoc huy lien ket.";
        if (target == null || target.trim().isEmpty()) return "Thieu ten can huy lien ket.";
        target = target.trim();
        if (w.primary.equalsIgnoreCase(target)) return "Khong the huy tai khoan chinh.";
        boolean removed = false;
        for (int i = w.linked.size() - 1; i >= 0; i--) if (w.linked.get(i).equalsIgnoreCase(target)) { w.linked.remove(i); removed = true; }
        if (!removed) return target + " khong thuoc vi nay.";
        save();
        ledger("UNLINK", "code=" + w.code + " unlinked=" + target + " by=" + requester);
        return null;
    }

    /** Returns error or null. On success fills outRecipient[0] if provided. */
    public synchronized String transfer(String sender, String recipient, long amount, Wallet[] outRecipient) {
        if (amount <= 0) return "So tien phai lon hon 0.";
        Wallet from = getOrCreate(sender);
        if (from.hasChar(recipient)) return "Khong the chuyen cho chinh vi cua ban.";
        if (available(from) < amount) return "So du kha dung khong du. Kha dung: " + available(from) + " VND";
        Wallet to = getOrCreate(recipient);
        from.balance -= amount;
        to.balance += amount;
        save();
        ledger("TRANSFER", "fromCode=" + from.code + " from=" + sender + " toCode=" + to.code + " to=" + recipient + " amount=" + amount + " fromBalance=" + from.balance + " toBalance=" + to.balance);
        if (outRecipient != null && outRecipient.length > 0) outRecipient[0] = to;
        return null;
    }
}
