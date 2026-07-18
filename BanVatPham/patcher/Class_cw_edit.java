/*
 * Decompiled with CFR 0.152.
 */
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.table.DefaultTableModel;

final class Class_cw
implements ActionListener {
    private /* synthetic */ Class_cu a;

    Class_cw(Class_cu class_cu) {
        this.a = class_cu;
    }

    @Override
    public final void actionPerformed(ActionEvent actionEvent) {
        try {
            Object object;
            if (Class_cd.a == null) {
                Object object2;
                block11: {
                    for (int i = 0; i < Class_cu.a().d.size(); ++i) {
                        object = (Class_cd)Class_cu.a().d.elementAt(i);
                        if (object == null || ((Class_cd)object).z.size() >= 3 || ((Class_cd)object).aa) continue;
                        object2 = object;
                        break block11;
                    }
                    object2 = null;
                }
                Class_cd.a = (Class_cd)object2;
            } else if (Class_cd.a.w == 1) {
                Class_cd.a = null;
            }
            object = this.a;
            Object[] objectArray = new String[]{"SERVER", "USERNAME", "MAP", "POS_X_Y", "XU", "BAG", "STATUS", "VAT PHAM BAN"};
            Object[][] objectArray2 = new Object[((Class_cu)object).d.size()][8];
            int n = 0;
            for (Object object3 : ((Class_cu)object).d) {
                objectArray2[n][0] = ((Class_cd)object3).d.a;
                if (((Class_cd)object3).h.b == 1) {
                    objectArray2[n][1] = ((Class_cd)object3).q.i;
                    objectArray2[n][2] = ((Class_cd)object3).r.b + " - " + ((Class_cd)object3).r.c;
                    objectArray2[n][3] = ((Class_cd)object3).q.aa + ":" + ((Class_cd)object3).q.ab;
                    objectArray2[n][4] = Class_bb.a((long)((Class_cd)object3).q.s);
                } else {
                    objectArray2[n][1] = ((Class_cd)object3).b;
                }
                objectArray2[n][5] = "[BAG]";
                objectArray2[n][6] = ((Class_cd)object3).w > 0 ? "[ON]" : "[OFF]";
                // Cot 8: vat pham ban + so luong (tu BanSeller cua nhan vat nay)
                String stock = "-";
                try {
                    if (((Class_cd)object3).p != null && ((Class_cd)object3).p.banSeller != null) {
                        stock = ((Class_cd)object3).p.banSeller.getStockSummary();
                    }
                } catch (Exception ex) {}
                objectArray2[n][7] = stock;
                ++n;
            }
            if (((Class_cu)object).e != -1 && ((Class_cu)object).d.size() > ((Class_cu)object).e) {
                Class_cd class_cd = (Class_cd)((Class_cu)object).d.elementAt(((Class_cu)object).e);
                if (class_cd.h.b == 1) {
                    String object3;
                    String string = class_cd.q.aa + ", " + class_cd.q.ab;
                    object3 = Class_bb.a((long)class_cd.q.t);
                    String string2 = Class_bb.a((long)class_cd.q.s);
                    String string3 = Class_bb.a((long)class_cd.q.u);
                    String string4 = class_cd.q.k + "/" + class_cd.q.j;
                    ((Class_cu)object).a(new String[]{string, object3, string2, string3, class_cd.o.a, string4, "" + class_cd.q.al, "" + class_cd.q.am});
                }
            }
            ((Class_cu)object).b.setModel(new DefaultTableModel(objectArray2, objectArray));
            ((Class_cu)object).b.getColumnModel().getColumn(0).setPreferredWidth(4);
            ((Class_cu)object).b.getColumnModel().getColumn(5).setPreferredWidth(2);
            ((Class_cu)object).b.getColumnModel().getColumn(6).setPreferredWidth(2);
            ((Class_cu)object).c.setText("Account running: " + objectArray2.length);
            return;
        }
        catch (Exception exception) {
            return;
        }
    }
}

