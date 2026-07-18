import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Chuyển chuỗi UTF-8 thành hex string để dùng với BytePatcher.
 */
public class StringToHex {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: StringToHex <string>");
            System.exit(1);
        }
        byte[] bytes = args[0].getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        System.out.println("String: " + args[0]);
        System.out.println("Length: " + bytes.length + " bytes");
        System.out.println("Hex:    " + sb.toString());
    }
}
