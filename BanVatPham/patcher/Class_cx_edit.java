/*
 * Decompiled with CFR 0.152.
 */
import java.awt.EventQueue;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

final class Class_cx
implements ListSelectionListener {
    private long a;
    private /* synthetic */ Class_cu b;

    Class_cx(Class_cu class_cu) {
        this.b = class_cu;
    }

    @Override
    public final void valueChanged(ListSelectionEvent listSelectionEvent) {
        int n = this.b.b.getSelectedRow();
        int n2 = this.b.b.getSelectedColumn();
        if (n != -1) {
            this.b.e = n;
            if (Class_bb.b() - this.a > 200L) {
                Class_cd class_cd;
                Class_cd class_cd2 = class_cd = (Class_cd)this.b.d.elementAt(this.b.e);
                int n3 = n2;
                if (class_cd2 != null) {
                    switch (n3) {
                        case 6: {
                            if (Class_ad.a == null) {
                                Class_ad.a = new Class_ad();
                            }
                            Class_ad class_ad = Class_ad.a;
                            EventQueue.invokeLater(new Class_ae(class_ad, class_cd2));
                            break;
                        }
                        case 7: {
                            if (class_cd2.w > 0) {
                                class_cd2.w = 0;
                                class_cd2.z.removeAllElements();
                                break;
                            }
                            class_cd2.w = 1;
                        }
                    }
                }
                this.a = Class_bb.b();
            }
        }
    }
}
