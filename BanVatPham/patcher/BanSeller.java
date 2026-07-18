import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * BanSeller - State machine ban vat pham P2P cho Ninja School Online.
 * Duoc goi tu Class_cq (case -22 nhan chat rieng, case 45 nhan xu khach)
 * va Class_cp (tra loi loi moi giao dich tu khach).
 *
 * Mot instance BanSeller gan voi mot Class_cd (1 nhan vat bot).
 */
public final class BanSeller {

    // Trang thai giao dich
    public static final int STATE_IDLE       = 0;
    public static final int STATE_WAITING    = 1; // Da gui bao gia, cho khach chap nhan loi moi
    public static final int STATE_IN_TRADE   = 2; // Da vao giao dich (opcode 44 nhan), cho khach bỏ xu
    public static final int STATE_CONFIRMED  = 3; // Da xac nhan (opcode 46)

    // Tham chieu toi nhan vat bot
    private Class_cd cd;

    // Config ban: List cac BanItem {id, donGia, maxSoLuong}
    private List<BanItem> priceList = new ArrayList<BanItem>();

    // Trang thai giao dich hien tai
    public int state = STATE_IDLE;
    public String buyerName = null;    // Ten khach hang dang giao dich
    public int orderQty   = 0;         // So luong khach yeu cau
    public int orderItemId = 0;        // Item id dang ban
    public int expectedXu  = 0;        // Tong gia can nhan
    public long tradeStartTime = 0;    // Time bat dau giao dich (cho timeout)
    public int prevXu = 0;             // Xu bot truoc giao dich, de tinh delta khi log

    // Slots trong bag bot se bo vao trade
    private Class_al[] tradeSlots = null;


    // Rate-limit gui huong dan (chong spam): luu time gui cuoi theo tung khach
    private java.util.HashMap<String, Long> lastHelpTime = new java.util.HashMap<String, Long>();

    public BanSeller(Class_cd cd) {
        this.cd = cd;
    }

    // ================================================================
    // RAO LOA CONG CONG: sinh cau rao tu priceList roi bat may rao co san
    // trong Class_ce (gui opcode -23 moi f.i giay, xoay vong f.j[t]).
    // Class_ce tu them tien to "@<random>: " truoc moi cau. Dang ban
    // (state != 0) thi Class_cp.a() tra true -> Class_ce break truoc khi
    // rao -> tu dong ngung loa luc giao dich.
    // Goi tu dau Class_cp.a() moi vong; chi thuc su set up 1 lan.
    // ================================================================
    private boolean broadcastReady = false;
    public void ensureBroadcast() {
        if (broadcastReady) return;
        try {
            ensureConfigLoaded();
            if (priceList == null || priceList.isEmpty()) return; // chua san sang, thu lai sau
            // 1 mon/cau: "bán <maVp> <gia>xu". Khong dung "/" vi font loa cong
            // cong khong hien thi "/" (bi nuot -> "100/1" thanh "1001").
            java.util.List<String> lines = new ArrayList<String>();
            for (BanItem it : priceList) {
                if (it.maVp == null || it.maVp.isEmpty()) continue; // bo mon khong co ma vp
                lines.add("bán " + it.maVp + " " + formatXu(it.donGia) + "xu");
            }
            if (lines.isEmpty()) return;              // khong mon nao co ma vp
            cd.f.j = lines.toArray(new String[0]);    // danh sach cau rao (Class_ce xoay vong)
            cd.f.i = 10;                              // 10 giay/lan
            cd.f.h = true;                            // bat rao
            broadcastReady = true;
            cd.a("BAN: Bat rao cong cong " + lines.size() + " cau, moi 10s", false);
        } catch (Exception e) {}
    }

    /**
     * Lazy load config tu file items/<itemFile>.txt trong thu muc lam viec.
     * Duoc goi truoc moi request.
     */
    // ================================================================
    // Tom tat kho cho bang GUI (Class_cw): "d7=2, d8=13, d9=12".
    // Doc so luong THAT trong tui (countAvailable). Bo mon khong co ma vp.
    // Chua nap bang gia / khong co mon -> tra "-".
    // ================================================================
    public String getStockSummary() {
        try {
            ensureConfigLoaded();
            if (priceList == null || priceList.isEmpty()) return "-";
            StringBuilder sb = new StringBuilder();
            for (BanItem it : priceList) {
                if (it.maVp == null || it.maVp.isEmpty()) continue;
                int id = it.id & 0xFFFF;
                if (sb.length() > 0) sb.append(", ");
                sb.append(it.maVp).append("=").append(countAvailable(id));
            }
            return sb.length() == 0 ? "-" : sb.toString();
        } catch (Exception e) {
            return "-";
        }
    }

