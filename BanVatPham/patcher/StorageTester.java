import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * StorageTester - Xac minh chuoi rut ruong chinh xac theo client NSO.
 *
 * Chuoi day du (theo aj.java tab j==4):
 *   1. Tim NPC giu do type 5: cd.h.d(5)
 *   2. Di chuyen + poll vi tri (loop move chong Class_ce keo ve)
 *   3. Gui opcode 29 (5,0,0) chao NPC mo phien kho
 *   4. Gui i(4) mo ruong, poll cd.q.x != null
 *   5. Dump .x + dem o tui trong
 *   6. Rut opcode 16 writeByte(slot), poll tui, log delta
 *   7. Neu 16 NO EFFECT -> thu opcode 15 va log ket qua rieng
 *
 * Usage: goi runTest() tu lenh "testkho" trong BanSeller.onPrivateMessage
 * Log: C:/NinjaLite/GomItem/bandebug/storage_test.txt
 */
public final class StorageTester {

    private Class_cd cd;

    private String logFile = "bandebug/storage_test.txt";
    private boolean testRunning = false;

    // Test state (set during runTestSequence)
    private int testSlot   = -1;
    private int testItemId = -1;

    public StorageTester(Class_cd cd) {
        this.cd = cd;
    }

    /**
     * Main entry point -- call from BanSeller.onPrivateMessage("testkho")
     * Runs entirely in a background thread; safe to call from packet-receiver thread.
     */
    public void runTest() {
        if (testRunning) {
            cd.a("STORAGE_TEST: Test da dang chay, vui long doi", false);
            return;
        }

        testRunning = true;
        log("=== BAT DAU TEST STORAGE (CHUOI CLIENT DAY DU) ===");
        log("Thoi gian: " + Class_bb.c());
        log("Nhan vat: " + cd.q.i);

        new Thread(new Runnable() {
            public void run() {
                try {
                    runTestSequence();
                } catch (Exception e) {
                    log("LOI NGOAI LE: " + e.getMessage());
                    if (e.getStackTrace() != null && e.getStackTrace().length > 0) {
                        log("  at " + e.getStackTrace()[0].toString());
                    }
                } finally {
                    testRunning = false;
                    log("=== KET THUC TEST ===");
                }
            }
        }).start();
    }

    // ================================================================
    // Test sequence chinh
    // ================================================================

