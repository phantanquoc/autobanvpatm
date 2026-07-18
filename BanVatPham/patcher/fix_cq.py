import re

path_in = r'C:\Users\TanQuoc\AppData\Local\Temp\ban_compile\src\Class_cq_work.java'
path_out = r'C:\Users\TanQuoc\AppData\Local\Temp\ban_compile\src\Class_cq_fixed.java'

with open(path_in, 'rb') as f:
    raw = f.read()

src = raw.decode('utf-8').replace('\r\n', '\n')
print("Read OK, lines:", src.count('\n'))

fixes_done = 0

def fix(old, new, name, count=1):
    global src, fixes_done
    n = src.count(old)
    if n == 0:
        print(f"WARNING: {name} pattern NOT FOUND")
        return False
    src = src.replace(old, new, count)
    print(f"OK: {name} ({n} occurrence(s))")
    fixes_done += 1
    return True

# Fix 1: f() method
fix(
    'private static byte[] f(String object) {\n        byte[] byArray = null;\n        try {\n            object = new File((String)object);\n            if (((File)object).exists()) {\n                object = new FileInputStream((File)object);\n                byArray = new byte[((InputStream)object).available()];\n                ((InputStream)object).read(byArray);\n                ((InputStream)object).close();\n            }\n        }\n        catch (Exception exception) {}\n        return byArray;\n    }',
    'private static byte[] f(String path) {\n        byte[] byArray = null;\n        try {\n            File fObj = new File(path);\n            if (fObj.exists()) {\n                InputStream inStr = new FileInputStream(fObj);\n                byArray = new byte[inStr.available()];\n                inStr.read(byArray);\n                inStr.close();\n            }\n        }\n        catch (Exception exception) {}\n        return byArray;\n    }',
    'Fix1 f()'
)

# Fix 2: d() method
fix(
    'public static String d(String object) {\n        try {\n            object = new File((String)object);\n            if (((File)object).exists()) {\n                String string;\n                object = new FileInputStream((File)object);\n                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader((InputStream)object, "utf-8"));\n                StringBuilder stringBuilder = new StringBuilder();\n                while ((string = bufferedReader.readLine()) != null) {\n                    stringBuilder.append(string.trim() + "\\r\\n");\n                }\n                bufferedReader.close();\n                ((InputStream)object).close();\n                return stringBuilder.toString();\n            }\n        }\n        catch (Exception exception) {}\n        return null;\n    }',
    'public static String d(String path) {\n        try {\n            File fObj = new File(path);\n            if (fObj.exists()) {\n                String line;\n                InputStream inStr = new FileInputStream(fObj);\n                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inStr, "utf-8"));\n                StringBuilder stringBuilder = new StringBuilder();\n                while ((line = bufferedReader.readLine()) != null) {\n                    stringBuilder.append(line.trim() + "\\r\\n");\n                }\n                bufferedReader.close();\n                inStr.close();\n                return stringBuilder.toString();\n            }\n        }\n        catch (Exception exception) {}\n        return null;\n    }',
    'Fix2 d()'
)

# Fix 3: a(String, String)
fix(
    'public static void a(String object, String string) {\n        try {\n            object = Class_cu.g + "\\\\logs\\\\" + (String)object + ".txt";\n            File file = new File(Class_cu.g + "\\\\logs\\\\");\n            if (!file.exists()) {\n                file.mkdirs();\n            }\n            if (!(file = new File((String)object)).exists()) {\n                file.createNewFile();\n            }\n            object = new FileOutputStream(file);\n            ((OutputStream)object).write(string.getBytes("utf-8"));\n            ((OutputStream)object).flush();\n            ((OutputStream)object).close();\n            return;\n        }\n        catch (Exception exception) {\n            return;\n        }\n    }',
    'public static void a(String relPath, String string) {\n        try {\n            String fullPath = Class_cu.g + "\\\\logs\\\\" + relPath + ".txt";\n            File dirFile = new File(Class_cu.g + "\\\\logs\\\\");\n            if (!dirFile.exists()) {\n                dirFile.mkdirs();\n            }\n            File outFile = new File(fullPath);\n            if (!outFile.exists()) {\n                outFile.createNewFile();\n            }\n            OutputStream outStr = new FileOutputStream(outFile);\n            outStr.write(string.getBytes("utf-8"));\n            outStr.flush();\n            outStr.close();\n            return;\n        }\n        catch (Exception exception) {\n            return;\n        }\n    }',
    'Fix3 a(String,String)'
)

