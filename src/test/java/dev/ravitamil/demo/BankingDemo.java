package dev.ravitamil.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Synthetic test fixture only. Not a banking application or production security reference. */
public final class BankingDemo implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<String, Account> sessions = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final HttpServer server;

    public BankingDemo() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                byte[] html;
                try (var stream = getClass().getResourceAsStream("/demo/index.html")) {
                    if (stream == null) throw new IOException("Missing demo HTML");
                    html = stream.readAllBytes();
                }
                send(exchange, 200, "text/html; charset=utf-8", html);
                return;
            }
            if (path.equals("/api/login") && exchange.getRequestMethod().equals("POST")) {
                JsonNode body = JSON.readTree(exchange.getRequestBody());
                if (!"qa@example.test".equals(body.path("email").asText()) || !"Demo123!".equals(body.path("password").asText())) {
                    json(exchange, 401, Map.of("error", "Invalid demo credentials"));
                    return;
                }
                String token = UUID.randomUUID().toString();
                sessions.put(token, new Account());
                exchange.getResponseHeaders().add("Set-Cookie", "demo_session=" + token + "; HttpOnly; SameSite=Lax; Path=/");
                json(exchange, 200, Map.of("name", "Alex Morgan"));
                return;
            }
            Account account = account(exchange);
            if (account == null) { json(exchange, 401, Map.of("error", "Sign in required")); return; }
            synchronized (account) {
                switch (path) {
                    case "/api/account" -> json(exchange, 200, Map.of("name", "Alex Morgan", "balance", account.balance, "currency", "GBP", "transactions", account.transactions));
                    case "/api/transfer" -> transfer(exchange, account);
                    case "/api/statement" -> {
                        StringBuilder csv = new StringBuilder("recipient,amount,reference\n");
                        for (var item : account.transactions) csv.append(item.get("recipient")).append(',').append(item.get("amount")).append(',').append(item.get("reference")).append('\n');
                        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=statement.csv");
                        send(exchange, 200, "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    case "/api/logout" -> {
                        sessions.remove(sessionToken(exchange));
                        exchange.getResponseHeaders().add("Set-Cookie", "demo_session=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax");
                        json(exchange, 200, Map.of("ok", true));
                    }
                    default -> json(exchange, 404, Map.of("error", "Not found"));
                }
            }
        } catch (IllegalArgumentException exception) {
            json(exchange, 400, Map.of("error", "Invalid request"));
        } catch (Exception exception) {
            json(exchange, 500, Map.of("error", "Demo server error"));
        } finally { exchange.close(); }
    }

    private void transfer(HttpExchange exchange, Account account) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) { json(exchange, 405, Map.of("error", "POST required")); return; }
        JsonNode body = JSON.readTree(exchange.getRequestBody());
        BigDecimal amount;
        try { amount = new BigDecimal(body.path("amount").asText()).setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (RuntimeException exception) { json(exchange, 422, Map.of("error", "Amount must have at most two decimals")); return; }
        String recipient = body.path("recipient").asText();
        if (!List.of("Jamie Lee", "Sam Patel").contains(recipient) || amount.signum() <= 0) {
            json(exchange, 422, Map.of("error", "Choose a recipient and a positive amount")); return;
        }
        if (amount.compareTo(account.balance) > 0) { json(exchange, 422, Map.of("error", "Insufficient funds")); return; }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (key == null || key.isBlank()) { json(exchange, 400, Map.of("error", "Idempotency key required")); return; }
        String signature = recipient + ":" + amount.toPlainString();
        if (account.idempotency.containsKey(key)) {
            var previous = account.idempotency.get(key);
            if (!signature.equals(previous.signature())) { json(exchange, 409, Map.of("error", "Idempotency key already used for another transfer")); return; }
            json(exchange, 200, previous.response()); return;
        }
        account.balance = account.balance.subtract(amount);
        Map<String, Object> transaction = Map.of("recipient", recipient, "amount", amount, "reference", "TX-" + UUID.randomUUID().toString().substring(0, 8));
        account.transactions.add(transaction);
        Map<String, Object> response = Map.of("balance", account.balance, "transaction", transaction);
        account.idempotency.put(key, new SavedTransfer(signature, response));
        json(exchange, 201, response);
    }

    private String sessionToken(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return "";
        for (String part : cookie.split(";")) if (part.trim().startsWith("demo_session=")) return part.trim().substring("demo_session=".length());
        return "";
    }
    private Account account(HttpExchange exchange) { return sessions.get(sessionToken(exchange)); }
    private void json(HttpExchange exchange, int status, Object body) throws IOException { send(exchange, status, "application/json", JSON.writeValueAsBytes(body)); }
    private void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }
    private static final class Account {
        BigDecimal balance = new BigDecimal("2500.00");
        final List<Map<String, Object>> transactions = new ArrayList<>();
        final Map<String, SavedTransfer> idempotency = new ConcurrentHashMap<>();
    }
    private record SavedTransfer(String signature, Map<String, Object> response) { }
}
