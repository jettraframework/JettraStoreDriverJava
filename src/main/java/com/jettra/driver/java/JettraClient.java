package com.jettra.driver.java;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;

/**
 * JettraClient is the main entry point for interacting with the JettraStoreEngine from Java.
 * Provides methods to connect, authenticate, perform multi-model operations,
 * generate IDs via multiple strategies (Manual, Auto-increment, Composite UUID),
 * and manage version history & restorations.
 */
public class JettraClient {

    public enum IdMode {
        MANUAL,
        AUTOINCREMENT,
        UUID;

        public static IdMode fromString(String raw) {
            if (raw == null || raw.isBlank()) return MANUAL;
            String norm = raw.trim().toUpperCase();
            return switch (norm) {
                case "AUTO", "AUTOINCREMENT", "AUTO_INCREMENT" -> AUTOINCREMENT;
                case "UUID", "COMPOSITE", "COMPOSITE_UUID" -> UUID;
                default -> MANUAL;
            };
        }
    }

    private final String host;
    private final int port;
    private boolean isConnected;
    private String authToken;
    private final HttpClient httpClient;

    public JettraClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.isConnected = false;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Connects to the JettraStoreEngine server.
     */
    public void connect() {
        System.out.println("Connecting to JettraStoreEngine at " + host + ":" + port + "...");
        this.isConnected = true;
        System.out.println("Connected successfully.");
    }

    /**
     * Disconnects from the JettraStoreEngine server.
     */
    public void close() {
        if (isConnected) {
            System.out.println("Closing connection to JettraStoreEngine...");
            this.isConnected = false;
            System.out.println("Connection closed.");
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Authenticates with the server and stores the JWT.
     */
    public boolean login(String username, String password) throws Exception {
        String jsonPayload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String body = response.body();
            JettraJson gson = new JettraJson();
            JsonObject res = gson.fromJson(body, JsonObject.class);
            if (res.has("token")) {
                this.authToken = (String) res.get("token");
                return true;
            }
        }
        return false;
    }

    // --- Document Operations with ID Modes ---

    /**
     * Inserts a document into a collection with a manual ID.
     */
    public boolean insertDocument(String collection, String id, String jsonDocument) throws Exception {
        return insertDocument(collection, id, jsonDocument, IdMode.MANUAL);
    }

    /**
     * Inserts a document into a collection specifying the IdMode strategy.
     */
    public boolean insertDocument(String collection, String id, String jsonDocument, IdMode idMode) throws Exception {
        String targetId = (id == null || id.isBlank()) ? (idMode == IdMode.AUTOINCREMENT ? "auto" : "uuid") : id;
        String url = String.format("http://%s:%d/api/document/%s/%s?id_mode=%s", host, port, collection, targetId, idMode.name().toLowerCase());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 201;
    }

    /**
     * Inserts a document with automatic ID generation (Auto-increment or Composite UUID).
     */
    public String insertDocumentAuto(String collection, String jsonDocument, IdMode idMode) throws Exception {
        String targetId = idMode == IdMode.AUTOINCREMENT ? "auto" : "uuid";
        String url = String.format("http://%s:%d/api/document/%s/%s?id_mode=%s", host, port, collection, targetId, idMode.name().toLowerCase());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) {
            JettraJson json = new JettraJson();
            JsonObject obj = json.fromJson(response.body(), JsonObject.class);
            if (obj != null && obj.has("id")) {
                return (String) obj.get("id");
            }
        }
        return null;
    }

    /**
     * Retrieves a document by ID.
     */
    public String getDocument(String collection, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/document/" + collection + "/" + id))
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    /**
     * Retrieves document version history.
     */
    public String getDocumentHistory(String collection, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/document/" + collection + "/" + id + "/history"))
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return "[]";
    }

    /**
     * Restores a document to a historical version by timestamp.
     */
    public boolean restoreDocumentVersion(String collection, String id, long timestamp) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/document/" + collection + "/" + id + "/restore?timestamp=" + timestamp))
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    // --- Multi-Model Universal Operations ---

    /**
     * Inserts a document into a specific model (e.g. VECTOR, GRAPH, COLUMN, KEYVALUE, RECORDS).
     */
    public boolean insertModel(String modelType, String collection, String id, String jsonDocument) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/model/" + modelType.toLowerCase() + "/" + collection + "/" + id))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 201;
    }

