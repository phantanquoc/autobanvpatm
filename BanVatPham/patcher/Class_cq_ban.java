/*
 * Class_cq - Fixed CFR errors + BanSeller hooks
 * Original decompiled by CFR 0.152
 * Part 1: imports + static helpers
 */
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import main.Midlet;

public final class Class_cq {
    private Class_cd a;
    private Class_bo b;

    public static ImageIcon a(String string) {
        URL uRL = Midlet.class.getResource(string);
        if (uRL != null) {
            return new ImageIcon(uRL, "Java");
        }
        System.err.println("Couldn't find file: ".concat(String.valueOf(string)));
        return null;
    }

    public static String b(String string) {
        try {
            return new String(Class_cq.c(string), "UTF-8");
        }
        catch (Exception exception) {
            return null;
        }
    }

    public static byte[] c(String string) {
        return Class_cq.f("C:/NinjaLite/GomItem/".concat(String.valueOf(string)));
    }

    private static byte[] f(String path) {
        byte[] byArray = null;
        try {
            File fObj = new File(path);
            if (fObj.exists()) {
                InputStream inStr = new FileInputStream(fObj);
                byArray = new byte[inStr.available()];
                inStr.read(byArray);
                inStr.close();
            }
        }
        catch (Exception exception) {}
        return byArray;
    }

