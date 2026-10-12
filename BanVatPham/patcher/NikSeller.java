import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public final class NikSeller {

    public static final int STATE_IDLE = 0;
    public static final int STATE_WAITING = 1;
    public static final int STATE_IN_TRADE = 2;
    public static final int STATE_CONFIRMED = 3;

    private Class_cd cd;
    private NikStore store;
    public int state = STATE_IDLE;
    public String buyerName = null;
    public long totalVnd = 0L;
    public int expectedXu = 0;
    public List<NikEntry> pendingEntries = null;
    public long tradeStartTime = 0L;

    public NikSeller(Class_cd cd, NikStore store) {
        this.cd = cd;
        this.store = store;
    }

    public NikStore getStore() { return store; }

    // routing check
    public static boolean isNikMessage(String content) {
        if (content == null) return false;
        String s = content.trim().toLowerCase();
        return s.contains("nick");
    }

    private java.util.HashMap<String, Long> lastHelpTime = new java.util.HashMap<String, Long>();
    private void sendGroupedHelp(String toName) {
        try {
            if (toName == null) return;
            long now = Class_bb.b();
            Long last = lastHelpTime.get(toName);
            if (last != null && now - last.longValue() < 15000L) return;
            lastHelpTime.put(toName, Long.valueOf(now));
            java.util.List<NikEntry> chuaBan = store != null ? store.getChuaBan() : null;
            if (chuaBan == null || chuaBan.isEmpty()) { sendPrivate(toName, "Chua co nick dang ban."); return; }
            java.util.LinkedHashMap<String,Integer> counts = new java.util.LinkedHashMap<String,Integer>();
            for (NikEntry e : chuaBan) {
                String key = e.vatPham != null ? e.vatPham.trim() : "";
                if (key.isEmpty()) key = "(khac)";
                Integer cur = counts.get(key);
                counts.put(key, cur == null ? Integer.valueOf(1) : Integer.valueOf(cur.intValue() + 1));
            }
            if (counts.isEmpty()) { sendPrivate(toName, "Chua co nick dang ban."); return; }
            for (java.util.Map.Entry<String,Integer> en : counts.entrySet()) {
                sendPrivate(toName, "nick ban " + en.getKey() + " sl: " + en.getValue());
            }
        } catch (Exception ignored) {}
    }

    private String findLoaiInTokens(String[] tokens) {
        if (tokens == null || store == null) return null;
        List<String> loais = store.getLoaiList();
        if (loais == null || loais.isEmpty()) return null;
        for (String tok : tokens) {
            String t = tok.trim().toLowerCase();
            if (t.isEmpty() || t.equals("mua") || t.equals("nick")) continue;
            boolean isNum = true;
            for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) { isNum = false; break; }
            if (isNum && !t.isEmpty()) continue;
            for (String loai : loais) if (loai != null && loai.equalsIgnoreCase(t)) return loai;
        }
        return null;
    }

    public boolean handlePrivateMessage(String senderName, String content) {
        if (senderName == null || content == null) return false;
        String normalized = content.trim().toLowerCase();
        // allow messages that contain a known loai even without explicit "nik" token (e.g. "mua ctt 2")
        boolean containsNik = normalized.contains("nick");
        if (!containsNik) {
            // quick check if it contains a known loai and "mua" -> treat as nik purchase intent
            String[] tmpTokens = normalized.split("\\s+");
            String tmpLoai = findLoaiInTokens(tmpTokens);
            if (tmpLoai == null || !normalized.contains("mua")) return false;
        }

        // busy guard with BanSeller (applies to both help and mua)
        try {
            if (cd != null && cd.p != null && cd.p.banSeller != null && cd.p.banSeller.state != BanSeller.STATE_IDLE) {
                sendPrivate(senderName, "Bot dang ban cho nguoi khac, vui long cho.");
                return true;
            }
        } catch (Exception ignored) {}
        if (state != STATE_IDLE) {
            if (buyerName != null && buyerName.equals(senderName)) {
                // same buyer already in flow - allow help but fall through
                if (!normalized.contains("mua")) { sendGroupedHelp(senderName); return true; }
            } else {
                sendPrivate(senderName, "Bot dang ban cho nguoi khac, vui long cho.");
                return true;
            }
        }

        // help path: no "mua" -> grouped stock help, not a purchase (after busy guard)
        if (!normalized.contains("mua")) {
            sendGroupedHelp(senderName);
            return true;
        }

        // also guard withdrawing for purchase
        try {
            if (cd != null && cd.p != null && cd.p.banSeller != null && cd.p.banSeller.withdrawing) {
                sendPrivate(senderName, "Bot dang lay hang tu ruong, vui long thu lai sau.");
                return true;
            }
        } catch (Exception ignored) {}

        // timeout recovery
        if (state != STATE_IDLE && tradeStartTime > 0 && Class_bb.b() - tradeStartTime > 90000L) {
            reset();
        }
        if (state != STATE_IDLE && !senderName.equals(buyerName)) {
            sendPrivate(senderName, "Bot dang ban cho nguoi khac, vui long cho.");
            return true;
        }

        String[] tokens = normalized.split("\\s+");
        String loai = findLoaiInTokens(tokens);

        // parse qty: "mua nik 3" or "mua nik" — also support "mua 2 nik ctt" and "mua ctt 2"
        // keeps qtyInvalid logic from original: non-integer after nik -> error
        int qty = 1;
        boolean qtyFound = false;
        boolean qtyInvalid = false;
        int nikIdx = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].contains("nick")) { nikIdx = i; break; }
        }
        if (nikIdx != -1 && nikIdx + 1 < tokens.length) {
            // first scan tokens after nik, skipping loai token
            for (int j = nikIdx + 1; j < tokens.length; j++) {
                String tok = tokens[j];
                if (loai != null && tok.equalsIgnoreCase(loai)) continue;
                String t = tok.replaceAll("[^0-9-]", "");
                if (t.isEmpty()) continue;
                try {
                    int v = Integer.parseInt(t);
                    if (v <= 0) qtyInvalid = true;
                    else { qty = v; qtyFound = true; }
                } catch (NumberFormatException e) {
                    qtyInvalid = true;
                }
                break;
            }
            if (!qtyFound && !qtyInvalid) {
                String after = null;
                for (int j = nikIdx + 1; j < tokens.length; j++) {
                    String tok = tokens[j];
                    if (loai != null && tok.equalsIgnoreCase(loai)) continue;
                    after = tok;
                    break;
                }
                if (after != null && after.matches(".*[a-z].*")) {
                    qtyInvalid = true;
                } else {
                    // fallback: any numeric token (supports "mua 2 nik ctt")
                    for (int j = 0; j < tokens.length; j++) {
                        if (j == nikIdx) continue;
                        String tok = tokens[j];
                        if (loai != null && tok.equalsIgnoreCase(loai)) continue;
                        if (tok.equals("mua") || tok.equals("nick")) continue;
                        String t = tok.replaceAll("[^0-9-]", "");
                        if (t.isEmpty()) continue;
                        try {
                            int v = Integer.parseInt(t);
                            if (v <= 0) qtyInvalid = true;
                            else { qty = v; qtyFound = true; }
                            break;
                        } catch (NumberFormatException e) { qtyInvalid = true; break; }
                    }
                }
            }
        } else if (nikIdx != -1) {
            // "mua nik" alone — look for number elsewhere (e.g. "mua ctt 2" already handled via nikIdx==-1 branch, but keep)
            for (int j = 0; j < tokens.length; j++) {
                if (j == nikIdx) continue;
                String tok = tokens[j];
                if (loai != null && tok.equalsIgnoreCase(loai)) continue;
                if (tok.equals("mua") || tok.equals("nik")) continue;
                String t = tok.replaceAll("[^0-9-]", "");
                if (t.isEmpty()) continue;
                try {
                    int v = Integer.parseInt(t);
                    if (v <= 0) qtyInvalid = true;
                    else { qty = v; qtyFound = true; }
                    break;
                } catch (NumberFormatException e) { qtyInvalid = true; break; }
            }
        } else {
            // no nik token (e.g. "mua ctt 2")
            for (String tok : tokens) {
                if (loai != null && tok.equalsIgnoreCase(loai)) continue;
                if (tok.equals("mua")) continue;
                String t = tok.replaceAll("[^0-9-]", "");
                if (t.isEmpty()) continue;
                try {
                    int v = Integer.parseInt(t);
                    if (v <= 0) qtyInvalid = true;
                    else { qty = v; qtyFound = true; }
                    break;
                } catch (NumberFormatException e) { qtyInvalid = true; break; }
            }
            if (!qtyFound && !qtyInvalid) {
                // check for non-numeric token that would be invalid like "mua ctt abc"
                boolean hasAlpha = false;
                for (String tok : tokens) {
                    if (tok.equals("mua")) continue;
                    if (loai != null && tok.equalsIgnoreCase(loai)) continue;
                    if (tok.matches(".*[a-z].*")) { hasAlpha = true; break; }
                }
                if (hasAlpha) qtyInvalid = true;
            }
        }

        if (qtyInvalid) {
            sendPrivate(senderName, "So luong khong hop le. VD: mua nick 2");
            return true;
        }

        List<NikEntry> selected;
        String quoteMsg;
        if (loai != null) {
            List<NikEntry> availByLoai = store.getChuaBanByLoai(loai);
            if (availByLoai.size() < qty) {
                sendPrivate(senderName, "Chi con " + availByLoai.size() + " nick loai " + loai + " chua ban.");
                return true;
            }
            selected = store.peekFifoByLoai(loai, qty);
            if (selected == null) {
                sendPrivate(senderName, "Chi con " + availByLoai.size() + " nick loai " + loai + " chua ban.");
                return true;
            }
            long total = 0;
            for (NikEntry e : selected) total += e.giaVnd;
            int vndPerXu = NikStore.resolveVndPerXu(Class_cu.g);
            if (vndPerXu <= 0) vndPerXu = 1000;
            int xu = (int) ((total + vndPerXu - 1) / vndPerXu);
            pendingEntries = selected;
            totalVnd = total;
            expectedXu = xu;
            buyerName = senderName;
            state = STATE_WAITING;
            tradeStartTime = Class_bb.b();
            quoteMsg = "Gia " + qty + " nick loai " + loai + ": " + total + " VND = " + xu + " xu. Dang moi giao dich...";
            sendPrivate(senderName, quoteMsg);
            final String inviteBuyer = senderName;
            new Thread(new Runnable() {
                public void run() {
                    try { Class_bb.b(1000L); } catch (Exception ignored) {}
                    if (state != STATE_WAITING || !inviteBuyer.equals(buyerName)) return;
                    sendInviteOpcode43(inviteBuyer);
                }
            }).start();
            return true;
        } else {
            List<NikEntry> avail = store.getChuaBan();
            if (avail.size() < qty) {
                sendPrivate(senderName, "Chi con " + avail.size() + " nick chua ban.");
                return true;
            }
            selected = new ArrayList<NikEntry>();
            for (int i = 0; i < qty; i++) selected.add(avail.get(i));
        }

        long total = 0;
        for (NikEntry e : selected) total += e.giaVnd;
        int vndPerXu = NikStore.resolveVndPerXu(Class_cu.g);
        if (vndPerXu <= 0) vndPerXu = 1000;
        int xu = (int) ((total + vndPerXu - 1) / vndPerXu); // ceil

        // store pending
        pendingEntries = selected;
        totalVnd = total;
        expectedXu = xu;
        buyerName = senderName;
        state = STATE_WAITING;
        tradeStartTime = Class_bb.b();

        // price quote
        sendPrivate(senderName, "Gia " + qty + " nick: " + total + " VND = " + xu + " xu. Dang moi giao dich...");

        // invite trade after short delay on new thread (avoid blocking packet thread)
        final String inviteBuyer = senderName;
        new Thread(new Runnable() {
            public void run() {
                try { Class_bb.b(1000L); } catch (Exception ignored) {}
                if (state != STATE_WAITING || !inviteBuyer.equals(buyerName)) return;
                sendInviteOpcode43(inviteBuyer);
            }
        }).start();

        return true;
    }

    private void sendInviteOpcode43(String targetName) {
        try {
            Class_ai buyerAi = cd.h.a(targetName, cd);
            if (buyerAi == null || buyerAi.e == 0) {
                sendPrivate(targetName, "Khong tim thay ban trong map. Vui long dung gan bot roi chat lai.");
                reset();
                return;
            }
            try { cd.h.a(buyerAi.aa, buyerAi.ab); } catch (Exception ignored) {}
            Class_bb.b(1200L);
            int buyerId = buyerAi.e;
            Class_ba ba = cd.l;
            Class_bo bo = null;
            try {
                bo = new Class_bo((byte)43);
                bo.c.writeInt(buyerId);
                ba.a.i.a(bo);
            } finally {
                if (bo != null) bo.b();
            }
            cd.a("NIK: Gui loi moi giao dich den " + targetName + " id=" + buyerId, false);
        } catch (Exception e) {
            cd.a("NIK: Loi moi gd: " + e.getMessage(), false);
            reset();
        }
    }

    public boolean isPaymentValid(int xuKhach) {
        return xuKhach >= expectedXu;
    }

    public void onTradeWindowOpened() {
        if (state == STATE_WAITING) {
            state = STATE_IN_TRADE;
            tradeStartTime = Class_bb.b();
            cd.a("NIK: Cua so gd mo -> IN_TRADE cho " + buyerName, false);
            // offer empty trade
            try {
                cd.l.a(0, new Class_al[0]);
                cd.a("NIK: Lock trade empty offer xu=0", false);
            } catch (Exception e) {
                cd.a("NIK: Loi lock trade: " + e.getMessage(), false);
            }
        }
    }

    public void onTradeOffer(int xuKhach) {
        if (state != STATE_IN_TRADE) return;
        if (isPaymentValid(xuKhach)) {
            try { cd.l.c(); } catch (Exception ignored) {}
            state = STATE_CONFIRMED;
            cd.a("NIK: Xac nhan gd xuKhach=" + xuKhach + " expected=" + expectedXu, false);
        } else {
            sendPrivate(buyerName, "Thieu xu! Can " + expectedXu + " xu nhung chi co " + xuKhach + ".");
            try { cd.l.b(); } catch (Exception ignored) {}
            reset();
        }
    }

    public void onTradeComplete(int xuNhan) {
        if (state != STATE_CONFIRMED && state != STATE_IN_TRADE) return;
        // deliver: move pendingEntries to DA_BAN
        List<NikEntry> toDeliver = pendingEntries != null ? new ArrayList<NikEntry>(pendingEntries) : null;
        String buyer = buyerName;
        if (toDeliver != null && buyer != null) {
            synchronized (store) {
                // mark sold and save
                // takeFifo already sets fields, but we already have selected list; update those objects
                for (NikEntry e : toDeliver) {
                    // ensure entries are marked (they are references to same objects in store)
                    e.trangThai = 1;
                    e.nguoiMua = buyer;
                }
                try { store.save(Class_cu.g); } catch (Exception ex) {
                    cd.a("NIK: Loi luu file: " + ex.getMessage(), false);
                }
                NikStore.appendSoldLog(Class_cu.g, toDeliver);
            }
            // send private TK/MK
            for (NikEntry e : toDeliver) {
                sendPrivate(buyer, "NICK: TK=" + e.tk + " MK=" + e.mk + " VatPham=" + e.vatPham + " Gia=" + e.giaVnd);
            }
            cd.a("NICK: Giao " + toDeliver.size() + " nick cho " + buyer, false);
            // refresh UI on EDT
            SwingUtilities.invokeLater(new Runnable() { public void run() { try { Class_cu.a().a.repaint(); } catch (Exception ignored) {} } });
            try { NikFrame.refreshIfVisible(); } catch (Exception ignored) {}
        }
        reset();
    }

    public void onTradeCancelled() {
        if (state != STATE_IDLE) {
            cd.a("NIK: Giao dich bi huy", false);
            reset();
        }
    }

    public void reset() {
        state = STATE_IDLE;
        buyerName = null;
        totalVnd = 0L;
        expectedXu = 0;
        pendingEntries = null;
        tradeStartTime = 0L;
    }

    private void sendPrivate(String to, String msg) {
        try { cd.l.a(to, msg); } catch (Exception ignored) {}
    }
}
