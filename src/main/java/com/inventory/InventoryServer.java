package com.inventory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class InventoryServer {
    private static final Inventory inventory = new Inventory();
    private static final Path publicDirectory = Paths.get("public").toAbsolutePath();

    public static void main(String[] args) throws IOException {
        seedProducts();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/inventory", InventoryServer::handleInventory);
        server.createContext("/api/health", InventoryServer::handleHealth);
        server.createContext("/", InventoryServer::serveFrontend);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Java inventory server is running on port " + port);
    }

    private static void seedProducts() {
        inventory.addProduct(new Product("P1001", "Wireless Headphones", "Audio", 85, 49.99, 79.99));
        inventory.addProduct(new Product("P1002", "Mechanical Keyboard", "Accessories", 42, 58.00, 94.99));
        inventory.addProduct(new Product("P1003", "USB-C Hub", "Accessories", 120, 18.50, 34.99));
        inventory.addProduct(new Product("P1004", "Smart Desk Lamp", "Office", 27, 32.00, 59.99));
        inventory.addProduct(new Product("P1005", "Portable SSD 1TB", "Storage", 64, 61.00, 109.99));
        inventory.addProduct(new Product("P1006", "Webcam Full HD", "Video", 18, 38.00, 69.99));
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"OK\",\"message\":\"Java inventory server is running\"}");
    }

    private static void handleInventory(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                if ("products".equals(query.get("view"))) {
                    sendJson(exchange, 200, inventory.productsJson());
                } else if ("report".equals(query.get("view"))) {
                    sendJson(exchange, 200, inventory.reportJson(query.get("from"), query.get("to")));
                } else {
                    sendJson(exchange, 200, inventory.dashboardJson());
                }
                return;
            }
            if (!"POST".equals(method)) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseQuery(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String action = body.get("action");
            if ("purchase".equals(action) || "shipping".equals(action)) {
                int quantity = Integer.parseInt(body.getOrDefault("quantity", "0"));
                String productId = body.get("productId");
                String customer = body.getOrDefault("customer", "Walk-in customer");
                Transaction transaction = inventory.process(action, productId, quantity, customer);
                sendJson(exchange, 200, transaction.json());
            } else {
                sendJson(exchange, 400, "{\"error\":\"Action must be purchase or shipping\"}");
            }
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(error.getMessage()) + "\"}");
        } catch (Exception error) {
            error.printStackTrace();
            sendJson(exchange, 500, "{\"error\":\"Unable to process inventory request\"}");
        }
    }

    private static void serveFrontend(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if ("/".equals(requested)) requested = "/index.html";
        Path file = publicDirectory.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(publicDirectory) || !Files.exists(file) || Files.isDirectory(file)) {
            sendJson(exchange, 404, "{\"error\":\"Page not found\"}");
            return;
        }
        String contentType = requested.endsWith(".css") ? "text/css" : requested.endsWith(".js") ? "application/javascript" : "text/html";
        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        return parseQuery(uri.getRawQuery());
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isBlank()) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static final class Product {
        private final String id;
        private final String name;
        private final String category;
        private int stock;
        private final double costPrice;
        private final double sellingPrice;

        Product(String id, String name, String category, int stock, double costPrice, double sellingPrice) {
            this.id = id; this.name = name; this.category = category;
            this.stock = stock; this.costPrice = costPrice; this.sellingPrice = sellingPrice;
        }
        String json() {
            return "{\"id\":\"" + escape(id) + "\",\"name\":\"" + escape(name) + "\",\"category\":\"" + escape(category)
                    + "\",\"stock\":" + stock + ",\"costPrice\":" + costPrice + ",\"sellingPrice\":" + sellingPrice
                    + ",\"margin\":" + (sellingPrice - costPrice) + "}";
        }
    }

    static final class Transaction {
        private final String type;
        private final Product product;
        private final int quantity;
        private final String customer;
        private final double total;
        private final LocalDate date;

        Transaction(String type, Product product, int quantity, String customer, double total) {
            this.type = type; this.product = product; this.quantity = quantity;
            this.customer = customer; this.total = total; this.date = LocalDate.now();
        }
        String json() {
            return "{\"message\":\"" + (type.equals("purchase") ? "Purchase recorded" : "Shipment recorded")
                    + "\",\"type\":\"" + type + "\",\"product\":\"" + escape(product.name)
                    + "\",\"quantity\":" + quantity + ",\"total\":" + total + ",\"date\":\"" + date + "\"}";
        }
    }

    static final class Inventory {
        private final List<Product> products = new ArrayList<>();
        private final List<Transaction> transactions = new ArrayList<>();

        void addProduct(Product product) { products.add(product); }

        synchronized Transaction process(String action, String productId, int quantity, String customer) {
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
            Product product = products.stream().filter(item -> item.id.equals(productId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            if ("shipping".equals(action) && product.stock < quantity) {
                throw new IllegalArgumentException("Insufficient stock for " + product.name);
            }
            if ("purchase".equals(action)) product.stock += quantity;
            else product.stock -= quantity;
            double total = quantity * ("purchase".equals(action) ? product.costPrice : product.sellingPrice);
            Transaction transaction = new Transaction(action, product, quantity, customer, total);
            transactions.add(transaction);
            return transaction;
        }

        synchronized String productsJson() {
            return "[" + products.stream().map(Product::json).reduce((a, b) -> a + "," + b).orElse("") + "]";
        }

        synchronized String dashboardJson() {
            int totalUnits = products.stream().mapToInt(item -> item.stock).sum();
            double inventoryValue = products.stream().mapToDouble(item -> item.stock * item.costPrice).sum();
            double revenue = transactions.stream().filter(item -> item.type.equals("shipping")).mapToDouble(item -> item.total).sum();
            double cost = transactions.stream().filter(item -> item.type.equals("shipping")).mapToDouble(item -> item.quantity * item.product.costPrice).sum();
            long lowStock = products.stream().filter(item -> item.stock < 25).count();
            return "{\"products\":" + products.size() + ",\"units\":" + totalUnits + ",\"inventoryValue\":" + inventoryValue
                    + ",\"revenue\":" + revenue + ",\"profit\":" + (revenue - cost) + ",\"lowStock\":" + lowStock
                    + ",\"transactions\":" + transactions.size() + "}";
        }

        synchronized String reportJson(String from, String to) {
            LocalDate start = from == null || from.isBlank() ? LocalDate.MIN : LocalDate.parse(from);
            LocalDate end = to == null || to.isBlank() ? LocalDate.MAX : LocalDate.parse(to);
            return "[" + transactions.stream().filter(item -> !item.date.isBefore(start) && !item.date.isAfter(end))
                    .sorted(Comparator.comparing(item -> item.date))
                    .map(Transaction::json).reduce((a, b) -> a + "," + b).orElse("") + "]";
        }
    }
}