    public static String d(String path) {
        try {
            File fObj = new File(path);
            if (fObj.exists()) {
                String line;
                InputStream inStr = new FileInputStream(fObj);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inStr, "utf-8"));
                StringBuilder stringBuilder = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line.trim() + "\r\n");
                }
                bufferedReader.close();
                inStr.close();
                return stringBuilder.toString();
            }
        }
        catch (Exception exception) {}
        return null;
    }

    public static void a(String relPath, String string) {
        try {
            String fullPath = Class_cu.g + "\\logs\\" + relPath + ".txt";
            File dirFile = new File(Class_cu.g + "\\logs\\");
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            File outFile = new File(fullPath);
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            OutputStream outStr = new FileOutputStream(outFile);
            outStr.write(string.getBytes("utf-8"));
            outStr.flush();
            outStr.close();
            return;
        }
        catch (Exception exception) {
            return;
        }
    }

    public static DataInputStream e(String path) {
        try {
            File fObj = new File("C:/NinjaLite/GomItem/".concat(String.valueOf(path)));
            if (fObj.exists()) {
                return new DataInputStream(new FileInputStream(fObj));
            }
        }
        catch (Exception exception) {}
        return null;
    }

    public static void a(String relPath, byte[] byArray) {
        File dirFile = new File("C:/NinjaLite/GomItem/" + relPath.substring(0, relPath.lastIndexOf("/")));
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File outFile = new File("C:/NinjaLite/GomItem/".concat(String.valueOf(relPath)));
        try {
            if (!outFile.exists()) {
                outFile.createNewFile();
            }
            OutputStream outStr = new FileOutputStream(outFile);
            outStr.write(byArray);
            outStr.flush();
            outStr.close();
        } catch (Exception exception) {}
    }

    public Class_cq(Class_cd class_cd) {
        this.a = class_cd;
    }

    public final void a(Class_bo object) {
        try {
            switch (((Class_bo)object).a) {
                case -28: {
                    this.b((Class_bo)object);
                    return;
                }
                case -30: {
                    this.c((Class_bo)object);
                    return;
                }
                case 93: {
                    ++this.a.h.o;
                    if (((Class_bo)object).d.readInt() != this.a.q.e) break;
                    Class_ai object2 = this.a.q;
                    this.a.q.i = ((Class_bo)object).d.readUTF();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readByte();
                    ((Class_bo)object).d.readByte();
                    ((Class_bo)object).d.readByte();
                    object2.k = ((Class_bo)object).d.readInt();
                    object2.j = ((Class_bo)object).d.readInt();
                    object2.m = ((Class_bo)object).d.readInt();
                    object2.l = ((Class_bo)object).d.readInt();
                    ((Class_bo)object).d.readByte();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readInt();
                    ((Class_bo)object).d.readInt();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    object2.ae = ((Class_bo)object).d.readUnsignedByte();
                    ((Class_bo)object).d.readShort();
                    object2.g = ((Class_bo)object).d.readUTF();
                    if (!object2.g.equals("")) {
                        ((Class_bo)object).d.readByte();
                    }
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).d.readShort();
                    ((Class_bo)object).a().readShort();
                    ((Class_bo)object).a().readByte();
                    ((Class_bo)object).a().readByte();
                    return;
                }
                case 38: {
                    ((Class_bo)object).a().readShort();
                    ((Class_bo)object).a().readUTF();
                    return;
                }
                case 8: {
                    byte by = ((Class_bo)object).a().readByte();
                    this.a.q.v[by] = new Class_al();
                    this.a.q.v[by].a = by;
                    this.a.q.v[by].b = this.a.d.b(((Class_bo)object).a().readShort());
                    this.a.q.v[by].c = ((Class_bo)object).a().readBoolean();
                    if (Class_al.a(this.a.q.v[by].b.b) || this.a.q.v[by].a() || this.a.q.v[by].b()) {
                        this.a.q.v[by].d = ((Class_bo)object).a().readByte();
                    }
                    ((Class_bo)object).a().readBoolean();
                    try {
                        this.a.q.v[by].e = ((Class_bo)object).a().readUnsignedShort();
                    }
                    catch (Exception exception) {
                        this.a.q.v[by].e = 1;
                    }
                    this.a.q.b();
                    this.a.g.g();
                    // BAN: notify BanSeller after item slot updated (after split)
                    this.a.p.banSeller.onItemUpdated();
                    return;
                }
                case 10: {
                    byte by = ((Class_bo)object).a().readByte();
                    this.a.q.v[by] = null;
                    this.a.q.b();
                    return;
                }
                case 18: {
                    byte by = ((Class_bo)object).a().readByte();
                    short s = 1;
                    try {
                        s = ((Class_bo)object).a().readShort();
                    }
                    catch (Exception exception) {}
                    if (this.a.q.v[by].e > s) {
                        this.a.q.v[by].e -= s;
                    } else {
                        this.a.q.v[by] = null;
                    }
                    this.a.q.b();
                    this.a.g.i();
                    return;
                }
                case 9: {
                    short s;
                    int n = ((Class_bo)object).a().readUnsignedByte();
                    Class_al class_al = this.a.q.v[n];
                    try {
                        s = ((Class_bo)object).a().readShort();
                    }
                    catch (Exception exception) {
                        s = 1;
                    }
                    class_al.e += s;
                    this.a.q.b();
                    return;
                }
                case 15: {
                    Class_bo class_bo = object;
                    Class_ai class_ai = this.a.q;
                    try {
                        class_ai.a(class_bo);
                        class_ai.p = class_bo.a().readShort();
                        class_ai.q = class_bo.a().readShort();
                        Class_al class_al = class_ai.w[class_bo.a().readUnsignedByte()];
                        class_ai.w[class_al.a] = null;
                        class_al.a = class_bo.a().readUnsignedByte();
                        class_bo.a().readShort();
                        class_ai.v[class_al.a] = class_al;
                    }
                    catch (Exception exception) {}
                    this.a.g.g();
                    return;
                }
                case 17: {
                    // FIXED: typed local vars instead of Object reuse
                    Class_bo class_bo17 = object;
                    Class_ai class_ai17 = this.a.q;
                    try {
                        int n = class_bo17.a().readUnsignedByte();
                        int n2 = class_bo17.a().readUnsignedByte();
                        Class_al alObj = class_ai17.v[n];
                        if (alObj != null) {
                            class_ai17.v[n] = null;
                            if (class_ai17.x[n2] == null) {
                                alObj.a = n2;
                                class_ai17.x[n2] = alObj;
                                return;
                            }
                            class_ai17.x[n2].e += alObj.e;
                        }
                        return;
                    }
                    catch (Exception exception) {
                        return;
                    }
                }
