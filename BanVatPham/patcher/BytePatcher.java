import java.io.*;
import java.nio.file.*;

/**
 * BytePatcher: tìm và thay thế chuỗi byte trong file .class (constant pool string patch).
 * Chỉ thay thế chuỗi CÓ CÙNG ĐỘ DÀI BYTE để không làm hỏng offset trong file.
 */
public class BytePatcher {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: BytePatcher <file> <oldHex> <newHex>");
            System.exit(1);
        }
        String file = args[0];
        byte[] oldBytes = hexToBytes(args[1]);
        byte[] newBytes = hexToBytes(args[2]);
        if (oldBytes.length != newBytes.length) {
            System.err.println("ERROR: old and new byte arrays must be same length!");
            System.exit(1);
        }
        byte[] data = Files.readAllBytes(Paths.get(file));
        int count = 0;
        for (int i = 0; i <= data.length - oldBytes.length; i++) {
            if (matches(data, i, oldBytes)) {
                System.arraycopy(newBytes, 0, data, i, newBytes.length);
                count++;
                i += oldBytes.length - 1;
            }
        }
        if (count == 0) {
            System.err.println("WARNING: pattern not found in " + file);
        } else {
            Files.write(Paths.get(file), data);
            System.out.println("Patched " + count + " occurrence(s) in " + file);
        }
    }

    static boolean matches(byte[] data, int offset, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