    private boolean configLoaded = false;
    private void ensureConfigLoaded() {
        if (configLoaded) return;
        try {
            // Tim itemFile tu Class_ch cua cd (giong cach Class_cu.a() load)
            // Class_cu.g la working dir
            String workDir = Class_cu.g;
            if (workDir == null || workDir.isEmpty()) return;
            // Doc banvatpham.txt de lay ten item file
            String banCfg = Class_cq.d(workDir + "\\banvatpham.txt");
            if (banCfg == null) {
                // Fallback: dung file items cua cd neu co (giong muado.txt)
                // Tim trong Class_ch.a cache
                cd.a("BAN: banvatpham.txt khong tim thay, dung items tu muado.txt", false);
                // Doc tu items/ theo cung ten file nhu muado config
                // Lay item file name tu cd.f.g la bao gia mua, dung lam bao gia ban
                loadFromPriceList();
                configLoaded = true;
                return;
            }
            // Tim DUNG dong tai khoan cua bot nay (khop TK == cd.b) roi lay [ITEM]
            // cua rieng dong do. Neu chi lay Class_bb.a(banCfg,"ITEM") thi luon ra
            // tag [ITEM] DAU TIEN -> moi bot deu doc chung 1 bang gia (sai khi
            // nhieu tai khoan ban mat hang khac nhau).
            String itemFile = null;
            String[] banLines = banCfg.split("\\[GROUP\\]");
            outer:
            for (String block : banLines) {
                String[] lns = block.split("\r\n|\n");
                for (String ln : lns) {
                    if (ln.trim().isEmpty()) continue;
                    String tk = Class_bb.a(ln, "TK");
                    if (tk != null && tk.equals(cd.b)) {
                        itemFile = Class_bb.a(ln, "ITEM");
                        break outer;
                    }
                }
            }
            // Fallback: khong khop TK -> dung tag ITEM dau tien (hanh vi cu)
            if (itemFile == null) itemFile = Class_bb.a(banCfg, "ITEM");
            if (itemFile == null) {
                // Khong co tag ITEM -> fallback dung file tu muado
                loadFromPriceList();
                configLoaded = true;
                return;
            }
            cd.a("BAN: TK=" + cd.b + " dung item file: " + itemFile, false);
            String itemContent = Class_cq.d(workDir + "\\items\\" + itemFile + ".txt");
            if (itemContent != null) {
                loadConfig(itemContent);
                cd.a("BAN: Loaded " + priceList.size() + " items tu " + itemFile + ".txt", false);
            }
        } catch (Exception e) {}
        configLoaded = true;
    }