# Fix 4: e(String)
fix(
    'public static DataInputStream e(String object) {\n        try {\n            object = new File("C:/NinjaLite/GomItem/".concat(String.valueOf(object)));\n            if (((File)object).exists()) {\n                return new DataInputStream(new FileInputStream((File)object));\n            }\n        }\n        catch (Exception exception) {}\n        return null;\n    }',
    'public static DataInputStream e(String path) {\n        try {\n            File fObj = new File("C:/NinjaLite/GomItem/".concat(String.valueOf(path)));\n            if (fObj.exists()) {\n                return new DataInputStream(new FileInputStream(fObj));\n            }\n        }\n        catch (Exception exception) {}\n        return null;\n    }',
    'Fix4 e(String)'
)

# Fix 5: a(String, byte[]) last static
fix(
    '        object = new FileOutputStream(file);\n        ((OutputStream)object).write(byArray);\n        ((OutputStream)object).flush();\n        ((OutputStream)object).close();\n    }',
    '        OutputStream outStr2 = new FileOutputStream(file);\n        outStr2.write(byArray);\n        outStr2.flush();\n        outStr2.close();\n    }',
    'Fix5 a(String,byte[])'
)

# Fix 6: case 17
fix(
    '                case 17: {\n                    Class_bo class_bo = object;\n                    Class_ai class_ai = this.a.q;\n                    try {\n                        int n = class_bo.a().readUnsignedByte();\n                        int n2 = class_bo.a().readUnsignedByte();\n                        object = class_ai.v[n];\n                        if (object != null) {\n                            class_ai.v[n] = null;\n                            if (class_ai.x[n2] == null) {\n                                ((Class_al)object).a = n2;\n                                class_ai.x[n2] = object;\n                                return;\n                            }\n                            class_ai.x[n2].e += ((Class_al)object).e;\n                        }\n                        return;\n                    }\n                    catch (Exception exception) {\n                        return;\n                    }\n                }',
    '                case 17: {\n                    Class_bo class_bo17 = object;\n                    Class_ai class_ai17 = this.a.q;\n                    try {\n                        int n = class_bo17.a().readUnsignedByte();\n                        int n2 = class_bo17.a().readUnsignedByte();\n                        Class_al alObj17 = class_ai17.v[n];\n                        if (alObj17 != null) {\n                            class_ai17.v[n] = null;\n                            if (class_ai17.x[n2] == null) {\n                                alObj17.a = n2;\n                                class_ai17.x[n2] = alObj17;\n                                return;\n                            }\n                            class_ai17.x[n2].e += alObj17.e;\n                        }\n                        return;\n                    }\n                    catch (Exception exception) {\n                        return;\n                    }\n                }',
    'Fix6 case17'
)

