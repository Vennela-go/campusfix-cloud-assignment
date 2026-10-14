package com.campusfix;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** CampusFix: a small dependency-free Java HTTP application for CI/CD demonstrations. */
public class CampusFixApplication {
    private static final Map<Integer, Issue> ISSUES = new ConcurrentHashMap<>();
    private static int nextId = 1;
    private static final Object ID_LOCK = new Object();
    private static final String[] CATEGORIES = {"Classroom", "Wi-Fi", "Water", "Electrical", "Lab Equipment", "Other"};
    private static final String[] STATUSES = {"Open", "In Progress", "Resolved"};

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        seed();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/health", ex -> send(ex, 200, "application/json", "{\"status\":\"UP\",\"app\":\"CampusFix\"}"));
        server.createContext("/api/issues", CampusFixApplication::handleIssues);
        server.createContext("/", CampusFixApplication::handleStatic);
        server.start();
        System.out.println("CampusFix is running on port " + port);
    }

    private static void seed() {
        addSeed("Projector not working in Room 204", "Classroom", "The classroom projector does not turn on.", "Open");
        addSeed("Wi-Fi is slow in the library", "Wi-Fi", "Connection drops frequently near the reading area.", "In Progress");
        addSeed("Water tap leaking near Block B", "Water", "The tap keeps leaking after use.", "Resolved");
    }
    private static void addSeed(String title, String category, String description, String status) {
        synchronized (ID_LOCK) { int id = nextId++; ISSUES.put(id, new Issue(id, title, category, description, status)); }
    }

    private static void handleIssues(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        if (path.equals("/api/issues") || path.equals("/api/issues/")) {
            if (method.equals("GET")) {
                List<Issue> items = new ArrayList<>(ISSUES.values()); items.sort(Comparator.comparingInt(i -> i.id));
                StringJoiner j = new StringJoiner(",", "[", "]"); for (Issue i : items) j.add(i.json());
                send(ex, 200, "application/json", j.toString()); return;
            }
            if (method.equals("POST")) {
                Map<String,String> b = parseBody(ex); String title = clean(b.get("title")); String category = clean(b.get("category")); String description = clean(b.get("description"));
                if (title.isBlank() || category.isBlank() || description.isBlank()) { send(ex, 400, "application/json", "{\"error\":\"Title, category and description are required\"}"); return; }
                if (!Arrays.asList(CATEGORIES).contains(category)) { send(ex, 400, "application/json", "{\"error\":\"Invalid category\"}"); return; }
                synchronized (ID_LOCK) { int id = nextId++; Issue issue = new Issue(id, title, category, description, "Open"); ISSUES.put(id, issue); send(ex, 201, "application/json", issue.json()); } return;
            }
            send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        String prefix = "/api/issues/";
        if (path.startsWith(prefix)) {
            String suffix = path.substring(prefix.length());
            if (suffix.contains("/")) { send(ex, 404, "application/json", "{\"error\":\"Not found\"}"); return; }
            int id; try { id = Integer.parseInt(suffix); } catch (Exception e) { send(ex, 400, "application/json", "{\"error\":\"Invalid issue id\"}"); return; }
            Issue old = ISSUES.get(id); if (old == null) { send(ex, 404, "application/json", "{\"error\":\"Issue not found\"}"); return; }
            if (method.equals("PUT")) {
                Map<String,String> b = parseBody(ex); String status = clean(b.get("status"));
                if (!Arrays.asList(STATUSES).contains(status)) { send(ex, 400, "application/json", "{\"error\":\"Status must be Open, In Progress or Resolved\"}"); return; }
                Issue updated = new Issue(old.id, old.title, old.category, old.description, status); ISSUES.put(id, updated); send(ex, 200, "application/json", updated.json()); return;
            }
            if (method.equals("DELETE")) { ISSUES.remove(id); send(ex, 200, "application/json", "{\"deleted\":true,\"id\":" + id + "}"); return; }
            send(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}"); return;
        }
        send(ex, 404, "application/json", "{\"error\":\"Not found\"}");
    }

    private static void handleStatic(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send(ex, 405, "text/plain; charset=utf-8", "Method not allowed"); return; }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.equals("/index.html")) {
            try (var in = CampusFixApplication.class.getResourceAsStream("/static/index.html")) {
                if (in == null) { send(ex, 500, "text/plain; charset=utf-8", "UI resource missing"); return; }
                send(ex, 200, "text/html; charset=utf-8", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } else send(ex, 404, "text/plain; charset=utf-8", "Not found");
    }

    private static Map<String,String> parseBody(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String,String> out = new HashMap<>();
        Pattern p = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\""); Matcher m = p.matcher(body);
        while (m.find()) out.put(m.group(1), unescape(m.group(2)));
        return out;
    }
    private static String unescape(String s) { return s.replace("\\\\\"", "\"").replace("\\\\n", " ").replace("\\\\r", " ").replace("\\\\\\\\", "\\"); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("<", "\\u003c").replace(">", "\\u003e"); }
    private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type", contentType); ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length); try (var os = ex.getResponseBody()) { os.write(bytes); }
    }
    private static class Issue {
        final int id; final String title, category, description, status;
        Issue(int id, String title, String category, String description, String status) { this.id=id; this.title=title; this.category=category; this.description=description; this.status=status; }
        String json() { return "{\"id\":"+id+",\"title\":\""+escape(title)+"\",\"category\":\""+escape(category)+"\",\"description\":\""+escape(description)+"\",\"status\":\""+escape(status)+"\"}"; }
    }
}