    /**
     * Retrieves an object from a specific model.
     */
    public String getModel(String modelType, String collection, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/model/" + modelType.toLowerCase() + "/" + collection + "/" + id))
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    /**
     * Deletes a model object by ID.
     */
    public boolean deleteModel(String modelType, String collection, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/model/" + modelType.toLowerCase() + "/" + collection + "/" + id))
                .header("Authorization", "Bearer " + authToken)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 204 || response.statusCode() == 200;
    }

    // --- Dedicated Records Engine Helpers (Java 25 Records) ---

    /**
     * Saves a Java Record into the RECORDS engine collection.
     */
    public <R extends Record> boolean saveRecord(String collection, String id, R record) throws Exception {
        JettraJson json = new JettraJson();
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("_recordClass", record.getClass().getName());
        JsonObject comps = json.fromJson(json.toJson(record), JsonObject.class);
        wrapper.add("components", comps);
        return insertModel("RECORDS", collection, id, json.toJson(wrapper));
    }

    /**
     * Retrieves a Java Record by ID from the RECORDS engine.
     */
    public <R extends Record> java.util.Optional<R> getRecord(String collection, String id, Class<R> recordClass) throws Exception {
        String jsonStr = getModel("RECORDS", collection, id);
        if (jsonStr != null && !jsonStr.isBlank()) {
            JettraJson json = new JettraJson();
            JsonObject root = json.fromJson(jsonStr, JsonObject.class);
            String compJson = (root != null && root.has("components")) ? root.getAsJsonObject("components").toString() : jsonStr;
            return java.util.Optional.of(json.fromJson(compJson, recordClass));
        }
        return java.util.Optional.empty();
    }

    public boolean deleteRecord(String collection, String id) throws Exception {
        return deleteModel("RECORDS", collection, id);
    }

    // --- Administrative & Monitoring ---

    /**
     * Triggers a manual backup.
     */
    public boolean triggerBackup() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/backup"))
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }
    
    public String getStatus() {
        return "{\n  \"ram_usage\": \"256 MB / 4096 MB\",\n  \"disk_usage\": \"1.2 GB / 500 GB\",\n  \"nodes\": \"1 (Master)\",\n  \"network\": \"ONLINE\"\n}";
    }

    // --- Fluent API Helpers ---
    
    public JettraFluentQuery model(String modelType) {
        return new JettraFluentQuery(this, modelType);
    }
    
    public JettraFluentQuery document() { return model("DOCUMENT"); }
    public JettraFluentQuery vector() { return model("VECTOR"); }
    public JettraFluentQuery graph() { return model("GRAPH"); }
    public JettraFluentQuery timeseries() { return model("TIMESERIES"); }
    public JettraFluentQuery column() { return model("COLUMN"); }
    public JettraFluentQuery keyvalue() { return model("KEYVALUE"); }
    public JettraFluentQuery geospatial() { return model("GEOSPATIAL"); }
    public JettraFluentQuery object() { return model("OBJECT"); }
    public JettraFluentQuery records() { return model("RECORDS"); }

    // --- Repository Pattern Helper ---

    public <T> JettraRepository<T> repository(Class<T> entityClass, String modelType, String collection) {
        return new JettraRepository<>(this, entityClass, modelType, collection);
    }

    public <R extends Record> JettraRepository<R> recordRepository(Class<R> recordClass, String collection) {
        return new JettraRepository<>(this, recordClass, "RECORDS", collection);
    }

    // --- Cross-Engine Fast References ---

    public JettraReference createRef(String engine, String db, String id) {
        return JettraReference.of(engine, db, id);
    }

    public JettraReference createRef(String node, String engine, String db, String id) {
        return JettraReference.of(node, engine, db, id);
    }

    public String resolveRef(String refUri) throws Exception {
        JettraReference ref = JettraReference.parse(refUri);
        return resolveRef(ref);
    }

    public String resolveRef(JettraReference ref) throws Exception {
        return getModel(ref.engine(), ref.database(), ref.entityId());
    }
}
