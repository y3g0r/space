package com.diskanalyzer.model;

import java.time.Instant;

/**
 * Represents the result of a disk scan operation.
 */
public class ScanResult {
    private final FileNode root;
    private final Instant scanTime;
    private final String scanPath;
    private final long totalFiles;
    private final long totalDirectories;

    public ScanResult(FileNode root, String scanPath, long totalFiles, long totalDirectories) {
        this.root = root;
        this.scanPath = scanPath;
        this.scanTime = Instant.now();
        this.totalFiles = totalFiles;
        this.totalDirectories = totalDirectories;
    }

    public FileNode getRoot() {
        return root;
    }

    public Instant getScanTime() {
        return scanTime;
    }

    public String getScanPath() {
        return scanPath;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public long getTotalDirectories() {
        return totalDirectories;
    }

    public long getTotalSize() {
        return root != null ? root.getSize() : 0;
    }

    @Override
    public String toString() {
        return String.format("ScanResult[path=%s, files=%d, dirs=%d, size=%s, time=%s]",
                scanPath, totalFiles, totalDirectories,
                FileNode.formatSize(getTotalSize()), scanTime);
    }
}
