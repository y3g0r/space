# Quick Start Guide

## Running the Application

### Option 1: Using Maven (Recommended)
```bash
mvn javafx:run
```

### Option 2: Build and Run JAR
```bash
# Build the project
mvn clean package

# Run the application (macOS/Linux)
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -jar target/space-1.0.0.jar

# Or on Windows
java --module-path %PATH_TO_FX% --add-modules javafx.controls,javafx.fxml -jar target/space-1.0.0.jar
```

## Basic Workflow

1. **Launch Application**: Run using `mvn javafx:run`

2. **Scan Directory**:
   - Click "Scan Directory..." button
   - Select a folder to analyze
   - Wait for scan to complete

3. **Navigate Pie Chart**:
   - Double-click segments to drill down
   - Double-click center to go up
   - Hover for details

4. **Use Tree View**:
   - Expand/collapse folders with arrows
   - Double-click folders to sync with pie chart
   - Right-click for file operations

5. **File Operations**:
   - Right-click → "Create Subfolder..." to add folders
   - Right-click → "Delete" to remove items
   - Right-click → "Properties" for details

## Key Features

- **Multi-layer Pie Chart**: Visual representation with 2-3 directory levels
- **Tree/Table View**: Hierarchical file list with size metrics
- **Caching**: Automatic caching of scan results
- **Cross-platform**: Works on Windows, macOS, and Linux

## Project Structure

```
space/
├── src/main/java/com/space/
│   ├── model/              # Data models (FileNode, ScanResult)
│   ├── service/            # Business logic (Scanner, Cache, FileOps)
│   ├── ui/                 # UI components (PieChart, TreeTable)
│   └── SpaceApp.java  # Main application
├── src/main/resources/
│   └── css/style.css       # Styling
├── pom.xml                 # Maven configuration
└── README.md              # Full documentation
```

## Troubleshooting

**Build fails**: Ensure Java 17+ is installed
```bash
java -version
```

**JavaFX errors**: Maven will automatically download platform-specific JavaFX libraries

**Permission errors**: Some system directories may require elevated privileges

## Next Steps

See `README.md` for:
- Detailed feature documentation
- Building for distribution
- Advanced configuration options
- Performance tips
