import java.io.*;
import java.util.*;

public final class NikStore {
    private final List<NikEntry> entries = new ArrayList<NikEntry>();
    private final List<String> loaiList = new ArrayList<String>();

    public synchronized List<String> getLoaiList() { return new ArrayList<String>(loaiList); }

    public synchronized void loadLoai(String workDir) {
        loaiList.clear();
        if (workDir == null || workDir.isEmpty()) return;
        File f = new File(workDir + "\\nik_loai.txt");
        if (!f.exists()) return;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.contains(":") || line.contains("\n") || line.contains("\r")) {
                    System.err.println("NikStore skip loai invalid: " + line);
                    continue;
                }
                boolean dup = false;
                for (String ex : loaiList) if (ex.equalsIgnoreCase(line)) { dup = true; break; }
                if (dup) continue;
                loaiList.add(line);
            }
        } catch (Exception ex) {
            System.err.println("NikStore loadLoai error: " + ex.getMessage());
        } finally {
            if (br != null) try { br.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized void saveLoai(String workDir) throws IOException {
        if (workDir == null || workDir.isEmpty()) throw new IOException("workDir null");
        File f = new File(workDir + "\\nik_loai.txt");
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8"));
            for (String s : loaiList) {
                bw.write(s);
                bw.newLine();
            }
        } finally {
            if (bw != null) try { bw.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized void saveLoaiSafe(String workDir) {
        try { saveLoai(workDir); } catch (Exception e) { System.err.println("NikStore saveLoai error: " + e.getMessage()); }
    }

    public synchronized String addLoai(String ten) {
        if (ten == null) ten = "";
        ten = ten.trim();
        if (ten.isEmpty()) return "Ten loai khong duoc rong";
        if (ten.contains(":") || ten.contains("\n") || ten.contains("\r")) return "Ten loai khong duoc chua ':' hoac xuong dong";
        for (String ex : loaiList) if (ex.equalsIgnoreCase(ten)) return "Loai da ton tai.";
        loaiList.add(ten);
        return null;
    }

    public synchronized String canRemoveLoai(String loai) {
        if (loai == null) return null;
        int count = 0;
        for (NikEntry e : entries) if (!e.isSold() && e.vatPham != null && e.vatPham.equalsIgnoreCase(loai)) count++;
        if (count > 0) return "Con " + count + " nick dang dung loai nay, khong xoa duoc.";
        return null;
    }

    public synchronized boolean removeLoaiAt(int idx) {
        if (idx < 0 || idx >= loaiList.size()) return false;
        String loai = loaiList.get(idx);
        String err = canRemoveLoai(loai);
        if (err != null) return false;
        loaiList.remove(idx);
        return true;
    }

    public synchronized int countByLoai(String loai) {
        if (loai == null) return 0;
        int c = 0;
        for (NikEntry e : entries) if (!e.isSold() && e.vatPham != null && e.vatPham.equalsIgnoreCase(loai)) c++;
        return c;
    }

    public synchronized List<NikEntry> getChuaBanByLoai(String loai) {
        List<NikEntry> r = new ArrayList<NikEntry>();
        if (loai == null) return r;
        for (NikEntry e : entries) if (!e.isSold() && e.vatPham != null && e.vatPham.equalsIgnoreCase(loai)) r.add(e);
        return r;
    }

    public synchronized List<NikEntry> peekFifoByLoai(String loai, int qty) {
        List<NikEntry> r = new ArrayList<NikEntry>();
        if (loai == null) return null;
        for (NikEntry e : entries) {
            if (!e.isSold() && e.vatPham != null && e.vatPham.equalsIgnoreCase(loai)) {
                r.add(e);
                if (r.size() == qty) break;
            }
        }
        if (r.size() < qty) return null;
        return r;
    }

    public synchronized List<NikEntry> getChuaBan() {
        List<NikEntry> r = new ArrayList<NikEntry>();
        for (NikEntry e : entries) if (!e.isSold()) r.add(e);
        return r;
    }

    public synchronized List<NikEntry> getDaBan() {
        List<NikEntry> r = new ArrayList<NikEntry>();
        for (NikEntry e : entries) if (e.isSold()) r.add(e);
        return r;
    }

    public synchronized List<NikEntry> getAll() {
        return new ArrayList<NikEntry>(entries);
    }

    public synchronized int chuaBanCount() {
        int c = 0;
        for (NikEntry e : entries) if (!e.isSold()) c++;
        return c;
    }

    public synchronized void add(NikEntry e) {
        entries.add(e);
    }

    public synchronized void removeChuaBanAt(int idx) {
        // idx is index within CHUA_BAN filtered list; map to backing list
        int seen = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).isSold()) {
                seen++;
                if (seen == idx) { entries.remove(i); return; }
            }
        }
    }

    public synchronized void updateChuaBanAt(int idx, NikEntry ne) {
        int seen = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).isSold()) {
                seen++;
                if (seen == idx) { entries.set(i, ne); return; }
            }
        }
    }

    public synchronized NikEntry getChuaBanAt(int idx) {
        int seen = -1;
        for (NikEntry e : entries) if (!e.isSold()) { seen++; if (seen == idx) return e; }
        return null;
    }

    public synchronized NikEntry getDaBanAt(int idx) {
        int seen = -1;
        for (NikEntry e : entries) if (e.isSold()) { seen++; if (seen == idx) return e; }
        return null;
    }

    // FIFO select first qty CHUA_BAN entries (without removing)
    public synchronized List<NikEntry> peekFifo(int qty) {
        List<NikEntry> r = new ArrayList<NikEntry>();
        for (NikEntry e : entries) {
            if (!e.isSold()) {
                r.add(e);
                if (r.size() == qty) break;
            }
        }
        if (r.size() < qty) return null;
        return r;
    }

    // Move first qty CHUA_BAN to DA_BAN (set nguoiMua, trangThai=1)
    public synchronized List<NikEntry> takeFifo(int qty, String buyer) {
        List<NikEntry> taken = new ArrayList<NikEntry>();
        int need = qty;
        // collect
        for (NikEntry e : entries) {
            if (!e.isSold()) {
                taken.add(e);
                need--;
                if (need == 0) break;
            }
        }
        if (taken.size() < qty) return null;
        for (NikEntry e : taken) {
            e.trangThai = 1;
            e.nguoiMua = buyer;
        }
        // reorder: keep order - entries already in order, sold stays where they were
        // But spec says preserve order; leaving in place is fine. For display, filtered lists preserve file order.
        return taken;
    }

    public synchronized void undoDaBanAt(int idx) {
        int seen = -1;
        int pos = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).isSold()) {
                seen++;
                if (seen == idx) { pos = i; break; }
            }
        }
        if (pos == -1) return;
        NikEntry e = entries.remove(pos);
        e.trangThai = 0;
        e.nguoiMua = "";
        entries.add(e); // append to end of CHUA_BAN (end of list)
    }

    public synchronized void load(String workDir) {
        entries.clear();
        if (workDir == null || workDir.isEmpty()) return;
        File f = new File(workDir + "\\nik_ban.txt");
        if (!f.exists()) { loadLoai(workDir); return; }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                NikEntry e = NikEntry.fromLine(line);
                if (e == null) {
                    System.err.println("NikStore skip malformed: " + line);
                    continue;
                }
                entries.add(e);
            }
        } catch (Exception ex) {
            // keep in-memory state, log
            System.err.println("NikStore load error: " + ex.getMessage());
        } finally {
            if (br != null) try { br.close(); } catch (Exception ignored) {}
        }
        loadLoai(workDir);
    }

    public synchronized void save(String workDir) throws IOException {
        if (workDir == null || workDir.isEmpty()) throw new IOException("workDir null");
        File f = new File(workDir + "\\nik_ban.txt");
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8"));
            for (NikEntry e : entries) {
                bw.write(e.toLine());
                bw.newLine();
            }
        } finally {
            if (bw != null) try { bw.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized void saveSafe(String workDir) {
        try { save(workDir); } catch (Exception e) { System.err.println("NikStore save error: " + e.getMessage()); }
    }

    public static int resolveVndPerXu(String workDir) {
        int def = 1000;
        if (workDir == null) return def;
        // try read via Class_cq/Class_bb reflectively, fallback to direct file read
        try {
            String c1 = null;
            try {
                java.lang.reflect.Method m = Class.forName("Class_cq").getMethod("d", String.class);
                c1 = (String) m.invoke(null, workDir + "\\banvatpham.txt");
            } catch (Exception ignored) { c1 = readFile(workDir + "\\banvatpham.txt"); }
            if (c1 != null) {
                String v = extractTag(c1, "VND_PER_XU");
                if (v != null) try { int iv = Integer.parseInt(v.trim()); if (iv > 0) return iv; } catch (Exception ignored) {}
            }
            String c2 = null;
            try {
                java.lang.reflect.Method m2 = Class.forName("Class_cq").getMethod("d", String.class);
                c2 = (String) m2.invoke(null, workDir + "\\config.txt");
            } catch (Exception ignored) { c2 = readFile(workDir + "\\config.txt"); }
            if (c2 != null) {
                String v2 = extractTag(c2, "VND_PER_XU");
                if (v2 != null) try { int iv = Integer.parseInt(v2.trim()); if (iv > 0) return iv; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return def;
    }

    private static String readFile(String path) {
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

    private static String extractTag(String content, String tag) {
        try {
            java.lang.reflect.Method m = Class.forName("Class_bb").getMethod("a", String.class, String.class);
            String v = (String) m.invoke(null, content, tag);
            if (v != null) return v;
        } catch (Exception ignored) {}
        // fallback: [TAG]value[/TAG] or TAG:value
        if (content == null) return null;
        String open = "[" + tag + "]";
        String close = "[/" + tag + "]";
        int a = content.indexOf(open);
        if (a >= 0) {
            int b = content.indexOf(close, a);
            if (b >= 0) return content.substring(a + open.length(), b).trim();
        }
        return null;
    }

    public static void appendSoldLog(String workDir, List<NikEntry> sold) {
        if (workDir == null || sold == null || sold.isEmpty()) return;
        try {
            String base = workDir + "\\logs";
            File dir = new File(base);
            if (!dir.exists()) dir.mkdirs();
            File logFile = new File(base + "\\nik_sold.log");
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), "UTF-8"));
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            for (NikEntry e : sold) {
                bw.write(ts + " | " + e.tk + " | " + e.vatPham + " | " + e.giaVnd + " | " + e.nguoiMua);
                bw.newLine();
            }
            bw.close();
        } catch (Exception ex) {
            System.err.println("appendSoldLog error: " + ex.getMessage());
        }
    }
}
