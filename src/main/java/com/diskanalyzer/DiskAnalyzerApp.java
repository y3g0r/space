package com.diskanalyzer;

import com.diskanalyzer.config.FeatureFlags;
import com.diskanalyzer.model.FileNode;
import com.diskanalyzer.model.ScanResult;
import com.diskanalyzer.service.CacheService;
import com.diskanalyzer.service.DiskScanner;
import com.diskanalyzer.service.FileOperationsController;
import com.diskanalyzer.ui.FileTreeTableView;
import com.diskanalyzer.ui.MultiLayerPieChart;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Optional;

/**
 * Main application class for Disk Analyzer.
 */
public class DiskAnalyzerApp extends Application {
    private DiskScanner scanner;
    private CacheService cacheService;
    private FileOperationsController fileOpsController;

    private MultiLayerPieChart pieChart;
    private FileTreeTableView treeTableView;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Label currentPathLabel;
    private Button scanButton;

    private ScanResult currentScanResult;
    private FileNode currentPieChartNode;

    @Override
    public void start(Stage primaryStage) {
        initializeServices();

        BorderPane root = new BorderPane();
        root.setTop(createToolbar());
        root.setCenter(createMainContent());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, 1200, 800);
        loadStyles(scene);

        primaryStage.setTitle("Disk Analyzer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeServices() {
        scanner = new DiskScanner();
        if (FeatureFlags.ENABLE_CACHING) {
            cacheService = new CacheService();
        }
        fileOpsController = new FileOperationsController(scanner);
    }

    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();

