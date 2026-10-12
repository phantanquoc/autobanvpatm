/*
 * Decompiled with CFR 0.152.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Calendar;

public final class Class_cp {
    public Class_cd a;
    public String b;
    private int g;
    public int c;
    private long h;
    private long i;
    private Class_al[] j;
    public Class_al[] d;
    public boolean e;
    long f;
    private int k;
    private int l;
    public BanSeller banSeller;

    public Class_cp(Class_cd class_cd) {
        this.a = class_cd;
        this.banSeller = new BanSeller(class_cd);
    }

    public final boolean a() {
        // RAO CONG CONG: thiet lap may rao 1 lan (sinh cau tu bang gia + bat f.h).
        // Goi moi vong luc bot ranh; ensureBroadcast tu bo qua sau lan dau thanh cong.
        if (this.banSeller != null) {
            this.banSeller.ensureBroadcast();
        }
        // REFRESH RUONG: gui i(4) toi da 1 lan/30s khi IDLE + khong withdrawing.
        // Ket qua: cd.q.x duoc server dien -> GUI hien thi toi. KHONG di chuyen.
        if (this.banSeller != null) {
            this.banSeller.maybeRefreshStorage();
        }
        // AUTO RUT RUONG (background): khi rảnh, mon nao het tui ma ruong con -> rut.
        if (this.banSeller != null) {
            this.banSeller.maybeWithdrawIdle();
        }
        // BAN MODE: dang ban (state != IDLE) -> CHI chay banSeller.tick() (tu bo do/split/lock),
        // BO QUA hoan toan logic MUA cu (tranh doc do khach -> huy "khong chap nhan vat pham").
        // Guard o DAU ham nen chan ca khi cua so gd DA mo (this.e == true).
        if (this.banSeller != null && (this.banSeller.state != 0 || this.banSeller.withdrawing)) {
            this.banSeller.tick();
            return true;
        }
        // BAN-ONLY: bot KHONG BAO GIO mua. Neu cua so gd DANG mo (this.e) ma
        // state == IDLE = khach TU mo trade bo do vao (khong qua luong ban cua
        // bot, vi luong ban luon set state != 0 TRUOC khi cua so mo). Huy ngay,
        // TUYET DOI khong roi xuong logic MUA cu ben duoi (doc do khach + bo xu).
        if (this.banSeller != null && this.e) {
            this.a.l.b();      // huy giao dich
            this.d();
            this.e = false;
            return false;
        }
        if (!this.e) {
            return false;
        }
        if (this.c != 2 && this.i != 0L && Class_bb.b() - this.i > 60000L) {
            this.a.l.a(this.b, "qu\u00e1 th\u1eddi gian thao t\u00e1c (t\u1ed1i \u0111a 1 ph\u00fat)");
            this.a.l.b();
            this.d();
            this.e = false;
            return false;
        }
        if (this.a.aa) {
            if (this.a.h.r) {
                if (this.g == 0) {
                    int n = 0;
                    int n2 = 29999;
                    for (int i = 0; i < this.a.q.v.length && n < 12; ++i) {
                        Class_al class_al = this.a.q.v[i];
                        if (class_al == null || class_al.c || !this.a(class_al.b.a) || class_al.b.b == 16 && class_al.e > n2) continue;
                        this.j[n] = class_al;
                        ++n;
                        if (class_al.b.b == 16) {
                            n2 -= class_al.e;
                        }
                        this.a.a("SELECT ITEM: " + class_al.b.d + " = " + class_al.e, false);
                    }
                    this.a.l.a(0, this.j);
                    this.g = 1;
                    this.b();
                } else if (this.g == 1 && this.c > 0 && Class_bb.b() - this.h > 5000L) {
                    this.a.l.c();
                    this.h = Class_bb.b();
                }
            } else if (this.g == 0 && this.c == 1) {
                Class_cp class_cp = this;
                int n = class_cp.a(class_cp.d);
                if (n == 0) {
                    this.a.l.a(this.b, "kh\u00f4ng ch\u1ea5p nh\u1eadn v\u1eadt ph\u1ea9m kh\u00f4ng h\u1ee3p l\u1ec7");
                    this.a.l.b();
                    this.d();
                    this.e = false;
                    return false;
                }
                this.k = n;
                this.a.l.a(n, this.j);
                this.g = 1;
                this.b();
            } else if (this.g == 1 && this.c >= 2 && Class_bb.b() - this.h > 5000L) {
                this.a.l.c();
                this.h = Class_bb.b();
            }
        } else if (this.a.b()) {
            if (this.g == 0) {
                this.a.l.a(0, this.j);
                this.g = 1;
                this.b();
            } else if (this.g == 1 && this.c > 0 && Class_bb.b() - this.h > 5000L) {
                this.a.l.c();
                this.h = Class_bb.b();
            }
        }
        this.banSeller.tick();
        return true;
    }

    public final boolean a(int n) {
        for (int i = 0; i < this.a.f.g.size(); ++i) {
            Class_am class_am = (Class_am)this.a.f.g.get(i);
            if (class_am == null || class_am.a != n) continue;
            return true;
        }
        return false;
    }

    private int a(Class_al[] class_alArray) {
        int n = 0;
        try {
            for (int i = 0; i < class_alArray.length; ++i) {
                if (class_alArray[i] == null) continue;
                Class_al class_al = class_alArray[i];
                boolean bl = false;
                for (int j = 0; j < this.a.f.g.size(); ++j) {
                    Class_am class_am = (Class_am)this.a.f.g.get(j);
                    if (class_am == null || class_am.a != class_al.g) continue;
                    n += class_am.b * class_al.e;
                    bl = true;
                }
                if (bl) continue;
                return 0;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return n;
    }

    public final void b() {
        if (this.g == 1 && this.c == 1) {
            this.h = Class_bb.b();
        }
    }

    public final void c() {
        this.k = 0;
        this.l = this.a.q.s;
        this.i = Class_bb.b();
        this.d = new Class_al[12];
        this.j = this.d;
        // BAN MODE: cua so gd vua mo -> bao BanSeller chuyen IN_TRADE de bo do
        if (this.banSeller != null) {
            this.banSeller.onTradeWindowOpened();
        }
    }

    public final void d() {
        this.b = null;
        Class_cp class_cp = this;
        class_cp.c = 0;
        class_cp.g = 0;
        Class_cp class_cp2 = this;
        class_cp2.d = null;
        class_cp2.j = null;
        this.i = 0L;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public final void a(Class_ai class_ai) {
        Object object;
        if (this.banSeller.shouldAcceptInviteFrom(class_ai)) {
            this.a.a("INVITE ACCEPT BAN: " + class_ai.i, false);
            this.banSeller.onInviteAccept(class_ai);
            return;
        }
        if (this.a.b()) {
            boolean bl;
            block10: {
                object = class_ai.i;
                for (int i = 0; i < Class_cu.a().d.size(); ++i) {
                    Class_cd class_cd = (Class_cd)Class_cu.a().d.elementAt(i);
                    if (class_cd == null || !class_cd.a() || !class_cd.q.i.equals(object)) continue;
                    bl = true;
                    break block10;
                }
                bl = false;
            }
            if (bl) {
                this.a.a("INVITE ACCEPT DUNG DO", false);
                this.a.l.a(class_ai.e);
                return;
            }
        }
        if (this.a.aa && !this.a.h.r) {
            this.a.a("INVITE ACCEPT GOM DO", false);
            this.a.l.a(class_ai.e);
            return;
        }
        object = this.a.l;
        ((Class_ba)object).a.a("HUY LOI MOI GIAO DICH", false);
        Class_bo class_bo = null;
        try {
            class_bo = new Class_bo((byte)56);
            ((Class_ba)object).a.i.a(class_bo);
            return;
        }
        catch (Exception exception) {
            return;
        }
        finally {
            class_bo.b();
        }
    }

    public final void e() {
        if (this.j != null) {
            for (int i = 0; i < this.j.length; ++i) {
                if (this.j[i] == null) continue;
                this.a.q.v[this.j[i].a] = null;
            }
        }
        String string = Class_bb.c();
        string = string + "\r\nNh\u00e2n v\u1eadt: " + this.b;
        string = string + "\r\nXu giao d\u1ecbch: " + Class_bb.a((long)this.k) + " xu";
        string = string + "\r\nBi\u1ebfn \u0111\u1ed9ng: " + Class_bb.a((long)this.l) + " -> " + Class_bb.a((long)this.a.q.s);
        string = string + "\r\nV\u1eadt ph\u1ea9m nh\u1eadn: ";
        if (this.d != null) {
            for (int i = 0; i < this.d.length; ++i) {
                if (this.d[i] == null) continue;
                string = string + this.d[i].b.d + " (" + this.d[i].e + "), ";
            }
        }
        string = string + "\r\n==========================================\r\n";
        if (this.k > 0) {
            Object object = this.a;
            Calendar calendar = Calendar.getInstance();
            int n = calendar.get(1);
            int n2 = calendar.get(2) + 1;
            int n3 = calendar.get(5);
            object = "logs/" + ((Class_cd)object).d.a + "/" + ((Class_cd)object).q.i + "/";
            Object object2 = "log-" + n + "-" + Class_bb.b(n2) + "-" + Class_bb.b(n3) + ".txt";
            try {
                String string2 = (String)object + (String)object2;
                object = string;
                string = string2;
                object2 = new File("C:/NinjaLite/GomItem/".concat(String.valueOf(string)));
                if (((File)object2).exists()) {
                    object2 = new FileInputStream((File)object2);
                    byte[] byArray = new byte[((InputStream)object2).available()];
                    ((InputStream)object2).read(byArray);
                    object = (String)object + new String(byArray, "UTF-8");
                    ((InputStream)object2).close();
                }
                Class_cq.a(string, ((String)object).getBytes("UTF-8"));
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        this.a.q.b();
    }
}

