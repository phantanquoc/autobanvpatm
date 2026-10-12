import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

final class Class_cw implements ActionListener {
    private Class_cu a;
    private JTextPane xuBox;
    private boolean nikBtnInit = false;

    private JTextPane findTextPane(Container c) {
        Component[] comps = c.getComponents();
        for (Component comp : comps) {
            if (comp instanceof JTextPane) return (JTextPane) comp;
            if (comp instanceof Container) {
                JTextPane found = findTextPane((Container) comp);
                if (found != null) return found;
            }
        }
        return null;
    }
    Class_cw(Class_cu class_cu) { this.a = class_cu; }

    private void ensureNikButton() {
        if (nikBtnInit) return;
        try {
            if (a == null || a.a == null) return;
            Container cp = a.a.getContentPane();
            JTextPane tp = findTextPane(cp);
            if (tp == null) { nikBtnInit = true; return; }
            Container parent = tp.getParent();
            if (parent == null) { nikBtnInit = true; return; }
            xuBox = tp;
            // Wrap xuBox + button, keep parent layout intact
            parent.remove(tp);
            JPanel wrapper = new JPanel(new BorderLayout(4, 4));
            wrapper.add(xuBox, BorderLayout.CENTER);
            JButton btnNik = new JButton("Quan ly NICK");
            btnNik.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { NikFrame.showFrame(); } });
            wrapper.add(btnNik, BorderLayout.SOUTH);
            // parent may have BorderLayout or something else - force BorderLayout and add wrapper
            try { parent.setLayout(new BorderLayout()); } catch (Exception ignored) {}
            parent.add(wrapper, BorderLayout.CENTER);
            try { a.a.revalidate(); a.a.repaint(); } catch (Exception ignored2) {}
            nikBtnInit = true;
        } catch (Exception ex) { nikBtnInit = true; }
    }

    @Override
    public final void actionPerformed(ActionEvent actionEvent) {
        try {
            ensureNikButton();
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
            Object[] objectArray = new String[]{"SERVER", "USERNAME", "MAP", "POS_X_Y", "XU", "VAT PHAM BAN", "BAG", "STATUS"};
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
                String stock = "-";
                try { if (((Class_cd)object3).p != null && ((Class_cd)object3).p.banSeller != null) stock = ((Class_cd)object3).p.banSeller.getStockCombined(); } catch (Exception ex) {}
                objectArray2[n][5] = stock;
                objectArray2[n][6] = "[BAG]";
                objectArray2[n][7] = ((Class_cd)object3).w > 0 ? "[ON]" : "[OFF]";
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
            ((Class_cu)object).b.getColumnModel().getColumn(0).setPreferredWidth(55);
            ((Class_cu)object).b.getColumnModel().getColumn(1).setPreferredWidth(95);
            ((Class_cu)object).b.getColumnModel().getColumn(2).setPreferredWidth(95);
            ((Class_cu)object).b.getColumnModel().getColumn(3).setPreferredWidth(80);
            ((Class_cu)object).b.getColumnModel().getColumn(4).setPreferredWidth(95);
            ((Class_cu)object).b.getColumnModel().getColumn(5).setPreferredWidth(380);
            ((Class_cu)object).b.getColumnModel().getColumn(6).setPreferredWidth(55);
            ((Class_cu)object).b.getColumnModel().getColumn(7).setPreferredWidth(65);
            ((Class_cu)object).c.setText("Account running: " + objectArray2.length);
            try {
                if (this.xuBox != null) {
                    Map<String, Long> totals = new LinkedHashMap<String, Long>();
                    for (Object b : ((Class_cu)object).d) {
                        Class_cd bot = (Class_cd)b;
                        if (bot == null || bot.d == null || bot.q == null) continue;
                        String sv = bot.d.a;
                        if (sv == null) continue;
                        Long cur = totals.get(sv);
                        long add = bot.q.s;
                        totals.put(sv, cur == null ? add : cur.longValue() + add);
                    }
                    StringBuilder html = new StringBuilder();
                    html.append("<html><body style=\"text-align: center;\"><p><b>Tong Xu</b></p>");
                    if (totals.isEmpty()) html.append("<p>-</p>");
                    else for (Map.Entry<String, Long> e : totals.entrySet()) html.append("<p>").append(e.getKey()).append(": ").append(Class_bb.a(e.getValue().longValue())).append("</p>");
                    html.append("</body></html>");
                    this.xuBox.setText(html.toString());
                }
            } catch (Exception exXu) {}
            return;
        } catch (Exception exception) { return; }
    }
}
