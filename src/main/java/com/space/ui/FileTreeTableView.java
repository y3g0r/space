package com.space.ui;

import com.space.model.FileNode;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * TreeTableView for displaying file hierarchy with size information.
 */
public class FileTreeTableView extends TreeTableView<FileNode> {
    private Consumer<FileNode> onNodeSelected;
    private Consumer<FileNode> onNodeDelete;
    private Consumer<FileNode> onCreateFolder;

    public FileTreeTableView() {
        setupColumns();
        setupContextMenu();
        setupEventHandlers();
        setShowRoot(true);
    }

    private void setupColumns() {
        // Name column with folder icon
        TreeTableColumn<FileNode, String> nameColumn = new TreeTableColumn<>("Name");
        nameColumn.setPrefWidth(300);
        nameColumn.setCellValueFactory(param ->
                new ReadOnlyStringWrapper(param.getValue().getValue().getName()));
        nameColumn.setCellFactory(column -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                getStyleClass().removeAll("directory-cell", "file-cell");
                
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    
                    TreeTableRow<FileNode> row = getTreeTableRow();
                    if (row != null) {
                        TreeItem<FileNode> treeItem = row.getTreeItem();
                        if (treeItem != null && treeItem.getValue() != null) {
                            FileNode node = treeItem.getValue();
                            if (node.isDirectory()) {
                                getStyleClass().add("directory-cell");
                            } else {
                                getStyleClass().add("file-cell");
                            }
                        }
                    }
                }
            }
        });

        // Percentage column
        TreeTableColumn<FileNode, String> percentageColumn = new TreeTableColumn<>("% of Parent");
        percentageColumn.setPrefWidth(100);
        percentageColumn.setCellValueFactory(param -> {
            FileNode node = param.getValue().getValue();
            double percentage = node.getPercentageOfParent();
            return new ReadOnlyStringWrapper(String.format("%.1f%%", percentage));
        });
        percentageColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Total size column
        TreeTableColumn<FileNode, String> totalSizeColumn = new TreeTableColumn<>("Total Size");
        totalSizeColumn.setPrefWidth(120);
        totalSizeColumn.setCellValueFactory(param -> {
            FileNode node = param.getValue().getValue();
            return new ReadOnlyStringWrapper(FileNode.formatSize(node.getSize()));
        });
        totalSizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Own size column
        // For files: same as Total Size
        // For directories: size of files directly in them (not including subdirectories)
        TreeTableColumn<FileNode, String> ownSizeColumn = new TreeTableColumn<>("Own Size");
        ownSizeColumn.setPrefWidth(120);
        ownSizeColumn.setCellValueFactory(param -> {
            FileNode node = param.getValue().getValue();
            return new ReadOnlyStringWrapper(FileNode.formatSize(node.getOwnSize()));
        });
        ownSizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        getColumns().addAll(nameColumn, percentageColumn, totalSizeColumn, ownSizeColumn);
    }

    private void setupContextMenu() {
        setContextMenu(null); // Will be set dynamically on right-click
        setOnContextMenuRequested(event -> {
            TreeItem<FileNode> selectedItem = getSelectionModel().getSelectedItem();
            if (selectedItem == null) return;

            FileNode node = selectedItem.getValue();
            ContextMenu contextMenu = new ContextMenu();

            if (node.isDirectory()) {
                MenuItem createFolderItem = new MenuItem("Create Subfolder...");
                createFolderItem.setOnAction(e -> {
                    if (onCreateFolder != null) {
                        onCreateFolder.accept(node);
                    }
                });
                contextMenu.getItems().add(createFolderItem);
                contextMenu.getItems().add(new SeparatorMenuItem());
            }

            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> {
                if (onNodeDelete != null) {
                    onNodeDelete.accept(node);
                }
            });

            MenuItem propertiesItem = new MenuItem("Properties");
            propertiesItem.setOnAction(e -> showPropertiesDialog(node));

            contextMenu.getItems().addAll(deleteItem, propertiesItem);
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
        });
    }

    private void setupEventHandlers() {
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<FileNode> selectedItem = getSelectionModel().getSelectedItem();
                if (selectedItem != null && onNodeSelected != null) {
                    FileNode node = selectedItem.getValue();
                    if (node.isDirectory()) {
                        onNodeSelected.accept(node);
                    }
                }
            }
        });

        // Single click selection
        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onNodeSelected != null) {
                onNodeSelected.accept(newVal.getValue());
            }
        });
    }

    public void setRootNode(FileNode node) {
        TreeItem<FileNode> rootItem = buildTreeItem(node);
        rootItem.setExpanded(true);
        setRoot(rootItem);
    }

    private TreeItem<FileNode> buildTreeItem(FileNode node) {
        TreeItem<FileNode> item = new TreeItem<>(node);

        if (node.isDirectory() && !node.getChildren().isEmpty()) {
            // Add a placeholder to make the node expandable
            item.getChildren().add(new TreeItem<>());

            // Lazy load children when expanded
            item.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                if (isNowExpanded) {
                    // Check if this is the first expansion (placeholder exists)
                    if (item.getChildren().size() == 1 && item.getChildren().get(0).getValue() == null) {
                        item.getChildren().clear();

                        // Sort children by size (largest first) and build their TreeItems
                        node.getChildren().stream()
                                .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
                                .forEach(child -> item.getChildren().add(buildTreeItem(child)));
                    }
                }
            });
        }

        return item;
    }

    public void refreshNode(FileNode node) {
        TreeItem<FileNode> item = findTreeItem(getRoot(), node);
        if (item != null) {
            boolean wasExpanded = item.isExpanded();
            item.getChildren().clear();

            if (node.isDirectory() && !node.getChildren().isEmpty()) {
                if (wasExpanded) {
                    // If it was expanded, rebuild the children immediately
                    node.getChildren().stream()
                            .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
                            .forEach(child -> item.getChildren().add(buildTreeItem(child)));
                } else {
                    // Otherwise, just add a placeholder for lazy loading
                    item.getChildren().add(new TreeItem<>());
                }
            }
            refresh();
        }
    }

    private TreeItem<FileNode> findTreeItem(TreeItem<FileNode> root, FileNode node) {
        if (root == null) return null;
        if (root.getValue() == node) return root;

        for (TreeItem<FileNode> child : root.getChildren()) {
            TreeItem<FileNode> result = findTreeItem(child, node);
            if (result != null) return result;
        }

        return null;
    }

    public void setOnNodeSelected(Consumer<FileNode> onNodeSelected) {
        this.onNodeSelected = onNodeSelected;
    }

    public void setOnNodeDelete(Consumer<FileNode> onNodeDelete) {
        this.onNodeDelete = onNodeDelete;
    }

    public void setOnCreateFolder(Consumer<FileNode> onCreateFolder) {
        this.onCreateFolder = onCreateFolder;
    }

    private void showPropertiesDialog(FileNode node) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Properties");
        alert.setHeaderText(node.getName());

        StringBuilder content = new StringBuilder();
        content.append("Path: ").append(node.getPath()).append("\n");
        content.append("Type: ").append(node.isDirectory() ? "Directory" : "File").append("\n");
        content.append("Total Size: ").append(FileNode.formatSize(node.getSize())).append("\n");

        if (node.isDirectory()) {
            content.append("Own Size: ").append(FileNode.formatSize(node.getOwnSize())).append("\n");
            content.append("Children: ").append(node.getChildren().size()).append("\n");
        }

        if (node.getParent() != null) {
            content.append("Percentage of Parent: ")
                    .append(String.format("%.2f%%", node.getPercentageOfParent())).append("\n");
        }

        alert.setContentText(content.toString());
        alert.showAndWait();
    }
}
