/*
 * Class_bl - Modified: adds raw packet dump for opcodes 1, 3, 43 (signed byte values).
 * Opcode 3 = player appear (0x03), opcode 1 = player move (0x01), opcode 43 = trade invite recv (0x2B).
 * As signed bytes: 3, 1, 43 remain the same (all < 128).
 * Dump file: C:/NinjaLite/GomItem/bandebug/rawpackets.txt
 * Format per line: OP=<n> LEN=<len> HEX=<hexstring>
 *
 * Cast fixes applied (CFR decompile produces Object where Class_bk cast needed):
 *  - Class_bk class_bk = (Class_bk)object2;  (4 occurrences in n4==-32 block)
 *  - ((byte[])object).length  instead of  ((Object)object).length
 *  - ((byte[])object)[n]  instead of  object[n]
 */
import java.io.File;
import java.io.FileOutputStream;

final class Class_bl
implements Runnable {
    private /* synthetic */ Class_bk a;

    Class_bl(Class_bk class_bk) {
        this.a = class_bk;
    }

    /** Dump raw packet bytes to file for opcode offset analysis. */
    private static void dumpRawPacket(int op, byte[] data) {
        try {
            // Only dump opcodes we care about: 1 (move), 3 (appear), 43 (trade invite recv)
            if (op != 1 && op != 3 && op != 43) return;
            // Build hex string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < data.length; i++) {
                int b = data[i] & 0xFF;
                if (b < 16) sb.append('0');
                sb.append(Integer.toHexString(b).toUpperCase());
            }
            String line = "OP=" + op + " LEN=" + data.length + " HEX=" + sb.toString() + "\r\n";
            // Ensure directory exists
            File dir = new File("C:/NinjaLite/GomItem/bandebug");
            if (!dir.exists()) dir.mkdirs();
            // Append to file
            FileOutputStream fos = new FileOutputStream(
                new File("C:/NinjaLite/GomItem/bandebug/rawpackets.txt"), true);
            fos.write(line.getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            // Silently ignore - never break main loop
        }
    }

    @Override
    public final void run() {
        Object object;
        while (this.a.a.e.d) {
            try {
                int n;
                int n2;
                int n3;
                Object object2 = this.a;
                int n4 = ((Class_bk)object2).b.readByte();
                if (((Class_bk)object2).a.e.g) {
                    n4 = ((Class_bk)object2).a((byte)n4);
                }
                if (n4 == -32) {
                    n4 = ((Class_bk)object2).b.readByte();
                    if (((Class_bk)object2).a.e.g) {
                        n4 = ((Class_bk)object2).a((byte)n4);
                    }
                    Class_bk class_bk = (Class_bk)object2;
                    byte by = class_bk.a(class_bk.b.readByte());
                    Class_bk class_bk2 = (Class_bk)object2;
                    n3 = class_bk2.a(class_bk2.b.readByte());
                    Class_bk class_bk3 = (Class_bk)object2;
                    n2 = class_bk3.a(class_bk3.b.readByte());
                    Class_bk class_bk4 = (Class_bk)object2;
                    n = class_bk4.a(class_bk4.b.readByte());
                    n = (by & 0xFF) << 24 | (n3 & 0xFF) << 16 | (n2 & 0xFF) << 8 | n & 0xFF;
                } else if (((Class_bk)object2).a.e.g) {
                    byte by = ((Class_bk)object2).b.readByte();
                    n3 = ((Class_bk)object2).b.readByte();
                    n = (((Class_bk)object2).a(by) & 0xFF) << 8 | ((Class_bk)object2).a((byte)n3) & 0xFF;
                } else {
                    n = ((Class_bk)object2).b.readUnsignedShort();
                }
                object = new byte[n];
                n3 = 0;
                n2 = 0;
                while (n3 != -1 && n2 < n) {
                    n3 = ((Class_bk)object2).b.read((byte[])object, n2, n - n2);
                    if (n3 <= 0) continue;
                    n2 += n3;
                }
                if (((Class_bk)object2).a.e.g) {
                    for (n = 0; n < ((byte[])object).length; ++n) {
                        ((byte[])object)[n] = ((Class_bk)object2).a((byte)((byte[])object)[n]);
                    }
                }
                // *** DUMP: ghi byte tho truoc khi wrap vao Class_bo ***
                dumpRawPacket(n4, (byte[])object);
                object = new Class_bo((byte)n4, (byte[])object);
                if (((Class_bo)object).a == -27) {
                    object2 = object;
                    object = this.a.a.e;
                    n4 = ((Class_bo)object2).d.readByte();
                    ((Class_bg)object).c = new byte[n4];
                    for (n = 0; n < n4; ++n) {
                        ((Class_bg)object).c[n] = ((Class_bo)object2).d.readByte();
                    }
                    for (n = 0; n < ((Class_bg)object).c.length - 1; ++n) {
                        int n5 = n + 1;
                        ((Class_bg)object).c[n5] = (byte)(((Class_bg)object).c[n5] ^ ((Class_bg)object).c[n]);
                    }
                    ((Class_bg)object).g = true;
                } else {
                    this.a.a.k.a((Class_bo)object);
                }
                Class_bb.b();
            }
            catch (Exception exception) {}
        }
        if (this.a.a.e.d) {
            this.a.a.e.a();
            return;
        }
        object = this.a.c;
        synchronized (object) {
            this.a.c.notifyAll();
            return;
        }
    }
}
