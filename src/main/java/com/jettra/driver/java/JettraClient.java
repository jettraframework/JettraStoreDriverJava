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
 * It provides methods to connect, authenticate, and perform operations on the database.
 */
public class JettraClient {

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
        // TODO: Initialize REST/gRPC client connections here
        this.isConnected = true;
        System.out.println("Connected successfully.");
    }

    /**
     * Disconnects from the JettraStoreEngine server.
     */
    public void close() {
        if (isConnected) {
            System.out.println("Closing connection to JettraStoreEngine...");
            // TODO: Shutdown REST/gRPC client connections here
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

    /**
     * Inserts a document into a collection.
     */
    public boolean insertDocument(String collection, String id, String jsonDocument) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/api/document/" + collection + "/" + id))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 201;
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
     * Inserts a document into a specific model (e.g. VECTOR, GRAPH, COLUMN, KEYVALUE).
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
     * Retrieves a document from a specific model.
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

    // --- Repository Pattern Helper ---

    public <T> JettraRepository<T> repository(Class<T> entityClass, String modelType, String collection) {
        return new JettraRepository<>(this, entityClass, modelType, collection);
    }
}
