package com.jettra.driver.java;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import java.util.List;
import java.util.Optional;

/**
 * Repository pattern implementation for JettraStoreEngine.
 * Allows mapping a Java class directly to a Model Collection.
 */
public class JettraRepository<T> {

    private final JettraClient client;
    private final Class<T> entityClass;
    private final String modelType;
    private final String collection;
    private final JettraJson gson;

    public JettraRepository(JettraClient client, Class<T> entityClass, String modelType, String collection) {
        this.client = client;
        this.entityClass = entityClass;
        this.modelType = modelType;
        this.collection = collection;
        this.gson = new JettraJson();
    }

    public boolean save(String id, T entity) {
        try {
            String json = gson.toJson(entity);
            return client.insertModel(modelType, collection, id, json);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Optional<T> findById(String id) {
        try {
            String json = client.getModel(modelType, collection, id);
            if (json != null && !json.isEmpty()) {
                return Optional.of(gson.fromJson(json, entityClass));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
}
