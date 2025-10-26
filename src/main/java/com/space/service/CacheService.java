package com.space.service;

import com.space.model.FileNode;
import com.space.model.ScanResult;
import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Service for caching and loading scan results.
 */
public class CacheService {
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.space/cache";
    private final Gson gson;

    public CacheService() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(FileNode.class, new FileNodeSerializer())
                .registerTypeAdapter(FileNode.class, new FileNodeDeserializer())
                .setPrettyPrinting()
                .create();

        try {
            Files.createDirectories(Paths.get(CACHE_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create cache directory: " + e.getMessage());
        }
    }

    /**
     * Saves scan result to cache.
     */
    public void saveScanResult(ScanResult result) {
        try {
            String filename = getCacheFilename(result.getScanPath());
            Path cachePath = Paths.get(CACHE_DIR, filename);

            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("scanPath", result.getScanPath());
            cacheData.put("scanTime", result.getScanTime().toString());
            cacheData.put("totalFiles", result.getTotalFiles());
            cacheData.put("totalDirectories", result.getTotalDirectories());
            cacheData.put("root", result.getRoot());

            try (Writer writer = new FileWriter(cachePath.toFile())) {
                gson.toJson(cacheData, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save cache: " + e.getMessage());
        }
    }

    /**
     * Loads scan result from cache if available and not stale.
     */
    public Optional<ScanResult> loadScanResult(String scanPath) {
        try {
            String filename = getCacheFilename(scanPath);
            Path cachePath = Paths.get(CACHE_DIR, filename);

            if (!Files.exists(cachePath)) {
                return Optional.empty();
            }

            try (Reader reader = new FileReader(cachePath.toFile())) {
                JsonObject cacheData = gson.fromJson(reader, JsonObject.class);

                FileNode root = gson.fromJson(cacheData.get("root"), FileNode.class);
                long totalFiles = cacheData.get("totalFiles").getAsLong();
                long totalDirs = cacheData.get("totalDirectories").getAsLong();

                return Optional.of(new ScanResult(root, scanPath, totalFiles, totalDirs));
            }
        } catch (Exception e) {
            System.err.println("Failed to load cache: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Clears all cached data.
     */
    public void clearCache() {
        try {
            Files.walk(Paths.get(CACHE_DIR))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete cache file: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to clear cache: " + e.getMessage());
        }
    }

    private String getCacheFilename(String path) {
        return Base64.getEncoder().encodeToString(path.getBytes()) + ".json";
    }

    /**
     * Custom serializer for FileNode.
     */
    private static class FileNodeSerializer implements JsonSerializer<FileNode> {
        @Override
        public JsonElement serialize(FileNode node, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            json.addProperty("path", node.getPath().toString());
            json.addProperty("name", node.getName());
            json.addProperty("isDirectory", node.isDirectory());
            json.addProperty("size", node.getSize());
            json.addProperty("ownSize", node.getOwnSize());
            json.addProperty("lastModified", node.getLastModified());

            if (node.isDirectory() && !node.getChildren().isEmpty()) {
                JsonArray children = new JsonArray();
                for (FileNode child : node.getChildren()) {
                    children.add(context.serialize(child, FileNode.class));
                }
                json.add("children", children);
            }

            return json;
        }
    }

    /**
     * Custom deserializer for FileNode.
     */
    private static class FileNodeDeserializer implements JsonDeserializer<FileNode> {
        @Override
        public FileNode deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            Path path = Paths.get(obj.get("path").getAsString());
            boolean isDirectory = obj.get("isDirectory").getAsBoolean();

            FileNode node = new FileNode(path, isDirectory);
            node.setSize(obj.get("size").getAsLong());
            node.setOwnSize(obj.get("ownSize").getAsLong());
            node.setLastModified(obj.get("lastModified").getAsLong());

            if (obj.has("children")) {
                JsonArray children = obj.getAsJsonArray("children");
                for (JsonElement childElement : children) {
                    FileNode child = context.deserialize(childElement, FileNode.class);
                    node.addChild(child);
                }
            }

            return node;
        }
    }
}