        scanButton = new Button("Scan Directory...");
        scanButton.setOnAction(e -> selectAndScanDirectory());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshCurrentScan());

        // Add Clear Cache button only if caching is enabled
        if (FeatureFlags.ENABLE_CACHING) {
            Button clearCacheButton = new Button("Clear Cache");
            clearCacheButton.setOnAction(e -> {
                cacheService.clearCache();
                updateStatus("Cache cleared");
            });
            toolbar.getItems().addAll(scanButton, refreshButton, clearCacheButton);
        } else {
            toolbar.getItems().addAll(scanButton, refreshButton);
        }

        Separator separator = new Separator(Orientation.VERTICAL);

        currentPathLabel = new Label("No directory selected");
        currentPathLabel.setStyle("-fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button aboutButton = new Button("About");
        aboutButton.setOnAction(e -> showAboutDialog());

        toolbar.getItems().addAll(
                separator, currentPathLabel, spacer, aboutButton
        );

        return toolbar;
    }

    private SplitPane createMainContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);

        // Left side: Pie chart
        VBox pieChartContainer = new VBox(10);
        pieChartContainer.setPadding(new Insets(10));

        Label pieChartLabel = new Label("Visual Disk Usage");
        pieChartLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        pieChart = new MultiLayerPieChart(500, 500);
        pieChart.setOnNavigate(this::navigateToPieChartNode);

        // Breadcrumb navigation
        HBox breadcrumbBox = new HBox(5);
        breadcrumbBox.setAlignment(Pos.CENTER_LEFT);
        Button upButton = new Button("↑ Up");
        upButton.setOnAction(e -> navigateUp());
        breadcrumbBox.getChildren().add(upButton);

        pieChartContainer.getChildren().addAll(pieChartLabel, pieChart, breadcrumbBox);

        // Right side: Tree table view
        VBox treeContainer = new VBox(10);
        treeContainer.setPadding(new Insets(10));

        Label treeLabel = new Label("File System Tree");
        treeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        treeTableView = new FileTreeTableView();
        treeTableView.setOnNodeSelected(this::onTreeNodeSelected);
        treeTableView.setOnNodeDelete(this::deleteNode);
        treeTableView.setOnCreateFolder(this::createFolder);

        VBox.setVgrow(treeTableView, Priority.ALWAYS);
        treeContainer.getChildren().addAll(treeLabel, treeTableView);

        splitPane.getItems().addAll(pieChartContainer, treeContainer);
        splitPane.setDividerPositions(0.45);

        return splitPane;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("Ready");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, progressBar);
        return statusBar;
    }

    private void selectAndScanDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory to Scan");

        // Set initial directory to user home
        File userHome = new File(System.getProperty("user.home"));
        if (userHome.exists()) {
            chooser.setInitialDirectory(userHome);
        }

        File selectedDirectory = chooser.showDialog(scanButton.getScene().getWindow());
        if (selectedDirectory != null) {
            scanDirectory(selectedDirectory.toPath().toString());
        }
    }

    private void scanDirectory(String path) {
        // If caching is disabled, skip cache check and perform scan directly
        if (!FeatureFlags.ENABLE_CACHING) {
            performScan(path);
            return;
        }

        // Disable scan button immediately
        scanButton.setDisable(true);
        updateStatus("Checking cache...");

        // Check cache in background to avoid UI freeze
        Task<Optional<ScanResult>> cacheCheckTask = new Task<>() {
            @Override
            protected Optional<ScanResult> call() {
                return cacheService.loadScanResult(path);
            }
        };

        cacheCheckTask.setOnSucceeded(e -> {
            scanButton.setDisable(false);
            Optional<ScanResult> cachedResult = cacheCheckTask.getValue();

            if (cachedResult.isPresent()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Cached Data Available");
                alert.setHeaderText("Found cached scan data");
                alert.setContentText("Use cached data or perform new scan?");

                ButtonType useCacheButton = new ButtonType("Use Cache");
                ButtonType newScanButton = new ButtonType("New Scan");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(useCacheButton, newScanButton, cancelButton);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == useCacheButton) {
                        loadScanResult(cachedResult.get());
                        return;
                    } else if (result.get() == cancelButton) {
                        return;
                    }
                }
            }

            // Perform new scan
            performScan(path);
        });

        cacheCheckTask.setOnFailed(e -> {
            scanButton.setDisable(false);
            // If cache check fails, just proceed with scan
            performScan(path);
        });

        new Thread(cacheCheckTask).start();
    }

    private void performScan(String path) {
        Task<ScanResult> scanTask = scanner.createScanTask(new File(path).toPath());

        // Bind status label and progress bar to task properties for live updates
        statusLabel.textProperty().bind(scanTask.messageProperty());
        progressBar.progressProperty().bind(scanTask.progressProperty());

        scanTask.setOnRunning(e -> {
            progressBar.setVisible(true);
            scanButton.setDisable(true);
        });

        scanTask.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            ScanResult result = scanTask.getValue();
            loadScanResult(result);
            // Save to cache only if caching is enabled
            if (FeatureFlags.ENABLE_CACHING) {
                cacheService.saveScanResult(result);
            }
            progressBar.setVisible(false);
            scanButton.setDisable(false);
            updateStatus("Scan completed: " + result.getTotalFiles() + " files, " +
                    result.getTotalDirectories() + " directories, " +
                    FileNode.formatSize(result.getTotalSize()));
        });

        scanTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            scanButton.setDisable(false);
            updateStatus("Scan failed");
            showError("Scan Failed", "Could not complete directory scan: " +
                    scanTask.getException().getMessage());
        });

        new Thread(scanTask).start();
    }

    private void loadScanResult(ScanResult result) {
        this.currentScanResult = result;
        this.currentPieChartNode = result.getRoot();

        currentPathLabel.setText(result.getScanPath());
        treeTableView.setRootNode(result.getRoot());
        pieChart.setCurrentNode(result.getRoot());
    }

    private void refreshCurrentScan() {
        if (currentScanResult != null) {
            scanDirectory(currentScanResult.getScanPath());
        }
    }

    private void navigateToPieChartNode(FileNode node) {
        if (node.isDirectory()) {
            currentPieChartNode = node;
            pieChart.setCurrentNode(node);
        }
    }

    private void navigateUp() {
        if (currentPieChartNode != null && currentPieChartNode.getParent() != null) {
            navigateToPieChartNode(currentPieChartNode.getParent());
        }
    }

    private void onTreeNodeSelected(FileNode node) {
        // Sync pie chart with tree selection
        if (node.isDirectory()) {
            navigateToPieChartNode(node);
        }
    }

    private void deleteNode(FileNode node) {
        if (fileOpsController.deleteWithConfirmation(node)) {
            // Refresh views
            if (currentScanResult != null) {
                treeTableView.refreshNode(node.getParent() != null ? node.getParent() : currentScanResult.getRoot());
                pieChart.setCurrentNode(currentPieChartNode);
            }
        }
    }

    private void createFolder(FileNode parent) {
        Optional<FileNode> newFolder = fileOpsController.createFolderDialog(parent);
        if (newFolder.isPresent()) {
            // Refresh views
            treeTableView.refreshNode(parent);
            pieChart.setCurrentNode(currentPieChartNode);
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Disk Analyzer");
        alert.setHeaderText("Disk Analyzer v1.0");

        StringBuilder features = new StringBuilder();
        features.append("A modern cross-platform disk usage analyzer\n\n");
        features.append("Features:\n");
        features.append("• Interactive multi-layer pie chart visualization\n");
        features.append("• Hierarchical tree view with size information\n");
        features.append("• File management (create, delete folders)\n");
        if (FeatureFlags.ENABLE_CACHING) {
            features.append("• Scan result caching\n");
        }
        features.append("• Cross-platform support (Windows, macOS, Linux)\n\n");
        features.append("Double-click on pie chart segments to navigate.\n");
        features.append("Double-click on center to go up.");

        alert.setContentText(features.toString());
        alert.showAndWait();
    }

    private void loadStyles(Scene scene) {
        // Load CSS if available
        String css = getClass().getResource("/css/style.css") != null ?
                getClass().getResource("/css/style.css").toExternalForm() : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
