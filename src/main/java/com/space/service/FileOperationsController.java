package com.space.service;

import com.space.model.FileNode;
import javafx.scene.control.*;

import java.io.File;
import java.util.Optional;

/**
 * Controller for file management operations with UI dialogs.
 */
public class FileOperationsController {
    private final DiskScanner scanner;

    public FileOperationsController(DiskScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Shows dialog to create a new folder and creates it if confirmed.
     */
    public Optional<FileNode> createFolderDialog(FileNode parent) {
        if (!parent.isDirectory()) {
            showError("Cannot create folder", "Parent must be a directory");
            return Optional.empty();
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New Folder");
        dialog.setHeaderText("Create subfolder in: " + parent.getName());
        dialog.setContentText("Folder name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String folderName = result.get().trim();

            // Validate folder name
            if (!isValidFileName(folderName)) {
                showError("Invalid Name", "Folder name contains invalid characters");
                return Optional.empty();
            }

            // Check if already exists
            File newFolder = parent.getPath().resolve(folderName).toFile();
            if (newFolder.exists()) {
                showError("Folder Exists", "A folder with this name already exists");
                return Optional.empty();
            }

            // Create folder
            if (scanner.createDirectory(parent, folderName)) {
                FileNode newNode = new FileNode(newFolder);
                parent.addChild(newNode);
                parent.calculateSizes(); // Recalculate sizes
                showInfo("Success", "Folder created successfully");
                return Optional.of(newNode);
            } else {
                showError("Failed", "Could not create folder");
            }
        }

        return Optional.empty();
    }

    /**
     * Shows confirmation dialog and deletes the node if confirmed.
     */
    public boolean deleteWithConfirmation(FileNode node) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete " + (node.isDirectory() ? "folder" : "file") + "?");

        String content = "Are you sure you want to delete:\n" + node.getPath() + "\n\n";
        if (node.isDirectory()) {
            content += "This will delete the folder and all its contents (" +
                    FileNode.formatSize(node.getSize()) + ")";
        } else {
            content += "Size: " + FileNode.formatSize(node.getSize());
        }

        alert.setContentText(content);

        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(deleteButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == deleteButton) {
            if (scanner.delete(node)) {
                // Remove from parent and recalculate sizes
                FileNode parent = node.getParent();
                if (parent != null) {
                    parent.removeChild(node);
                    parent.calculateSizes();
                }
                showInfo("Success", "Deleted successfully");
                return true;
            } else {
                showError("Failed", "Could not delete " + node.getName());
            }
        }

        return false;
    }

    /**
     * Shows dialog to select destination and moves the node.
     */
    public boolean moveWithDialog(FileNode source, FileNode destination) {
        if (!destination.isDirectory()) {
            showError("Invalid Destination", "Destination must be a directory");
            return false;
        }

        // Check if trying to move into itself or its subdirectory
        if (isAncestor(source, destination)) {
            showError("Invalid Move", "Cannot move a folder into itself or its subdirectory");
            return false;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Move");
        alert.setHeaderText("Move " + source.getName() + "?");
        alert.setContentText("From: " + source.getPath() + "\n" +
                "To: " + destination.getPath().resolve(source.getName()));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (scanner.move(source, destination)) {
                // Update tree structure
                FileNode sourceParent = source.getParent();
                if (sourceParent != null) {
                    sourceParent.removeChild(source);
                    sourceParent.calculateSizes();
                }
                destination.addChild(source);
                destination.calculateSizes();

                showInfo("Success", "Moved successfully");
                return true;
            } else {
                showError("Failed", "Could not move " + source.getName());
            }
        }

        return false;
    }

    private boolean isAncestor(FileNode potentialAncestor, FileNode node) {
        FileNode current = node;
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isValidFileName(String name) {
        // Check for invalid characters (cross-platform)
        String invalidChars = "[<>:\"/\\\\|?*]";
        if (name.matches(".*" + invalidChars + ".*")) {
            return false;
        }

        // Check for reserved names on Windows
        String[] reserved = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
                "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3",
                "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        String upperName = name.toUpperCase();
        for (String res : reserved) {
            if (upperName.equals(res) || upperName.startsWith(res + ".")) {
                return false;
            }
        }

        return true;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
