import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class AtmPaymentService {
    private static AtmPaymentService instance;
    private HttpServer server;
    private WalletService walletService;
    private WalletStore walletStore;
    private String sepaySecret;
    private int port;

    private AtmPaymentService() {}

    public static synchronized AtmPaymentService getInstance() {
        if (instance == null) instance = new AtmPaymentService();
        return instance;
    }

    public synchronized void start(int port, String sepaySecret, WalletService walletService, WalletStore walletStore) {
        this.walletService = walletService;
        this.walletStore = walletStore;
        if (server != null) return;
        this.port = port;
        this.sepaySecret = sepaySecret != null ? sepaySecret : "";
        if (this.sepaySecret.isEmpty()) {
            log("!!! WARNING: SEPAY_SECRET is EMPTY while ATM=1 - webhook on port " + port + " will accept UNAUTHENTICATED requests! Set [SEPAY_SECRET] in banvatpham.txt !!!");
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/webhook/sepay", new SepayHandler());
            server.setExecutor(Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) { Thread t = new Thread(r); t.setDaemon(true); return t; }
            }));
            server.start();
            log("ATM webhook started on port " + port);
        } catch (Exception e) {
            log("ATM webhook start failed port " + port + ": " + e.getMessage());
            server = null;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            try { server.stop(0); } catch (Exception ignored) {}
            server = null;
            log("ATM webhook stopped");
        }
    }

    public synchronized boolean isRunning() { return server != null; }

    public synchronized void updateWalletService(WalletService ws, WalletStore store) { this.walletService = ws; if (store != null) this.walletStore = store; }

    private void log(String msg) {
        // WalletService.logAtm handles console + atm_webhook.log file
        try { if (walletService != null) walletService.logAtm(msg); else System.out.println(msg); } catch (Exception ignored) {}
    }

    private class SepayHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = "";
            try {
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    send(ex, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }
                // verify secret
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                String secretHeader = ex.getRequestHeaders().getFirst("X-Sepay-Secret");
                if (secretHeader == null) secretHeader = ex.getRequestHeaders().getFirst("SEPAY_SECRET");
                String provided = auth != null ? auth : secretHeader;
                // also check Bearer token: "Apikey xxx" or plain
                String expected = sepaySecret;
                if (expected != null && !expected.isEmpty()) {
                    boolean ok = false;
                    if (provided != null) {
                        String p = provided.trim();
                        // allow "Bearer <secret>" or "Apikey <secret>" or plain
                        if (p.contains(" ")) p = p.substring(p.lastIndexOf(' ') + 1).trim();
                        if (p.equals(expected)) ok = true;
                        if (provided.contains(expected)) ok = true;
                    }
                    if (!ok) {
                        // also check query/ fallback: header may be missing but body contains secret? reject
                        log("ATM webhook invalid secret: provided=" + provided);
                        send(ex, 401, "{\"error\":\"invalid secret\"}");
                        return;
                    }
                }
                // read body
                InputStream is = ex.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                body = new String(baos.toByteArray(), "UTF-8");
                log("ATM webhook raw: " + body);

                Parsed p = parseBody(body);
                if (p == null || p.transactionId == null || p.transactionId.isEmpty() || p.amount == null) {
                    log("ATM webhook missing fields: " + body);
                    send(ex, 400, "{\"error\":\"missing fields\"}");
                    return;
                }
                if (walletService == null || walletStore == null) {
                    send(ex, 500, "{\"error\":\"no wallet service\"}");
                    return;
                }
                if (walletService.isTransactionSeen(p.transactionId)) {
                    log("ATM webhook replay rejected id=" + p.transactionId);
                    send(ex, 200, "{\"status\":\"duplicate\"}");
                    return;
                }
                String content = p.content != null ? p.content : "";
                long amount = p.amount.longValue();
                int result = walletService.handleAtmWebhook(walletStore, amount, content, p.transactionId);
                if (result == 1) send(ex, 200, "{\"status\":\"credited\"}");
                else if (result == 0) { log("ATM webhook replay rejected id=" + p.transactionId); send(ex, 200, "{\"status\":\"duplicate\"}"); }
                else send(ex, 200, "{\"status\":\"unmatched\"}");
            } catch (Exception e) {
                log("ATM webhook handler error: " + e.getMessage());
                try { send(ex, 500, "{\"error\":\"internal\"}"); } catch (Exception ignored) {}
            }
        }
        private void send(HttpExchange ex, int code, String json) throws IOException {
            byte[] b = json.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(code, b.length);
            OutputStream os = ex.getResponseBody();
            os.write(b);
            os.close();
        }
    }

    private static class Parsed {
        Long amount;
        String content;
        String transactionId;
    }

    private static Parsed parseBody(String body) {
        if (body == null) return null;
        Parsed p = new Parsed();
        // amount fields: transferAmount, amount
        p.amount = extractLong(body, "transferAmount");
        if (p.amount == null) p.amount = extractLong(body, "amount");
        // content fields
        p.content = extractString(body, "transferContent");
        if (p.content == null) p.content = extractString(body, "content");
        if (p.content == null) p.content = extractString(body, "description");
        if (p.content == null) p.content = extractString(body, "addInfo");
        // transaction id
        p.transactionId = extractString(body, "transaction_id");
        if (p.transactionId == null) p.transactionId = extractString(body, "referenceCode");
        if (p.transactionId == null) p.transactionId = extractString(body, "id");
        if (p.transactionId == null) p.transactionId = extractString(body, "transactionId");
        // numeric id may be number not string
        if (p.transactionId == null) {
            Long nid = extractLong(body, "id");
            if (nid != null) p.transactionId = String.valueOf(nid);
        }
        // fallback: raw content as body itself
        if (p.content == null) p.content = body;
        return p;
    }

    private static Long extractLong(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx == -1) idx = json.indexOf("'" + key + "'");
            if (idx == -1) return null;
            int colon = json.indexOf(":", idx);
            if (colon == -1) return null;
            int i = colon + 1;
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '"' || json.charAt(i) == '\'')) i++;
            int j = i;
            while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
            if (j <= i) return null;
            String num = json.substring(i, j).trim().replaceAll("[^0-9-]", "");
            if (num.isEmpty()) return null;
            return Long.parseLong(num);
        } catch (Exception e) { return null; }
    }

    private static String extractString(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx == -1) idx = json.indexOf("'" + key + "'");
            if (idx == -1) return null;
            int colon = json.indexOf(":", idx);
            if (colon == -1) return null;
            int i = colon + 1;
            while (i < json.length() && json.charAt(i) == ' ') i++;
            if (i >= json.length()) return null;
            if (json.charAt(i) == '"' || json.charAt(i) == '\'') {
                char q = json.charAt(i);
                int j = json.indexOf(q, i + 1);
                if (j == -1) return null;
                return json.substring(i + 1, j);
            } else {
                int j = i;
                while (j < json.length() && json.charAt(j) != ',' && json.charAt(j) != '}' && json.charAt(j) != '\n') j++;
                String v = json.substring(i, j).trim().replaceAll("^\"|\"$", "");
                if (v.isEmpty() || v.equals("null")) return null;
                return v;
            }
        } catch (Exception e) { return null; }
    }
}