    private void runTestSequence() {

        // ---- Phase 0: Dump NPC list ----
        log("=== DUMP NPC (cd.h.f) ===");
        log("BOT pos: aa=" + cd.q.aa + " ab=" + cd.q.ab);
        try {
            java.util.Vector npcVec = cd.h.f;
            if (npcVec == null) {
                log("  cd.h.f = null (chua co NPC nao trong danh sach)");
            } else {
                log("  cd.h.f.size=" + npcVec.size());
                for (int ni = 0; ni < npcVec.size(); ni++) {
                    try {
                        Class_at npcItem = (Class_at) npcVec.elementAt(ni);
                        if (npcItem == null) { log("  NPC[" + ni + "] = null"); continue; }
                        int npcType = -1;
                        try { if (npcItem.ao != null) npcType = npcItem.ao.a; } catch (Exception ex2) {}
                        int dist = Math.abs(cd.q.aa - npcItem.aa) + Math.abs(cd.q.ab - npcItem.ab);
                        log("  NPC[" + ni + "] type(ao.a)=" + npcType
                                + " aa=" + npcItem.aa + " ab=" + npcItem.ab
                                + " khoangCach=" + dist);
                    } catch (Exception ex) {
                        log("  NPC[" + ni + "] LOI: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log("  LOI dump NPC: " + e.getMessage());
        }
        log("=== HET DUMP NPC ===");

        // ---- Phase 1: Tim NPC giu do type 5 ----
        log("PHASE 1: Tim NPC giu do (type 5)");
        Class_at npc = null;
        try {
            npc = cd.h.d(5);
        } catch (Exception e) {
            log("  LOI goi cd.h.d(5): " + e.getMessage());
        }

        if (npc == null) {
            log("KHONG THAY NPC type5 trong zone nay -> DUNG TEST.");
            return;
        }
        log("Tim thay NPC type5: aa=" + npc.aa + " ab=" + npc.ab);

        // ---- Phase 2: Di chuyen + poll vi tri (loop move chong Class_ce keo ve) ----
        log("Di chuyen toi NPC (aa=" + npc.aa + " ab=" + npc.ab + ")");
        long moveStart = System.currentTimeMillis();
        boolean arrived = false;
        for (int i = 1; i <= 20; i++) {
            // Goi lai move moi vong de chong Class_ce keo bot ve pos config
            try {
                cd.h.a(npc.aa, npc.ab);
            } catch (Exception e) {
                log("  LOI cd.h.a: " + e.getMessage());
            }
            Class_bb.b(500L);
            int curAa = cd.q.aa;
            int curAb = cd.q.ab;
            int dist = Math.abs(curAa - npc.aa) + Math.abs(curAb - npc.ab);
            log("POS #" + i + ": bot aa=" + curAa + " ab=" + curAb
                    + " (target " + npc.aa + "," + npc.ab + ") dist=" + dist);
            if (dist <= 20) {
                long elapsed = System.currentTimeMillis() - moveStart;
                log("DA TOI NPC sau " + elapsed + "ms (dist=" + dist + ")");
                arrived = true;
                break;
            }
        }
        if (!arrived) {
            int finalDist = Math.abs(cd.q.aa - npc.aa) + Math.abs(cd.q.ab - npc.ab);
            log("CHUA TOI NPC (con cach " + finalDist + ") - van thu tiep");
        }

        // ---- Phase 3: Chao NPC opcode 29 (5,0,0) ----
        log("PHASE 3: Gui opcode 29 (chao NPC giu do: 5,0,0)");
        Class_bo bo29 = null;
        try {
            bo29 = new Class_bo((byte)29);
            bo29.c.writeByte(5);
            bo29.c.writeByte(0);
            bo29.c.writeByte(0);
            cd.l.a.i.a(bo29);
            log("Gui opcode 29 (5,0,0) thanh cong");
        } catch (Exception e) {
            log("LOI gui opcode 29: " + e.getMessage());
        } finally {
            if (bo29 != null) bo29.b();
        }
        Class_bb.b(800L); // cho server xu ly phien kho

        // ---- Phase 4: Mo ruong i(4) = -30/-103/writeByte(4) ----
        log("PHASE 4: Mo ruong (opcode -30/-103/writeByte(4))");
        log("TRUOC MO: v=" + arrLen(cd.q.v)
                + " w=" + arrLen(cd.q.w)
                + " x=" + arrLen(cd.q.x)
                + " y=" + arrLen(cd.q.y)
                + " z=" + arrLen(cd.q.z));
        openStorage();

        // Poll cd.q.x
        boolean ruongDaVe = false;
        long pollStart = System.currentTimeMillis();
        for (int pi = 1; pi <= 16; pi++) {
            Class_bb.b(500L);
            long elapsed = System.currentTimeMillis() - pollStart;
            log("POLL #" + pi + " (" + elapsed + "ms):"
                    + " v=" + arrLen(cd.q.v)
                    + " w=" + arrLen(cd.q.w)
                    + " x=" + arrLen(cd.q.x)
                    + " y=" + arrLen(cd.q.y)
                    + " z=" + arrLen(cd.q.z));
            if (cd.q.x != null && cd.q.x.length > 0) {
                long elapsedDone = System.currentTimeMillis() - pollStart;
                log("RUONG (.x) DA VE sau " + elapsedDone + "ms, x.length=" + cd.q.x.length);
                ruongDaVe = true;
                break;
            }
        }

        if (!ruongDaVe) {
            log("RUONG .x null sau khi chao NPC - server chua tra ve data ruong.");
            log("  Kiem tra: (a) NPC dung la kho hang (type 5)? (b) co phai doi hon? (c) xem POLL tren mang nao doi.");
            return;
        }

        // ---- Phase 5: Dump .x ----
        log("=== DUMP RUONG .x ===");
        for (int i = 0; i < cd.q.x.length; i++) {
            Class_al al = cd.q.x[i];
            if (al != null && al.b != null) {
                int dumpId  = al.b.a & 0xFFFF;
                String dumpTen = (al.b.d != null) ? al.b.d : "null";
                log("  slot=" + i
                        + " .a=" + al.a
                        + " id=" + dumpId
                        + " ten=" + dumpTen
                        + " e=" + al.e
                        + " d=" + (al.d & 0xFF)
                        + " c=" + al.c);
            }
        }
        log("=== HET DUMP ===");

        // ---- Phase 5b: dem o tui trong ----
        int tuiTong  = (cd.q.v != null) ? cd.q.v.length : 0;
        int tuiTrong = 0;
        if (cd.q.v != null) {
            for (int i = 0; i < cd.q.v.length; i++) {
                if (cd.q.v[i] == null) tuiTrong++;
            }
        }
        log("TUI (.v): tong=" + tuiTong + " o_trong=" + tuiTrong);
        if (tuiTrong == 0) {
            log("CANH BAO: TUI DAY - opcode rut se NO EFFECT du dung. Xoa bot do roi test lai.");
        }

        // ---- Phase 6: Chon o rut (phan tu .x dau tien co .b!=null && .e>0) ----
        testSlot   = -1;
        testItemId = -1;
        for (int i = 0; i < cd.q.x.length; i++) {
            Class_al al = cd.q.x[i];
            if (al != null && al.b != null && al.e > 0) {
                testSlot   = i;
                testItemId = al.b.a & 0xFFFF;
                break;
            }
        }
        if (testSlot == -1) {
            log("LOI: Ruong rong hoac khong co item co .e>0 (kiem tra DUMP o tren)");
            return;
        }

        Class_al slotAl  = cd.q.x[testSlot];
        // sendSlot dung .a neu >=0, nguoc lai dung array index
        int sendSlot = (slotAl.a >= 0) ? slotAl.a : testSlot;
        log("Chon pha rut: index=" + testSlot
                + " slot(.a)=" + slotAl.a
                + " sendSlot=" + sendSlot
                + " itemId=" + testItemId
                + " .e=" + slotAl.e);

        // ---- Phase 7: Rut opcode 16 ----
        int beforeBagQty     = captureInventory().getQuantity(testItemId);
        int beforeStorageQty = slotAl.e;
        log("  Truoc: tui itemId=" + testItemId + " qty=" + beforeBagQty
                + " | ruong index=" + testSlot + " .e=" + beforeStorageQty);

        Class_bo bo16 = null;
        try {
            bo16 = new Class_bo((byte)16);
            bo16.c.writeByte(sendSlot);
            cd.l.a.i.a(bo16);
            log("  Gui opcode 16 writeByte(" + sendSlot + ")");
        } catch (Exception e) {
            log("  LOI gui opcode 16: " + e.getMessage());
            return;
        } finally {
            if (bo16 != null) bo16.b();
        }

        // Poll tui 8x500ms
        Class_bb.b(1000L);
        int afterBagQty = captureInventory().getQuantity(testItemId);
        for (int poll = 1; poll <= 8; poll++) {
            Class_bb.b(500L);
            InventorySnapshot cur = captureInventory();
            int curQty = cur.getQuantity(testItemId);
            log("  POLL #" + poll + ": tui itemId=" + testItemId + " qty=" + curQty);
            afterBagQty = curQty;
            if (curQty != beforeBagQty) break;
        }
        int afterStorageQty = (cd.q.x != null && testSlot < cd.q.x.length && cd.q.x[testSlot] != null)
                ? cd.q.x[testSlot].e : -1;

        int delta16 = afterBagQty - beforeBagQty;
        log("RUT OPCODE 16: tui truoc=" + beforeBagQty + " sau=" + afterBagQty + " delta=" + delta16
                + " | ruong .e truoc=" + beforeStorageQty
                + " sau=" + (afterStorageQty < 0 ? "null(rut het?)" : String.valueOf(afterStorageQty)));

        if (delta16 > 0) {
            log(">>> OPCODE 16 SUCCESS: tui +" + delta16 + ", ruong giam");
        } else {
            log(">>> OPCODE 16 NO EFFECT."
                    + " Co the: chua toi NPC / chao NPC sai sub-type / slot sai."
                    + " Thu opcode 15 ngay sau day...");

            // ---- Phase 7b: Thu opcode 15 ----
            log("THU OPCODE 15: writeByte(slot=" + sendSlot + ")");
            int before15 = captureInventory().getQuantity(testItemId);
            Class_bo bo15 = null;
            try {
                bo15 = new Class_bo((byte)15);
                bo15.c.writeByte(sendSlot);
                cd.l.a.i.a(bo15);
                log("  Gui opcode 15 writeByte(" + sendSlot + ")");
            } catch (Exception e) {
                log("  LOI gui opcode 15: " + e.getMessage());
                return;
            } finally {
                if (bo15 != null) bo15.b();
            }

            Class_bb.b(1000L);
            int after15 = captureInventory().getQuantity(testItemId);
            for (int poll = 1; poll <= 8; poll++) {
                Class_bb.b(500L);
                int curQty = captureInventory().getQuantity(testItemId);
                log("  POLL 15 #" + poll + ": tui qty=" + curQty);
                after15 = curQty;
                if (curQty != before15) break;
            }
            int delta15 = after15 - before15;
            log("THU OPCODE 15: tui truoc=" + before15 + " sau=" + after15 + " delta=" + delta15);
            if (delta15 > 0) {
                log(">>> OPCODE 15 SUCCESS: RUT DUNG LA OPCODE 15 (khong phai 16)");
            } else {
                log(">>> OPCODE 15 CUNG NO EFFECT."
                        + " Kha nang: (a) server doi phien kho chao dung sub-type khac;"
                        + " (b) slot index sai; (c) NPC la kho loai khac (khong phai storage ban do).");
            }
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Open storage: opcode -30 + writeByte(-103) + writeByte(4)
     * Giong i(4) trong client cn.java
     */
    private void openStorage() {
        Class_ba ba = cd.l;
        Class_bo bo = null;
        try {
            bo = new Class_bo((byte)-30);
            bo.c.writeByte(-103);
            bo.c.writeByte(4);
            ba.a.i.a(bo);
            log("Gui opcode -30, -103, writeByte(4) de mo ruong");
        } catch (Exception e) {
            log("LOI openStorage: " + e.getMessage());
        } finally {
            if (bo != null) bo.b();
        }
    }

    /**
     * Capture current bag state (cd.q.v), keyed by itemId.
     */
    private InventorySnapshot captureInventory() {
        InventorySnapshot snap = new InventorySnapshot();
        if (cd.q.v == null) return snap;
        for (int i = 0; i < cd.q.v.length; i++) {
            Class_al al = cd.q.v[i];
            if (al != null && al.b != null && al.e > 0) {
                snap.items.add(new ItemSlot(i, al.b.a & 0xFFFF, al.e));
            }
        }
        return snap;
    }

    /**
     * Null-safe array length as String.
     */
    private static String arrLen(Object[] arr) {
        return arr == null ? "null" : String.valueOf(arr.length);
    }

    // ================================================================
    // Logging
    // ================================================================

    private void log(String msg) {
        cd.a("STORAGE_TEST: " + msg, false);
        writeLog(logFile, msg + "\r\n");
    }

    private void writeLog(String relPath, String content) {
        try {
            String base     = "C:/NinjaLite/GomItem/";
            String fullPath = base + relPath;

            File f   = new File(fullPath);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            String existing = "";
            if (f.exists()) {
                InputStream is  = new FileInputStream(f);
                byte[] buf      = new byte[is.available()];
                is.read(buf);
                is.close();
                existing = new String(buf, "UTF-8");
            }

            OutputStream os = new FileOutputStream(f);
            os.write((existing + content).getBytes("UTF-8"));
            os.flush();
            os.close();
        } catch (Exception e) {
            cd.a("STORAGE_TEST: Loi ghi log: " + e.getMessage(), false);
        }
    }

    // ================================================================
    // Inner classes
    // ================================================================

    private static class InventorySnapshot {
        List<ItemSlot> items = new ArrayList<ItemSlot>();

        int getQuantity(int itemId) {
            int total = 0;
            for (ItemSlot item : items) {
                if (item.itemId == itemId) total += item.qty;
            }
            return total;
        }
    }

    private static class ItemSlot {
        int slot;
        int itemId;
        int qty;

        ItemSlot(int slot, int itemId, int qty) {
            this.slot   = slot;
            this.itemId = itemId;
            this.qty    = qty;
        }
    }
}
