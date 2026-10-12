import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class NikFrame extends JFrame {
    private static NikFrame instance;
    private JTable tableChuaBan;
    private JTable tableDaBan;
    private DefaultTableModel modelChuaBan;
    private DefaultTableModel modelDaBan;
    private JLabel emptyChuaBanLabel;
    private JLabel emptyDaBanLabel;

    private NikFrame() {
        super("Quan ly NICK - Ban NICK");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(900, 500);
        setLayout(new BorderLayout(6, 6));

        modelChuaBan = new DefaultTableModel(new Object[]{"Tai khoan", "Mat khau", "Vat pham", "Gia VND"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        modelDaBan = new DefaultTableModel(new Object[]{"Tai khoan", "Mat khau", "Vat pham", "Nguoi mua", "Gia"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tableChuaBan = new JTable(modelChuaBan);
        tableDaBan = new JTable(modelDaBan);
        tableChuaBan.setRowHeight(22);
        tableDaBan.setRowHeight(22);
        tableChuaBan.setFillsViewportHeight(true);
        tableDaBan.setFillsViewportHeight(true);
        tableChuaBan.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tableDaBan.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tableChuaBan.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                setText("******");
                return comp;
            }
        });
        try { tableChuaBan.getColumnModel().getColumn(0).setPreferredWidth(150); } catch (Exception ignored) {}
        try { tableChuaBan.getColumnModel().getColumn(1).setPreferredWidth(120); } catch (Exception ignored) {}
        try { tableChuaBan.getColumnModel().getColumn(2).setPreferredWidth(240); } catch (Exception ignored) {}
        try { tableChuaBan.getColumnModel().getColumn(3).setPreferredWidth(120); } catch (Exception ignored) {}
        try { tableDaBan.getColumnModel().getColumn(0).setPreferredWidth(140); } catch (Exception ignored) {}
        try { tableDaBan.getColumnModel().getColumn(1).setPreferredWidth(120); } catch (Exception ignored) {}
        try { tableDaBan.getColumnModel().getColumn(2).setPreferredWidth(220); } catch (Exception ignored) {}
        try { tableDaBan.getColumnModel().getColumn(3).setPreferredWidth(130); } catch (Exception ignored) {}
        try { tableDaBan.getColumnModel().getColumn(4).setPreferredWidth(110); } catch (Exception ignored) {}

        emptyChuaBanLabel = new JLabel("Chua co nick dang ban", SwingConstants.CENTER);
        emptyDaBanLabel = new JLabel("Chua co nick da ban", SwingConstants.CENTER);

        JPanel chuaBanPanel = new JPanel(new BorderLayout());
        chuaBanPanel.setBorder(BorderFactory.createTitledBorder("CHUA BAN"));
        chuaBanPanel.add(new JScrollPane(tableChuaBan), BorderLayout.CENTER);
        chuaBanPanel.add(emptyChuaBanLabel, BorderLayout.SOUTH);

        JPanel daBanPanel = new JPanel(new BorderLayout());
        daBanPanel.setBorder(BorderFactory.createTitledBorder("DA BAN"));
        daBanPanel.add(new JScrollPane(tableDaBan), BorderLayout.CENTER);
        daBanPanel.add(emptyDaBanLabel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chuaBanPanel, daBanPanel);
        split.setResizeWeight(0.5);
        split.setDividerLocation(440);
        add(split, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton btnThem = new JButton("Them");
        JButton btnXoa = new JButton("Xoa");
        JButton btnSua = new JButton("Sua");
        JButton btnHoanTac = new JButton("Hoan tac");
        JButton btnLoaiVp = new JButton("Loai VP");
        btnPanel.add(btnThem);
        btnPanel.add(btnXoa);
        btnPanel.add(btnSua);
        btnPanel.add(btnHoanTac);
        btnPanel.add(btnLoaiVp);
        add(btnPanel, BorderLayout.SOUTH);

        btnThem.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doThem(); } });
        btnXoa.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doXoa(); } });
        btnSua.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doSua(); } });
        btnHoanTac.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doHoanTac(); } });
        btnLoaiVp.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { doLoaiVp(); } });
    }

    public static synchronized NikFrame getInstance() {
        if (instance == null) instance = new NikFrame();
        return instance;
    }

    public static void showFrame() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                NikFrame f = getInstance();
                f.refreshTables();
                f.setVisible(true);
                f.toFront();
                try { f.setLocationRelativeTo(null); } catch (Exception ignored) {}
            }
        });
    }

    public static void refreshIfVisible() {
        if (instance != null && instance.isVisible()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { instance.refreshTables(); }
            });
        }
    }

    private NikStore getStore() {
        try { return BanSeller.getSharedNikStore(); } catch (Exception e) { return null; }
    }

    public void refreshTables() {
        try {
            NikStore store = getStore();
            if (store == null || modelChuaBan == null || modelDaBan == null) return;
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        modelChuaBan.setRowCount(0);
                        for (NikEntry e : store.getChuaBan()) modelChuaBan.addRow(new Object[]{e.tk, e.mk, e.vatPham, String.valueOf(e.giaVnd)});
                        modelDaBan.setRowCount(0);
                        for (NikEntry e : store.getDaBan()) modelDaBan.addRow(new Object[]{e.tk, e.mk, e.vatPham, e.nguoiMua, String.valueOf(e.giaVnd)});
                        if (emptyChuaBanLabel != null) emptyChuaBanLabel.setVisible(store.getChuaBan().isEmpty());
                        if (emptyDaBanLabel != null) emptyDaBanLabel.setVisible(store.getDaBan().isEmpty());
                    } catch (Exception ignored) {}
                }
            };
            if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
        } catch (Exception ignored) {}
    }

    private void doLoaiVp() {
        try {
            NikStore store = getStore();
            if (store == null) return;
            JDialog dlg = new JDialog(this, "Quan ly Loai VP", true);
            dlg.setSize(350, 300);
            dlg.setLayout(new BorderLayout(6, 6));
            dlg.setLocationRelativeTo(this);

            DefaultListModel<String> model = new DefaultListModel<String>();
            for (String s : store.getLoaiList()) model.addElement(s);
            JList<String> list = new JList<String>(model);
            dlg.add(new JScrollPane(list), BorderLayout.CENTER);

            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            JButton btnThemLoai = new JButton("Them");
            JButton btnXoaLoai = new JButton("Xoa");
            JButton btnDong = new JButton("Dong");
            south.add(btnThemLoai);
            south.add(btnXoaLoai);
            south.add(btnDong);
            dlg.add(south, BorderLayout.SOUTH);

            btnThemLoai.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String ten = JOptionPane.showInputDialog(dlg, "Ten loai (vd ctt):");
                    if (ten == null) return;
                    ten = ten.trim();
                    if (ten.isEmpty()) { JOptionPane.showMessageDialog(dlg, "Ten loai khong duoc rong", "Loi", JOptionPane.ERROR_MESSAGE); return; }
                    String err = store.addLoai(ten);
                    if (err != null) {
                        JOptionPane.showMessageDialog(dlg, err, "Loi", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    store.saveLoaiSafe(Class_cu.g);
                    model.clear();
                    for (String s : store.getLoaiList()) model.addElement(s);
                }
            });

            btnXoaLoai.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int idx = list.getSelectedIndex();
                    if (idx < 0) { JOptionPane.showMessageDialog(dlg, "Chon 1 loai de xoa", "Thong bao", JOptionPane.INFORMATION_MESSAGE); return; }
                    String loai = model.get(idx);
                    String err = store.canRemoveLoai(loai);
                    if (err != null) { JOptionPane.showMessageDialog(dlg, err, "Loi", JOptionPane.ERROR_MESSAGE); return; }
                    int c = JOptionPane.showConfirmDialog(dlg, "Xoa loai '" + loai + "'?", "Xac nhan", JOptionPane.YES_NO_OPTION);
                    if (c != JOptionPane.YES_OPTION) return;
                    if (!store.removeLoaiAt(idx)) { JOptionPane.showMessageDialog(dlg, "Khong xoa duoc", "Loi", JOptionPane.ERROR_MESSAGE); return; }
                    store.saveLoaiSafe(Class_cu.g);
                    model.clear();
                    for (String s : store.getLoaiList()) model.addElement(s);
                    refreshTables();
                }
            });

            btnDong.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { dlg.dispose(); }
            });

            dlg.setVisible(true);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); }
    }

    private void doThem() {
        try {
            NikStore store = getStore();
            if (store == null) return;
            List<String> loais = store.getLoaiList();
            if (loais.isEmpty()) { JOptionPane.showMessageDialog(this, "Tao loai truoc (Loai VP)", "Loi", JOptionPane.ERROR_MESSAGE); return; }
            JTextField fTk = new JTextField();
            JTextField fMk = new JTextField();
            JComboBox<String> cbVp = new JComboBox<String>(loais.toArray(new String[0]));
            cbVp.setEditable(false);
            JTextField fGia = new JTextField();
            Object[] msg = {"Tai khoan:", fTk, "Mat khau:", fMk, "Vat pham:", cbVp, "Gia VND:", fGia};
            int opt = JOptionPane.showConfirmDialog(this, msg, "Them NIK", JOptionPane.OK_CANCEL_OPTION);
            if (opt != JOptionPane.OK_OPTION) return;
            String selectedLoai = (String) cbVp.getSelectedItem();
            if (selectedLoai == null || selectedLoai.trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Vat pham khong duoc rong", "Loi", JOptionPane.ERROR_MESSAGE); return; }
            String err = NikEntry.validate(fTk.getText(), fMk.getText(), selectedLoai, fGia.getText());
            if (err != null) { JOptionPane.showMessageDialog(this, err, "Loi", JOptionPane.ERROR_MESSAGE); return; }
            long gia = Long.parseLong(fGia.getText().trim());
            NikEntry ne = new NikEntry(fTk.getText().trim(), fMk.getText().trim(), selectedLoai.trim(), gia, 0, "");
            synchronized (store) { store.add(ne); try { store.save(Class_cu.g); } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi luu file: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); } }
            refreshTables();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); }
    }

    private void doXoa() {
        try {
            int row = tableChuaBan.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "Chon 1 dong CHUA BAN de xoa", "Thong bao", JOptionPane.INFORMATION_MESSAGE); return; }
            int c = JOptionPane.showConfirmDialog(this, "Xoa NIK da chon?", "Xac nhan", JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) return;
            NikStore store = getStore();
            if (store == null) return;
            synchronized (store) { store.removeChuaBanAt(row); try { store.save(Class_cu.g); } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi luu: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); } }
            refreshTables();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); }
    }

    private void doSua() {
        try {
            int row = tableChuaBan.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "Chon 1 dong CHUA BAN de sua", "Thong bao", JOptionPane.INFORMATION_MESSAGE); return; }
            NikStore store = getStore();
            if (store == null) return;
            List<String> loais = store.getLoaiList();
            if (loais.isEmpty()) { JOptionPane.showMessageDialog(this, "Tao loai truoc (Loai VP)", "Loi", JOptionPane.ERROR_MESSAGE); return; }
            NikEntry cur = store.getChuaBanAt(row);
            if (cur == null) return;
            JTextField fTk = new JTextField(cur.tk);
            JTextField fMk = new JTextField(cur.mk);
            JComboBox<String> cbVp = new JComboBox<String>(loais.toArray(new String[0]));
            cbVp.setEditable(false);
            // select current value; if not in list add temporarily? but spec says strict - if old value not in list, keep it selectable
            boolean found = false;
            for (String s : loais) if (s.equalsIgnoreCase(cur.vatPham)) { found = true; break; }
            if (found) cbVp.setSelectedItem(cur.vatPham);
            else {
                // add current to combo so it shows, but user must pick valid loai; keep current selected
                cbVp.addItem(cur.vatPham);
                cbVp.setSelectedItem(cur.vatPham);
            }
            JTextField fGia = new JTextField(String.valueOf(cur.giaVnd));
            Object[] msg = {"Tai khoan:", fTk, "Mat khau:", fMk, "Vat pham:", cbVp, "Gia VND:", fGia};
            int opt = JOptionPane.showConfirmDialog(this, msg, "Sua NIK", JOptionPane.OK_CANCEL_OPTION);
            if (opt != JOptionPane.OK_OPTION) return;
            String selectedLoai = (String) cbVp.getSelectedItem();
            if (selectedLoai == null || selectedLoai.trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Vat pham khong duoc rong", "Loi", JOptionPane.ERROR_MESSAGE); return; }
            String err = NikEntry.validate(fTk.getText(), fMk.getText(), selectedLoai, fGia.getText());
            if (err != null) { JOptionPane.showMessageDialog(this, err, "Loi", JOptionPane.ERROR_MESSAGE); return; }
            long gia = Long.parseLong(fGia.getText().trim());
            NikEntry ne = new NikEntry(fTk.getText().trim(), fMk.getText().trim(), selectedLoai.trim(), gia, 0, "");
            synchronized (store) { store.updateChuaBanAt(row, ne); try { store.save(Class_cu.g); } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi luu: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); } }
            refreshTables();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); }
    }

    private void doHoanTac() {
        try {
            int row = tableDaBan.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "Chon 1 dong DA BAN de hoan tac", "Thong bao", JOptionPane.INFORMATION_MESSAGE); return; }
            NikStore store = getStore();
            if (store == null) return;
            synchronized (store) { store.undoDaBanAt(row); try { store.save(Class_cu.g); } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi luu: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); } }
            refreshTables();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Loi: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE); }
    }
}