# Fix 7: case 122
fix(
    '                case 122: {\n                    if (((Class_bo)object).a().readByte() != 0) break;\n                    Object object3 = object;\n                    object = this;\n                    try {\n                        byte by = ((Class_bo)object3).d.readByte();\n                        for (byte by2 = 0; by2 < by; by2 = (byte)(by2 + 1)) {\n                            short s = (short)((Class_bo)object3).d.readUnsignedByte();\n                            ((Class_bo)object3).d.readBoolean();\n                            ((Class_bo)object3).d.readBoolean();\n                            ((Class_bo)object3).d.readBoolean();\n                            ((Class_bo)object3).d.readBoolean();\n                            ((Class_bo)object3).d.readBoolean();\n                            int n = ((Class_bo)object3).d.readUnsignedByte();\n                            ((Class_bo)object3).d.readByte();\n                            ((Class_bo)object3).d.readInt();\n                            ((Class_bo)object3).d.readUnsignedByte();\n                            ((Class_bo)object3).d.readInt();\n                            short s2 = ((Class_bo)object3).d.readShort();\n                            short s3 = ((Class_bo)object3).d.readShort();\n                            byte by3 = ((Class_bo)object3).d.readByte();\n                            ((Class_bo)object3).d.readByte();\n                            ((Class_bo)object3).d.readBoolean();\n                            Class_ar class_ar = new Class_ar(s, n, s2, s3, by3);\n                            ((Class_cq)object).a.h.c.addElement(class_ar);\n                        }\n                        return;\n                    }\n                    catch (Exception exception) {\n                        ((Class_cq)object).a.a("err addMob", false);\n                        return;\n                    }\n                }',
    '                case 122: {\n                    if (((Class_bo)object).a().readByte() != 0) break;\n                    Class_bo object3 = object;\n                    Class_cq selfRef = this;\n                    try {\n                        byte by = object3.d.readByte();\n                        for (byte by2 = 0; by2 < by; by2 = (byte)(by2 + 1)) {\n                            short s = (short)object3.d.readUnsignedByte();\n                            object3.d.readBoolean();\n                            object3.d.readBoolean();\n                            object3.d.readBoolean();\n                            object3.d.readBoolean();\n                            object3.d.readBoolean();\n                            int n = object3.d.readUnsignedByte();\n                            object3.d.readByte();\n                            object3.d.readInt();\n                            object3.d.readUnsignedByte();\n                            object3.d.readInt();\n                            short s2 = object3.d.readShort();\n                            short s3 = object3.d.readShort();\n                            byte by3 = object3.d.readByte();\n                            object3.d.readByte();\n                            object3.d.readBoolean();\n                            Class_ar class_ar = new Class_ar(s, n, s2, s3, by3);\n                            selfRef.a.h.c.addElement(class_ar);\n                        }\n                        return;\n                    }\n                    catch (Exception exception) {\n                        selfRef.a.a("err addMob", false);\n                        return;\n                    }\n                }',
    'Fix7 case122'
)

# Fix 8: case -26 String/Class_ai
fix(
    '                case -26: {\n                    object = ((Class_bo)object).a().readUTF();\n                    this.a.h.a((String)object);\n                    this.a.g.c();\n                    this.a.g.g();\n                    return;\n                }',
    '                case -26: {\n                    String pName26 = ((Class_bo)object).a().readUTF();\n                    this.a.h.a(pName26);\n                    this.a.g.c();\n                    this.a.g.g();\n                    return;\n                }',
    'Fix8 case-26'
)

# Fix 9: case -108 ByteArrayInputStream
fix(
    '                    object = new ByteArrayInputStream(byArray);\n                    this.a.h.a(ImageIO.read((InputStream)object));',
    '                    ByteArrayInputStream bais = new ByteArrayInputStream(byArray);\n                    this.a.h.a(ImageIO.read(bais));',
    'Fix9 case-108'
)

# Fix 10: a(Class_ai, Class_bo) end - Class_ci confusion
fix(
    '            Class_ai class_ai2 = class_ai;\n            object = this.a.h;\n            for (n = 0; n < ((Class_ci)object).j.size(); ++n) {\n                object2 = (Class_av)((Class_ci)object).j.elementAt(n);\n                if (((Class_av)object2).a != class_ai2.e) continue;\n                ((Class_av)object2).c = class_ai2;\n                break;\n            }',
    '            Class_ai class_ai2 = class_ai;\n            Class_ci ciRef = this.a.h;\n            for (n = 0; n < ciRef.j.size(); ++n) {\n                Class_av avObj = (Class_av)ciRef.j.elementAt(n);\n                if (avObj.a != class_ai2.e) continue;\n                avObj.c = class_ai2;\n                break;\n            }',
    'Fix10 a(Class_ai,Class_bo)'
)

