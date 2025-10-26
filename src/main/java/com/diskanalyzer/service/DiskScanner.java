package com.diskanalyzer.service;

import com.diskanalyzer.model.FileNode;
import com.diskanalyzer.model.ScanResult;
import javafx.concurrent.Task;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for scanning disk usage.
 */
public class DiskScanner {

    // POSIX interface for efficient cross-platform stat() calls
    private static final POSIX posix = POSIXFactory.getPOSIX();
    private static final int S_BLKSIZE = 512; // Standard block size from sys/stat.h
    private static final boolean USE_POSIX_STAT = !System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * Creates a JavaFX Task for scanning a directory.
     */
    public Task<ScanResult> createScanTask(Path rootPath) {
        return new ScanTask(rootPath);
    }

    /**
     * Internal Task class that exposes updateMessage for progress reporting.
     */
    private class ScanTask extends Task<ScanResult> {
        private final Path rootPath;
        private long lastUpdateTime = System.currentTimeMillis();
        private static final long UPDATE_INTERVAL_MS = 100; // Update every 100ms

        public ScanTask(Path rootPath) {
            this.rootPath = rootPath;
        }

        @Override
        protected ScanResult call() throws Exception {
            updateMessage("Scanning: " + rootPath);
            updateProgress(-1, 1); // Indeterminate progress

            AtomicLong fileCount = new AtomicLong(0);
            AtomicLong dirCount = new AtomicLong(0);

            // Track file identifiers to avoid counting hard links multiple times
            Set<Object> seenFileKeys = new HashSet<>();

            FileNode root = scanDirectory(rootPath, fileCount, dirCount, seenFileKeys, this);

            updateMessage("Calculating sizes...");
            root.calculateSizes();

            return new ScanResult(root, rootPath.toString(),
                    fileCount.get(), dirCount.get());
        }

        void updateScanProgress(long fileCount, long dirCount) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime > UPDATE_INTERVAL_MS) {
                updateMessage(String.format("Scanning: %d files, %d directories", fileCount, dirCount));
                lastUpdateTime = currentTime;
            }
        }
    }

    /**
     * Recursively scans a directory and builds a FileNode tree.
     * Uses file keys to avoid counting hard links multiple times.
     */
    private FileNode scanDirectory(Path path, AtomicLong fileCount, AtomicLong dirCount,
                                   Set<Object> seenFileKeys, Task<?> task) {
        File file = path.toFile();
        FileNode node = new FileNode(file);

        try {
            // Read file attributes to get the file key (inode identifier)
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            Object fileKey = attrs.fileKey();

            if (!file.isDirectory()) {
                fileCount.incrementAndGet();

                // For regular files, check if we've seen this file key before (hard link)
                if (fileKey != null && !seenFileKeys.add(fileKey)) {
                    // This is a hard link to a file we've already seen
                    // Set size to 0 to avoid double-counting
                    node.setSize(0);
                } else {
                    // Use actual disk allocation (blocks) instead of logical size
                    // This handles sparse files, virtual disks, etc. correctly
                    long actualSize = getActualDiskUsage(path);
                    node.setSize(actualSize);
                }
                return node;
            }

            dirCount.incrementAndGet();

            // Update progress periodically
            if (task instanceof ScanTask) {
                ((ScanTask) task).updateScanProgress(fileCount.get(), dirCount.get());
            }

            File[] files = file.listFiles();

            if (files != null) {
                for (File child : files) {
                    try {
                        Path childPath = child.toPath();

                        // Skip symbolic links to avoid infinite loops
                        if (Files.isSymbolicLink(childPath)) {
                            continue;
                        }

                        FileNode childNode = scanDirectory(childPath, fileCount, dirCount, seenFileKeys, task);
                        node.addChild(childNode);
                    } catch (SecurityException e) {
                        // Skip files/directories we don't have permission to access
                        System.err.println("Skipping: " + child + " - " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            // If we can't read attributes, fall back to basic handling
            System.err.println("Error reading attributes for: " + path + " - " + e.getMessage());
            if (!file.isDirectory()) {
                fileCount.incrementAndGet();
                node.setSize(getActualDiskUsage(path));
            } else {
                dirCount.incrementAndGet();
            }
        }

        return node;
    }

    /**
     * Deletes a file or directory.
     */
    public boolean delete(FileNode node) {
        try {
            File file = node.getPath().toFile();
            return deleteRecursive(file);
        } catch (Exception e) {
            System.err.println("Error deleting: " + node.getPath() + " - " + e.getMessage());
            return false;
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    /**
     * Moves a file or directory.
     */
    public boolean move(FileNode source, FileNode destination) {
        try {
            Path sourcePath = source.getPath();
            Path destPath = destination.getPath().resolve(source.getName());
            Files.move(sourcePath, destPath);
            return true;
        } catch (IOException e) {
            System.err.println("Error moving: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a new directory.
     */
    public boolean createDirectory(FileNode parent, String name) {
        try {
            Path newPath = parent.getPath().resolve(name);
            Files.createDirectory(newPath);
            return true;
        } catch (IOException e) {
            System.err.println("Error creating directory: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the actual disk allocation for a file (accounting for sparse files).
     * Uses efficient jnr-posix library for cross-platform stat() calls.
     * This correctly handles sparse files and virtual disks.
     */
    public long getActualDiskUsage(Path path) {
        // Try POSIX stat() call for Unix/Linux/macOS
        if (USE_POSIX_STAT) {
            try {
                FileStat stat = posix.stat(path.toString());
                if (stat != null) {
                    // st_blocks is in 512-byte units on Unix/macOS/Linux
                    return stat.blocks() * S_BLKSIZE;
                }
            } catch (Exception e) {
                // Fall through to fallback
            }
        }

        // Windows: try DOS compression size attribute
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Object sizeAttr = Files.getAttribute(path, "dos:compressionSize");
                if (sizeAttr instanceof Long) {
                    return (Long) sizeAttr;
                }
            } catch (Exception e) {
                // Attribute not available
            }
        }

        // Fallback to logical file size if native calls not available
        try {
            return Files.size(path);
        } catch (IOException e) {
            System.err.println("Error getting file size for: " + path + " - " + e.getMessage());
            return 0;
        }
    }
}
