package com.space.model;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a file or directory node in the disk usage tree.
 */
public class FileNode {
    private final Path path;
    private final String name;
    private final boolean isDirectory;
    private long size; // Total size including children
    private long ownSize; // Size of files directly in this directory (not including subdirectories)
    private final List<FileNode> children;
    private FileNode parent;
    private long lastModified;

    public FileNode(Path path, boolean isDirectory) {
        this.path = path;
        this.name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        this.isDirectory = isDirectory;
        this.children = isDirectory ? new ArrayList<>() : Collections.emptyList();
        this.size = 0;
        this.ownSize = 0;
        this.lastModified = 0;
    }

    public FileNode(File file) {
        this(file.toPath(), file.isDirectory());
        this.lastModified = file.lastModified();
    }

    public void addChild(FileNode child) {
        if (!isDirectory) {
            throw new IllegalStateException("Cannot add children to a file node");
        }
        child.parent = this;
        children.add(child);
    }

    public void removeChild(FileNode child) {
        children.remove(child);
        child.parent = null;
    }

    public void updateSize(long size) {
        long delta = size - this.size;
        this.size = size;

        // Propagate size change up the tree
        if (parent != null) {
            parent.updateTotalSize(delta);
        }
    }

    private void updateTotalSize(long delta) {
        this.size += delta;
        if (parent != null) {
            parent.updateTotalSize(delta);
        }
    }

    public void calculateSizes() {
        if (!isDirectory) {
            // For files, ownSize should equal size
            this.ownSize = this.size;
            return;
        }

        long totalSize = 0;
        long directFileSize = 0;

        for (FileNode child : children) {
            // Recursively calculate sizes for all children
            child.calculateSizes();

            totalSize += child.getSize();

            if (!child.isDirectory) {
                directFileSize += child.getSize();
            }
        }

        this.size = totalSize;
        this.ownSize = directFileSize;
    }

    public double getPercentageOfParent() {
        if (parent == null || parent.getSize() == 0) {
            return 100.0;
        }
        return (size * 100.0) / parent.getSize();
    }

    public List<FileNode> getTopChildren(int limit) {
        return children.stream()
                .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
                .limit(limit)
                .toList();
    }

    // Getters
    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getOwnSize() {
        return ownSize;
    }

    public void setOwnSize(long ownSize) {
        this.ownSize = ownSize;
    }

    public List<FileNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public FileNode getParent() {
        return parent;
    }

    public void setParent(FileNode parent) {
        this.parent = parent;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String toString() {
        return name + " (" + formatSize(size) + ")";
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