# Fix 11: Class_an byte cast
for old11, new11 in [
    ('new Class_an(this.a, s, s4, n, s5, 0)', 'new Class_an(this.a, s, s4, n, s5, (byte)0)'),
    ('new Class_an(this.a, s, s6, n, s7, 0)', 'new Class_an(this.a, s, s6, n, s7, (byte)0)'),
    ('new Class_an(this.a, s, s8, n, s9, 0)', 'new Class_an(this.a, s, s8, n, s9, (byte)0)'),
    ('new Class_an(this.a, s, s10, n, s11, 0)', 'new Class_an(this.a, s, s10, n, s11, (byte)0)'),
    ('new Class_an(this.a, class_bo.d.readShort(), class_bo.d.readShort(), class_bo.d.readShort(), class_bo.d.readShort())',
     'new Class_an(this.a, class_bo.d.readShort(), class_bo.d.readShort(), class_bo.d.readShort(), class_bo.d.readShort(), (byte)0)'),
]:
    n = src.count(old11)
    if n > 0:
        src = src.replace(old11, new11)
        print(f"Fix11 byte cast ({n}x): {old11[:40]}")

# Fix 12: BAN SELLER HOOKS

# 12a: case -22 - keep chat content
fix(
    '                case -22: {\n                    ((Class_bo)object).a().readUTF();\n                    ((Class_bo)object).a().readUTF();\n                    return;\n                }',
    '                case -22: {\n                    // BAN: giu lai chat rieng, goi BanSeller\n                    String senderName = ((Class_bo)object).a().readUTF();\n                    String chatContent = ((Class_bo)object).a().readUTF();\n                    this.a.p.banSeller.onPrivateMessage(senderName, chatContent);\n                    return;\n                }',
    'Fix12a case-22 BAN'
)

# 12b: case 45 - keep xu, call onTradeOffer
fix(
    '                case 45: {\n                    ((Class_bo)object).a().readInt();\n                    this.a.p.d = new Class_al[12];',
    '                case 45: {\n                    // BAN: doc xu khach bo vao, notify BanSeller\n                    int xuKhach = ((Class_bo)object).a().readInt();\n                    this.a.p.banSeller.onTradeOffer(xuKhach);\n                    this.a.p.d = new Class_al[12];',
    'Fix12b case45 BAN'
)

# 12c: case 58 - trade complete
fix(
    '                case 58: {\n                    this.a.q.s = ((Class_bo)object).a().readInt();\n                    this.a.p.e();\n                    this.a.p.e = false;\n                    this.a.l.b();\n                    this.a.p.d();\n                    return;\n                }',
    '                case 58: {\n                    this.a.q.s = ((Class_bo)object).a().readInt();\n                    // BAN: giao dich hoan thanh\n                    this.a.p.banSeller.onTradeComplete(this.a.q.s);\n                    this.a.p.e();\n                    this.a.p.e = false;\n                    this.a.l.b();\n                    this.a.p.d();\n                    return;\n                }',
    'Fix12c case58 BAN'
)

# 12d: case 57 - trade cancelled
fix(
    '                case 57: {\n                    if (this.a.q.y != null) {\n                        this.a.g.j();\n                        this.a.q.y = null;\n                    }\n                    this.a.p.e = false;\n                    this.a.p.d();\n                    return;\n                }',
    '                case 57: {\n                    if (this.a.q.y != null) {\n                        this.a.g.j();\n                        this.a.q.y = null;\n                    }\n                    // BAN: giao dich bi huy\n                    this.a.p.banSeller.onTradeCancelled();\n                    this.a.p.e = false;\n                    this.a.p.d();\n                    return;\n                }',
    'Fix12d case57 BAN'
)

# 12e: case 8 - item bag updated (for split callback)
fix(
    '                    this.a.q.b();\n                    this.a.g.g();\n                    return;\n                }\n                case 10:',
    '                    this.a.q.b();\n                    this.a.g.g();\n                    // BAN: bag slot updated, notify BanSeller\n                    this.a.p.banSeller.onItemUpdated();\n                    return;\n                }\n                case 10:',
    'Fix12e case8 BAN'
)

with open(path_out, 'w', encoding='utf-8', newline='\n') as f:
    f.write(src)
print(f"\nTotal fixes applied: {fixes_done}")
print("Written to:", path_out)
