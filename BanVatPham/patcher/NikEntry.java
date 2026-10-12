import java.util.*;

public final class NikEntry {
    public String tk;
    public String mk;
    public String vatPham;
    public long giaVnd;
    public int trangThai; // 0 = CHUA_BAN, 1 = DA_BAN
    public String nguoiMua;

    public NikEntry(String tk, String mk, String vatPham, long giaVnd, int trangThai, String nguoiMua) {
        this.tk = tk;
        this.mk = mk;
        this.vatPham = vatPham;
        this.giaVnd = giaVnd;
        this.trangThai = trangThai;
        this.nguoiMua = nguoiMua != null ? nguoiMua : "";
    }

    public boolean isSold() { return trangThai == 1; }

    public String toLine() {
        return tk + ":" + mk + ":" + vatPham + ":" + giaVnd + ":" + trangThai + ":" + (nguoiMua != null ? nguoiMua : "");
    }

    public static NikEntry fromLine(String line) {
        if (line == null) return null;
        String[] p = line.split(":", -1);
        if (p.length != 6) return null;
        String tk = p[0].trim();
        String mk = p[1].trim();
        String vatPham = p[2].trim();
        String giaStr = p[3].trim();
        String ttStr = p[4].trim();
        String nguoiMua = p[5].trim();
        if (tk.isEmpty() || mk.isEmpty()) return null;
        long gia;
        int tt;
        try {
            gia = Long.parseLong(giaStr);
            tt = Integer.parseInt(ttStr);
        } catch (NumberFormatException e) { return null; }
        if (gia <= 0) return null;
        if (tt != 0 && tt != 1) return null;
        if (tt == 0 && !nguoiMua.isEmpty()) {
            // unsold should have empty buyer, but tolerate
        }
        return new NikEntry(tk, mk, vatPham, gia, tt, nguoiMua);
    }

    public static String validate(String tk, String mk, String vatPham, String giaVndStr) {
        if (tk == null || tk.trim().isEmpty()) return "Tai khoan khong duoc rong";
        if (mk == null || mk.trim().isEmpty()) return "Mat khau khong duoc rong";
        if (vatPham == null || vatPham.trim().isEmpty()) return "Vat pham khong duoc rong";
        if (giaVndStr == null || giaVndStr.trim().isEmpty()) return "Gia VND khong duoc rong";
        for (String s : new String[]{tk, mk, vatPham}) {
            if (s.contains(":") || s.contains("\n") || s.contains("\r")) return " Truong khong duoc chua ':' hoac xuong dong";
        }
        try {
            long g = Long.parseLong(giaVndStr.trim());
            if (g <= 0) return "Gia VND phai > 0";
        } catch (NumberFormatException e) {
            return "Gia VND phai la so nguyen";
        }
        return null;
    }
}