    /**
     * Fallback: dung chinh bảng giá muado (Class_ch.g) lam bang gia ban.
     */
    private void loadFromPriceList() {
        try {
            priceList.clear();
            for (int i = 0; i < cd.f.g.size(); i++) {
                Class_am am = (Class_am) cd.f.g.get(i);
                if (am != null) {
                    priceList.add(new BanItem(am.a, am.b, 999, ""));
                }
            }
            cd.a("BAN: Loaded " + priceList.size() + " items tu muado price list", false);
        } catch (Exception e) {}
    }
    public void loadConfig(String configContent) {
        priceList.clear();
        if (configContent == null) return;
        String[] lines = configContent.split("\r\n|\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    short id = Short.parseShort(parts[0].trim());
                    int donGia = Integer.parseInt(parts[1].trim());
                    int maxQty = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : 999;
                    String maVp = parts.length >= 4 ? parts[3].trim() : "";
                    priceList.add(new BanItem(id, donGia, maxQty, maVp));
                }
            } catch (Exception e) {
                // bo qua dong loi
            }
        }
    }

    // ================================================================
    // Xu ly tin nhan rieng tu khach (opcode -22 nhan)
    // Format: "ban <id> <so_luong>"  hoac  "ban <so_luong>" neu chi co 1 sp trong priceList
    // ================================================================
    public void onPrivateMessage(String senderName, String content) {
        // LOG DEBUG: xac nhan ham co duoc goi khong + tham so
        cd.a("BAN-DEBUG onPrivateMessage: from=[" + senderName + "] content=[" + content + "] state=" + state, false);
        if (content == null || senderName == null) return;
        // Lazy load config khi can
        ensureConfigLoaded();
        content = content.trim().toLowerCase();

        // TU PHUC HOI: tick() (chua timeout) chi chay khi co cua so giao dich mo.
        // Neu bot ket trang thai (WAITING/IN_TRADE/CONFIRMED) qua 90s ma khong xong
        // -> tu reset ve IDLE de khong bi "im re" vinh vien.
        if (state != STATE_IDLE && tradeStartTime > 0
                && Class_bb.b() - tradeStartTime > 90000L) {
            cd.a("BAN: Tu phuc hoi - reset state ket qua lau", false);
            reset();
        }

        // Bo qua neu dang busy voi khach khac
        if (state != STATE_IDLE) {
            if (!senderName.equals(buyerName)) {
                sendPrivate(senderName, "Bot dang ban cho nguoi khac, vui long cho.");
                return;
            }
            // Cung khach, dang STATE_WAITING: kiem tra tu khoa xac nhan de bot gui opcode 43
            if (state == STATE_WAITING) {
                if (isConfirmKeyword(content)) {
                    sendInviteOpcode43();
                    return;
                }
                // Khach nhan lai lenh ban moi (vi du "ban 101 5"): cho tiep tuc xuong duoi xu ly
            } else {
                // STATE_IN_TRADE hoac STATE_CONFIRMED: bo qua
                return;
            }
        }

        // Parse lenh: "<sl> <mavp>" hoac "<mavp> <sl>" (thu tu tuy y)
        // Kiem tra findByMaVp TRUOC loc spam, de don that ("12 d7") khong bi chan nham.
        String[] tokens = content.split("\\s+");
        BanItem banItem = null;
        int qty = 0;
        for (String tok : tokens) {
            if (banItem == null) {
                BanItem found = findByMaVp(tok);
                if (found != null) { banItem = found; continue; }
            }
            if (qty == 0) {
                try {
                    int n = Integer.parseInt(tok);
                    if (n > 0) { qty = n; continue; }
                } catch (NumberFormatException ex) {}
            }
        }

        // Khong khop ma vp nao -> gui huong dan (neu khong phai spam)
        if (banItem == null) {
            if (!isSpamMessage(content)) sendHelp(senderName);
            return;
        }
        // Co ma vp nhung thieu so luong
        if (qty <= 0) {
            sendPrivate(senderName, "Vui long ghi so luong, vd: 12 " + banItem.maVp);
            return;
        }

        int itemId = (int)(banItem.id & 0xFFFF);

        if (qty > banItem.maxQty) {
            sendPrivate(senderName, "So luong toi da: " + banItem.maxQty);
            return;
        }

        // Kiem tra kha nang giao: dua HOAN TOAN vao so luong that trong tui (al.e),
        // KHONG doan gop/khong-gop qua type byte (khong dang tin: d9 type bao gop
        // nhung thuc te moi vien 1 o). checkFulfill mo phong dung logic build.
        orderItemId = itemId;   // set truoc de collectMatches dung
        orderQty    = qty;
        int fc = checkFulfill(qty);
        if (fc == -1) {
            sendPrivate(senderName, "Bot khong du hang (co " + countAvailable(itemId) + ")");
            return;
        }
        if (fc == -2) {
            sendPrivate(senderName, "So luong qua nhieu, khong du 12 o de bo vao giao dich.");
            return;
        }

        // Tinh gia
        int totalXu = banItem.donGia * qty;

        // Bao gia (khong con doi xac nhan - tu moi trade sau 1s)
        sendPrivate(senderName, "S/L " + qty + " x " + formatXu(banItem.donGia) + " = " + formatXu(totalXu) + " xu. Dang moi giao dich...");

        // Luu trang thai (phai set truoc khi goi sendInviteOpcode43 vi no yeu cau STATE_WAITING)
        state        = STATE_WAITING;
        buyerName    = senderName;
        orderQty     = qty;
        orderItemId  = (int)(banItem.id & 0xFFFF);
        expectedXu   = totalXu;
        tradeStartTime = Class_bb.b();

        // Bo qua buoc xac nhan: cho 1s roi tu dong moi giao dich (opcode 43).
        // QUAN TRONG: onPrivateMessage chay tren THREAD NHAN GOI TIN. Khong duoc
        // sleep/block o day (se lam bot "diec" khong xu ly duoc goi khac). Day viec
        // cho + moi trade sang thread rieng.
        final String inviteBuyer = senderName;
        new Thread(new Runnable() {
            public void run() {
                try {
                    Class_bb.b(1000L);
                    if (state != STATE_WAITING || !inviteBuyer.equals(buyerName)) return;
                    // TACH STACK TRUOC KHI MOI (con dang WAITING, cua so gd CHUA mo).
                    // Server BO QUA lenh tach (-85) khi cua so gd DANG mo -> phai tach
                    // xong o day roi moi m/ moi. Do khong gop (moi o 1 cai) -> khong
                    // can tach, prepareStock tra ngay.
                    if (!prepareStock()) {
                        sendPrivate(buyerName, "Loi: bot khong chuan bi duoc hang (thu lai).");
                        reset();
                        return;
                    }
                    if (state == STATE_WAITING && inviteBuyer.equals(buyerName)) {
                        sendInviteOpcode43();
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    /**
     * Chuan bi hang TRUOC khi vao giao dich: neu gom o nguyen chua du orderQty
     * (do gop can tach 1 phan chong lon) -> gui lenh tach (-85) va cho opcode 8
     * cap nhat, lap toi khi selectWholeSlots() du (hoac het luot). CHI goi luc
     * con WAITING (cua so gd chua mo) vi server bo qua tach khi dang giao dich.
     * Chay tren THREAD MOI (khong phai thread nhan goi) nen duoc phep sleep/poll.
     * Tra true neu da san sang (gom du bang o nguyen).
     */
    private boolean prepareStock() {
        for (int attempt = 0; attempt < 15; attempt++) {
            if (selectWholeSlots() != null) return true; // da du bang o nguyen
            int remaining = shortfallAfterWholeSlots();
            if (remaining <= 0) return true;
            // Tim chong nho nhat co e > remaining de tach dung phan con thieu
            Class_al best = null;
            for (int i = 0; i < cd.q.v.length; i++) {
                Class_al al = cd.q.v[i];
                if (al == null || al.c) continue;
                if (al.b != null && (al.b.a & 0xFFFF) == orderItemId && al.e > remaining) {
                    if (best == null || al.e < best.e) best = al;
                }
            }
            if (best == null) return false; // khong co chong du de tach
            int slot = best.a;
            Class_ba ba = cd.l;
            Class_bo bo = null;
            try {
                bo = Class_ba.b((byte)-85);
                bo.c.writeByte(slot);
                bo.c.writeInt(remaining);
                ba.a.i.a(bo);
            } catch (Exception e) {
            } finally {
                if (bo != null) bo.b();
            }
            cd.a("BAN: Chuan bi - tach slot=" + slot + " qty=" + remaining + " (chong co " + best.e + ")", false);
            Class_bb.b(700L); // cho opcode 8 cap nhat tui
        }
        return selectWholeSlots() != null;
    }

    // ================================================================
    // Xu ly khi khach chap nhan loi moi (opcode 43 nhan - khach xac nhan moi)
    // Duoc goi tu Class_cp.a(Class_ai) khi mode ban
    // ================================================================
    public boolean onInviteAccept(Class_ai buyerAi) {
        if (state != STATE_WAITING) return false;
        if (buyerAi == null) return false;
        if (!buyerAi.i.equals(buyerName)) return false;

        // Check timeout 60s tu luc bao gia
        if (Class_bb.b() - tradeStartTime > 60000L) {
            reset();
            return false;
        }

        // === CACH 3: Dump id that cua khach khi nhan invite ===
        // Dump tat ca field co the la id de doi chieu voi rawpackets.txt
        try {
            String rawIdDump = "BAN-RAWID invite-received name=[" + buyerAi.i + "]"
                + " .e=" + buyerAi.e
                + " .j=" + buyerAi.j
                + " .k=" + buyerAi.k
                + " .l=" + buyerAi.l
                + " .m=" + buyerAi.m
                + " .s=" + buyerAi.s
                + " .t=" + buyerAi.t
                + " .u=" + buyerAi.u
                + " .aa=" + buyerAi.aa
                + " .ab=" + buyerAi.ab
                + " .ac=" + buyerAi.ac
                + " .ad=" + buyerAi.ad
                + " .ae=" + buyerAi.ae
                + " .af=" + buyerAi.af;
            cd.a(rawIdDump, false);
            writeLog("bandebug/rawpackets.txt",
                "INVITE " + rawIdDump + "\r\n");
        } catch (Exception ex) {}
        // === END DUMP ===

        // Chap nhan loi moi (opcode 44)
        prevXu = cd.q.s;          // luu xu bot truoc giao dich de tinh delta
        cd.l.a(buyerAi.e);
        state = STATE_IN_TRADE;
        tradeStartTime = Class_bb.b();
        cd.a("BAN: Chap nhan loi moi tu " + buyerName, false);
        return true;
    }

    // ================================================================
    // Duoc goi khi bot nhan duoc loi moi giao dich (opcode 43)
    // neu dang o trang thai WAITING thi chap nhan, nguoc lai tu choi
    // ================================================================
    public boolean shouldAcceptInviteFrom(Class_ai ai) {
        if (state == STATE_WAITING && ai != null && ai.i.equals(buyerName)) {
            return true;
        }
        return false;
    }

    // ================================================================
    // Duoc goi tu Class_cp.c() KHI CUA SO GIAO DICH MO (server xac nhan
    // trade da bat dau - du bot GUI moi hay khach GUI moi).
    // Chuyen WAITING -> IN_TRADE de tick() bat dau bo do.
    // Vi bot CHU DONG moi (opcode 43 GUI), khong co su kien "nhan moi"
    // (onInviteAccept) de set IN_TRADE -> phai set o day.
    // ================================================================
    public void onTradeWindowOpened() {
        if (state == STATE_WAITING) {
            prevXu = cd.q.s;               // xu bot truoc gd, de tinh delta khi log
            state = STATE_IN_TRADE;
            tradeSlots = null;             // buoc tick() build lai + bo do
            tradeStartTime = Class_bb.b();
            cd.a("BAN: Cua so gd mo -> IN_TRADE, bat dau bo do cho " + buyerName, false);
        }
    }

    // ================================================================
    // Xu ly case 45: khach da dat do + xu vao trade
    // Duoc goi tu Class_cq case 45
    // ================================================================
    public void onTradeOffer(int xuKhach) {
        if (state != STATE_IN_TRADE) return;

        // Bot da bỏ do vao (duoc goi tu a() loop), bay gio nhan xu
        if (xuKhach >= expectedXu) {
            // Du xu -> xac nhan
            cd.l.c();
            cd.a("BAN: Xac nhan giao dich " + buyerName + " xu=" + formatXu(xuKhach), false);
            state = STATE_CONFIRMED;
        } else {
            // Thieu xu -> bao va huy
            sendPrivate(buyerName, "Thieu xu! Can " + formatXu(expectedXu) + " nhung chi nhan " + formatXu(xuKhach));
            cd.l.b();
            reset();
        }
    }

    // ================================================================
    // Khi giao dich hoan thanh (opcode 58 - server xac nhan xong)
    // Duoc goi tu Class_cq case 58
    // ================================================================
    public void onTradeComplete(int xuNhan) {
        if (state == STATE_CONFIRMED || state == STATE_IN_TRADE) {
            logSale(xuNhan);
            // Xoa cac o da ban khoi tui RAM (server KHONG tu gui cap nhat tui sau trade ban).
            // Phai lam TRUOC reset() vi reset() se set tradeSlots = null.
            int xoaCount = 0;
            if (tradeSlots != null) {
                for (int i = 0; i < tradeSlots.length; i++) {
                    try {
                        if (tradeSlots[i] != null) {
                            cd.q.v[tradeSlots[i].a & 0xFF] = null;
                            xoaCount++;
                        }
                    } catch (Exception ex) {}
                }
                try { cd.q.b(); } catch (Exception ex) {}
            }
            cd.a("BAN: Xoa " + xoaCount + " o da ban khoi tui", false);
        }
        reset();
    }

    // ================================================================
    // Khi giao dich bi huy (opcode 57)
    // ================================================================
    public void onTradeCancelled() {
        if (state != STATE_IDLE) {
            cd.a("BAN: Giao dich bi huy", false);
        }
        reset();
    }

    // ================================================================
    // Tick logic ban: duoc goi tu vong lap chinh (Class_cp.a() hoac
    // Class_ce.run()) moi 50ms
    // Phan cong viec: neu dang STATE_IN_TRADE va chua put do -> put do
    // ================================================================
    public void tick() {
        if (state == STATE_IDLE) return;

        // Timeout: qua 90s khong xong -> huy
        if (state != STATE_CONFIRMED && Class_bb.b() - tradeStartTime > 90000L) {
            cd.a("BAN: Timeout giao dich, huy", false);
            if (state == STATE_IN_TRADE) cd.l.b();
            sendPrivate(buyerName, "Qua thoi gian thao tac (toi da 90 giay).");
            reset();
            return;
        }

        // Neu dang trong giao dich va chua build tradeSlots -> gom do roi lock.
        // Hang da duoc TACH SAN o prepareStock() TRUOC khi mo cua so gd (server
        // bo qua lenh tach khi dang giao dich), nen o day chi con gom o nguyen.
        if (state == STATE_IN_TRADE && tradeSlots == null && cd.h.b == 1) {
            fulfill();
        }
    }

    /**
     * Gom cac o NGUYEN cho du orderQty va lock giao dich.
     * Hang da tach san (prepareStock) truoc khi vao trade -> tai day chac chan
     * selectWholeSlots() gom du bang o nguyen. Neu van thieu (hiem: tui thay doi)
     * -> bao loi + huy, KHONG tach trong trade (server bo qua).
     */
    private void fulfill() {
        Class_al[] slots = selectWholeSlots();
        if (slots != null && slots.length > 0) {
            tradeSlots = slots;
            lockAndOffer();
            return;
        }
        sendPrivate(buyerName, "Loi: bot khong gom duoc du hang (thu lai).");
        cd.l.b();
        reset();
    }

    // ================================================================
    // Helper: sau khi split xong (opcode 8 ve) trong luc dang trade.
    // Thuong hang da tach san truoc trade nen day chi la fallback.
    // Duoc goi tu Class_cq case 8
    // ================================================================
    public void onItemUpdated() {
        if (state == STATE_IN_TRADE && tradeSlots == null && cd.h.b == 1) {
            fulfill();
        }
    }

    // ================================================================
    // Lock giao dich + bo do vao (opcode 45, xu bot = 0)
    // ================================================================
    private void lockAndOffer() {
        if (tradeSlots == null) return;
        cd.l.a(0, tradeSlots);
        cd.a("BAN: Lock trade voi " + slotCount(tradeSlots) + " items, xu=0", false);
    }

    // ================================================================
    // Gom cac o NGUYEN cho du orderQty (greedy: uu tien o lon nhat <= con thieu).
    // KHONG doan gop/khong-gop qua type byte. Chi dua vao al.e that trong tui.
    //   - Tra mang o (sum == orderQty, <=12 o) neu gom du bang o nguyen.
    //   - Tra null neu con thieu -> can tach 1 phan cua chong lon (doSplitStack).
    //   - Tra mang RONG (length 0) neu tui khong co mon nao -> loi that.
    // ================================================================
    private Class_al[] selectWholeSlots() {
        java.util.List<Class_al> matches = collectMatches();
        if (matches.isEmpty()) return new Class_al[0];
        java.util.List<Class_al> chosen = new ArrayList<Class_al>();
        int remaining = orderQty;
        boolean[] used = new boolean[matches.size()];
        while (remaining > 0 && chosen.size() < 12) {
            // Tim o lon nhat co e <= remaining chua dung
            int bestIdx = -1;
            for (int i = 0; i < matches.size(); i++) {
                if (used[i]) continue;
                int e = matches.get(i).e;
                if (e <= remaining && (bestIdx == -1 || e > matches.get(bestIdx).e)) bestIdx = i;
            }
            if (bestIdx == -1) break; // khong o nao vua -> can tach
            used[bestIdx] = true;
            chosen.add(matches.get(bestIdx));
            remaining -= matches.get(bestIdx).e;
        }
        if (remaining == 0) return chosen.toArray(new Class_al[0]);
        return null; // con thieu -> tach
    }

    // Phan con thieu sau khi gom het o nguyen (dung greedy giong selectWholeSlots).
    private int shortfallAfterWholeSlots() {
        java.util.List<Class_al> matches = collectMatches();
        int remaining = orderQty;
        int slotsUsed = 0;
        boolean[] used = new boolean[matches.size()];
        while (remaining > 0 && slotsUsed < 12) {
            int bestIdx = -1;
            for (int i = 0; i < matches.size(); i++) {
                if (used[i]) continue;
                int e = matches.get(i).e;
                if (e <= remaining && (bestIdx == -1 || e > matches.get(bestIdx).e)) bestIdx = i;
            }
            if (bestIdx == -1) break;
            used[bestIdx] = true;
            slotsUsed++;
            remaining -= matches.get(bestIdx).e;
        }
        return remaining;
    }

    // Kiem tra kha nang giao truoc khi nhan don: 0=OK, -1=thieu hang, -2=can >12 o.
    private int checkFulfill(int qty) {
        java.util.List<Class_al> matches = collectMatches();
        int total = 0;
        for (Class_al al : matches) total += al.e;
        if (total < qty) return -1;
        // Mo phong greedy gom o nguyen
        int remaining = qty;
        int slotsUsed = 0;
        boolean[] used = new boolean[matches.size()];
        while (remaining > 0 && slotsUsed < 12) {
            int bestIdx = -1;
            for (int i = 0; i < matches.size(); i++) {
                if (used[i]) continue;
                int e = matches.get(i).e;
                if (e <= remaining && (bestIdx == -1 || e > matches.get(bestIdx).e)) bestIdx = i;
            }
            if (bestIdx == -1) break;
            used[bestIdx] = true;
            slotsUsed++;
            remaining -= matches.get(bestIdx).e;
        }
        if (remaining == 0) return 0;              // du bang o nguyen
        // Con thieu -> can tach them 1 o (total>=qty nen chac co chong de tach)
        if (slotsUsed + 1 <= 12) return 0;
        return -2;                                 // qua nhieu o
    }

    // Gom cac o trong tui khop orderItemId, chua khoa (al.c), co e>0.
    private java.util.List<Class_al> collectMatches() {
        java.util.List<Class_al> list = new ArrayList<Class_al>();
        for (int i = 0; i < cd.q.v.length; i++) {
            Class_al al = cd.q.v[i];
            if (al == null || al.c) continue;
            if (al.b != null && (al.b.a & 0xFFFF) == orderItemId && al.e > 0) list.add(al);
        }
        return list;
    }

    private Class_ap getTemplate(int id) {
        try {
            return cd.d.b((short)id);
        } catch (Exception e) {
            return null;
        }
    }

    // Tong so mon that su co trong tui (tong al.e cua cac o khop).
    private int countAvailable(int itemId) {
        int total = 0;
        for (Class_al al : cd.q.v) {
            if (al == null || al.c) continue;
            if (al.b != null && (al.b.a & 0xFFFF) == itemId) total += al.e;
        }
        return total;
    }

    private BanItem findItem(int id) {
        for (BanItem item : priceList) {
            if ((item.id & 0xFFFF) == (id & 0xFFFF)) return item;
        }
        return null;
    }

    private BanItem findByMaVp(String maVp) {
        if (maVp == null || maVp.isEmpty()) return null;
        for (BanItem item : priceList) {
            if (item.maVp != null && !item.maVp.isEmpty() && item.maVp.equalsIgnoreCase(maVp)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Kiem tra tu khoa xac nhan (khong phan biet hoa thuong, co/khong dau).
     * Chap nhan: "xac nhan", "xacnhan", "ok", "co", "dong y", "dongy", "yes"
     */
    private boolean isConfirmKeyword(String content) {
        if (content == null) return false;
        // content da duoc trim().toLowerCase() truoc khi truyen vao
        // Chuan hoa: bo dau tieng Viet de so sanh ASCII
        String s = content.trim().toLowerCase();
        return s.equals("xac nhan") || s.equals("xacnhan")
            || s.equals("ok") || s.equals("co")
            || s.equals("dong y") || s.equals("dongy")
            || s.equals("yes") || s.equals("chap nhan")
            || s.equals("chapnhan");
    }

    /**
     * Bot chu dong gui loi moi giao dich opcode 43 den khach (buyerName).
     * Chua co Class_bo truc tiep trong BanSeller -> dung pattern tu Class_ce:
     *   cd.h.a(buyerName, cd) -> Class_ai voi playerId
     *   new Class_bo(43) + writeInt(playerId) + send
     */
    private void sendInviteOpcode43() {
        try {
            // DEBUG: dump toan bo player trong danh sach map de biet ten/id thuc te
            try {
                StringBuilder dbg = new StringBuilder("BAN-DEBUG playerList size=" + cd.h.d.size() + ": ");
                for (int di = 0; di < cd.h.d.size(); di++) {
                    Class_ai p = (Class_ai) cd.h.d.elementAt(di);
                    if (p != null && p.i != null && p.i.equals(buyerName)) {
                        // Dump TAT CA field int cua dung khach de tim id that
                        dbg.append("[FOUND ").append(p.i)
                           .append(" e=").append(p.e)
                           .append(" k=").append(p.k)
                           .append(" j=").append(p.j)
                           .append(" ae=").append(p.ae)
                           .append(" aa=").append(p.aa)
                           .append(" ab=").append(p.ab)
                           .append(" ac=").append(p.ac)
                           .append(" ad=").append(p.ad)
                           .append(" f=").append(p.f).append("] ");
                    }
                }
                cd.a(dbg.toString(), false);
                cd.a("BAN-DEBUG tim buyerName=[" + buyerName + "] botOwnId q.e=" + cd.q.e, false);
                // Ghi ra FILE de doc lai (console kho chup)
                dbg.append("\r\ntim buyerName=[").append(buyerName).append("] botOwnId q.e=").append(cd.q.e).append("\r\n");
                writeLog("bandebug/dump.txt", dbg.toString());
            } catch (Exception ex) {}
            // Tim doi tuong Class_ai cua buyerName trong danh sach player tren map
            // cd.h la Class_ci (map state), .a(String, Class_cd) tim player theo ten
            Class_ai buyerAi = cd.h.a(buyerName, cd);
            if (buyerAi == null || buyerAi.e == 0) {
                // buyerAi.e==0 = player id chua load / khong hop le -> khong moi rac
                cd.a("BAN: Khong tim thay khach hop le (buyerAi=" + (buyerAi == null ? "null" : ("id=" + buyerAi.e)) + ")", false);
                sendPrivate(buyerName, "Khong tim thay ban trong map. Vui long dung SAT canh bot roi chat lai.");
                reset();
                return;
            }
            // Di chuyen gan khach neu can thiet (giong Class_ce dong 113)
            cd.h.a(buyerAi.aa, buyerAi.ab);
            Class_bb.b(1500L);
            // Gui opcode 43 (invite trade)
            int buyerId = buyerAi.e;
            Class_ba ba = cd.l;
            Class_bo bo = null;
            try {
                bo = new Class_bo((byte)43);
                bo.c.writeInt(buyerId);
                ba.a.i.a(bo);
            } catch (Exception ex) {
            } finally {
                if (bo != null) bo.b();
            }
            cd.a("BAN: Gui loi moi giao dich (opcode 43) den " + buyerName + " id=" + buyerId, false);
            // state van la STATE_WAITING, cho server bao khach accept (opcode 43 server -> Class_cp.a(Class_ai))
        } catch (Exception e) {
            cd.a("BAN: Loi khi gui loi moi: " + e.getMessage(), false);
        }
    }

    private void sendPrivate(String toName, String msg) {
        try {
            cd.l.a(toName, msg);
        } catch (Exception e) {}
    }

    /**
     * Gui huong dan cach mua + bang gia cho khach (nhan rieng).
     * Rate-limit 15s/khach de tranh spam.
     */
    /**
     * Nhan biet tin nhan spam/loa cua bot khac (tranh tra loi -> bi server chan gui).
     * Bot spam thuong: tin dai, co url/zalo/fb/shop, hoac tien to "XyZ:" o dau.
     */
    private boolean isSpamMessage(String content) {
        if (content == null) return true;
        String s = content.toLowerCase();
        // Qua dai = khong phai khach hoi mua
        if (s.length() > 40) return true;
        // Chua tu khoa quang cao
        if (s.contains("zalo") || s.contains(" fb") || s.contains("fb ")
            || s.contains("shop") || s.contains("http") || s.contains(".net")
            || s.contains(".com") || s.contains("bán ") || s.contains("ban ")
            || s.contains("mua ib") || s.contains("@") || s.contains("momo")
            || s.contains("atm")) {
            // Tru truong hop dung la lenh mua "ban <id> <sl>" (da xu ly o tren)
            return true;
        }
        return false;
    }

    private void sendHelp(String toName) {
        try {
            cd.a("BAN-DEBUG sendHelp: to=[" + toName + "] priceListSize=" + priceList.size(), false);
            if (toName == null) return;
            long now = Class_bb.b();
            Long last = lastHelpTime.get(toName);
            if (last != null && now - last.longValue() < 15000L) {
                cd.a("BAN-DEBUG sendHelp: rate-limited (con " + (15000L - (now - last.longValue())) + "ms)", false);
                return;
            }
            lastHelpTime.put(toName, now);

            ensureConfigLoaded();
            // Lay vi du ma vp tu mon dau tien co ma vp (dung cho dong huong dan cuoi)
            String exMaVp = "d7";
            for (BanItem it : priceList) {
                if (it.maVp != null && !it.maVp.isEmpty()) { exMaVp = it.maVp; break; }
            }
            if (priceList.isEmpty()) exMaVp = "d7";
            // Truoc: liet ke tung mon (moi mon 1 tin, toi da 8 mon)
            int shown = 0;
            for (BanItem it : priceList) {
                if (it.maVp == null || it.maVp.isEmpty()) continue;
                if (shown >= 8) break;
                int id = it.id & 0xFFFF;
                Class_ap template = getTemplate(id);
                int avail = countAvailable(id);
                String tenMon = (template != null && template.d != null) ? " (" + template.d + ")" : "";
                sendPrivate(toName, "Ma VP: " + it.maVp + tenMon + " ban: " + formatXu(it.donGia) + "xu/1 cai, Co: x" + avail);
                shown++;
            }
            if (shown == 0) sendPrivate(toName, "(Chua co mat hang nao)");
            // Sau cung: dong huong dan cach mua (day xuong CUOI theo yeu cau)
            sendPrivate(toName, "Chat <so luong> <ma vp> de mua. VD: 12 " + exMaVp);
        } catch (Exception e) {}
    }

    private static String formatXu(long xu) {
        return Class_bb.a(xu);
    }

    private int slotCount(Class_al[] slots) {
        int c = 0;
        for (Class_al al : slots) { if (al != null) c++; }
        return c;
    }

    public void reset() {
        state      = STATE_IDLE;
        buyerName  = null;
        orderQty   = 0;
        orderItemId = 0;
        expectedXu = 0;
        tradeStartTime = 0;
        prevXu     = 0;
        tradeSlots = null;
    }

    // ================================================================
    // Log ban hang (giong Class_cp.e())
    // ================================================================
    private void logSale(int xuNhan) {
        try {
            int postXu = cd.q.s;
            int delta = postXu - prevXu;   // xu thuc su nhan = tong xu sau - tong xu truoc
            String log = Class_bb.c();
            log += "\r\nNhan vat: " + cd.q.i;
            log += "\r\nKhach: " + buyerName;
            log += "\r\nItem ban: " + orderItemId + " x " + orderQty;
            log += "\r\nXu nhan: " + formatXu(delta) + " xu (tong xu: " + formatXu(postXu) + ")";
            log += "\r\n==========================================\r\n";

            Calendar cal = Calendar.getInstance();
            int y  = cal.get(Calendar.YEAR);
            int m  = cal.get(Calendar.MONTH) + 1;
            int d2 = cal.get(Calendar.DAY_OF_MONTH);
            String path = "logs/" + cd.d.a + "/" + cd.q.i + "/";
            String filename = "ban-" + y + "-" + Class_bb.b(m) + "-" + Class_bb.b(d2) + ".txt";
            String fullPath = path + filename;
            writeLog(fullPath, log);
        } catch (Exception e) {}
    }

    private void writeLog(String relPath, String content) {
        try {
            String base = "C:/NinjaLite/GomItem/";
            String dir = base + relPath.substring(0, relPath.lastIndexOf("/"));
            File fDir = new File(dir);
            if (!fDir.exists()) fDir.mkdirs();
            File f = new File(base + relPath);
            String existing = "";
            if (f.exists()) {
                InputStream is = new FileInputStream(f);
                byte[] buf = new byte[is.available()];
                is.read(buf);
                is.close();
                existing = new String(buf, "UTF-8");
            }
            OutputStream os = new FileOutputStream(f);
            os.write((existing + content).getBytes("UTF-8"));
            os.flush();
            os.close();
        } catch (Exception e) {}
    }

    // ================================================================
    // Inner class config item
    // ================================================================
    public static class BanItem {
        public short id;
        public int donGia;
        public int maxQty;
        public String maVp;
        public BanItem(short id, int donGia, int maxQty, String maVp) {
            this.id = id;
            this.donGia = donGia;
            this.maxQty = maxQty;
            this.maVp = maVp;
        }
    }
}
