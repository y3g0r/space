# Release Process Guide

This document explains how to create releases for the Space application using GitHub Actions.

## Overview

The release workflow automatically creates pre-packaged installers for multiple platforms:

- **Fat JAR**: Universal JAR file (requires Java 17+ to be installed)
- **Windows**: MSI installer with bundled JRE
- **macOS**: DMG disk image with bundled JRE
- **Linux**: DEB and RPM packages with bundled JRE

## Prerequisites

1. Your repository must be hosted on GitHub
2. You need write access to create releases
3. (Optional) Application icons in `src/main/resources/icons/` for professional-looking installers

## Creating a Release

### Method 1: Using Git Tags (Recommended)

1. **Update version in pom.xml** (if needed):
   ```bash
   # Edit pom.xml and update the version number
   vim pom.xml
   ```

2. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Prepare release v1.0.0"
   ```

3. **Create and push a tag**:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

4. **Wait for the workflow to complete**:
   - Go to your GitHub repository
   - Click on "Actions" tab
   - Watch the "Build and Release" workflow run
   - Once complete, a new release will appear under "Releases"

### Method 2: Manual Trigger

1. Go to your GitHub repository
2. Click on "Actions" tab
3. Select "Build and Release" workflow
4. Click "Run workflow" button
5. Select the branch and click "Run workflow"

## Workflow Details

### Build Jobs

The workflow runs four parallel jobs:

#### 1. Fat JAR Build (Ubuntu)
- Builds a standalone JAR file with all dependencies
- Output: `space-fat.jar`
- Users need Java 17+ installed to run this

#### 2. Windows Build
- Creates MSI installer
- Bundles Java runtime (users don't need Java installed)
- Adds Start Menu shortcuts
- Output: `Space-1.0.0.msi`

#### 3. macOS Build (Intel)
- Creates DMG disk image for Intel Macs
- Bundles Java runtime
- Creates .app bundle
- Output: `Space-1.0.0-intel.dmg`

#### 4. macOS Build (Apple Silicon)
- Creates DMG disk image for M1/M2/M3 Macs
- Bundles Java runtime
- Creates .app bundle
- Output: `Space-1.0.0-arm64.dmg`

#### 5. Linux Build
- Creates both DEB and RPM packages
- Bundles Java runtime
- Adds desktop launcher
- Output: `space_1.0.0-1_amd64.deb` and `space-1.0.0-1.x86_64.rpm`

### Release Creation

After all builds complete successfully:
- Creates a GitHub Release with the tag name
- Uploads all installers as release assets
- Auto-generates release notes from commits

## Adding Application Icons

To make your installers look professional, add icons:

### 1. Create or find an icon
- Size: 512x512 or 1024x1024 pixels
- Format: PNG with transparent background
- Design should be simple and recognizable

### 2. Convert to platform-specific formats

#### Windows (.ico):
```bash
# Using ImageMagick
magick convert icon.png -define icon:auto-resize=256,128,96,64,48,32,16 icon.ico
```

#### macOS (.icns):
```bash
mkdir icon.iconset
sips -z 16 16     icon.png --out icon.iconset/icon_16x16.png
sips -z 32 32     icon.png --out icon.iconset/icon_16x16@2x.png
sips -z 32 32     icon.png --out icon.iconset/icon_32x32.png
sips -z 64 64     icon.png --out icon.iconset/icon_32x32@2x.png
sips -z 128 128   icon.png --out icon.iconset/icon_128x128.png
sips -z 256 256   icon.png --out icon.iconset/icon_128x128@2x.png
sips -z 256 256   icon.png --out icon.iconset/icon_256x256.png
sips -z 512 512   icon.png --out icon.iconset/icon_256x256@2x.png
sips -z 512 512   icon.png --out icon.iconset/icon_512x512.png
sips -z 1024 1024 icon.png --out icon.iconset/icon_512x512@2x.png
iconutil -c icns icon.iconset
```

#### Linux (.png):
```bash
# Just use the original PNG (512x512 or larger recommended)
cp icon.png src/main/resources/icons/icon.png
```

### 3. Place icons in the correct location
```
src/main/resources/icons/
├── icon.ico    # Windows
├── icon.icns   # macOS
└── icon.png    # Linux
```

### 4. Commit and create a new release
```bash
git add src/main/resources/icons/
git commit -m "Add application icons"
git tag v1.0.1
git push origin v1.0.1
```

## Troubleshooting

### Build fails on a specific platform

- Check the Actions log for that platform's job
- Common issues:
  - Missing dependencies in pom.xml
  - Incorrect main class name
  - Version number mismatch

### Release not created

- Ensure the tag starts with 'v' (e.g., v1.0.0)
- Check that you have write permissions
- Verify all build jobs completed successfully

### Icons not showing in installer

- Verify icon files are in correct format
- Check file paths are correct
- Ensure icons were committed before creating the tag

## Version Management

To update the version for a new release:

1. **Update pom.xml**:
   ```xml
   <version>1.0.1</version>
   ```

2. **Update the workflow** (`.github/workflows/release.yml`):
   - Search for version numbers (e.g., "1.0.0")
   - Update to match pom.xml version
   - This affects the `--app-version` parameter in jpackage commands

3. **Commit changes**:
   ```bash
   git add pom.xml .github/workflows/release.yml
   git commit -m "Bump version to 1.0.1"
   ```

4. **Create and push tag**:
   ```bash
   git tag v1.0.1
   git push origin main
   git push origin v1.0.1
   ```

## Advanced Configuration

### Customizing the Windows Installer

Edit the Windows build job in `.github/workflows/release.yml`:

```yaml
jpackage \
  --type msi \
  --win-menu \                    # Add to Start Menu
  --win-dir-chooser \             # Let user choose install directory
  --win-shortcut \                # Create desktop shortcut
  --win-menu-group "Utilities" \  # Start Menu folder
  --win-per-user-install \        # Per-user vs system-wide
  ...
```

### Customizing the macOS Package

```yaml
jpackage \
  --type dmg \                    # or 'pkg' for package installer
  --mac-package-name "Space" \
  --mac-package-identifier "com.space.app" \
  ...
```

### Customizing Linux Packages

```yaml
jpackage \
  --type deb \                    # or 'rpm'
  --linux-shortcut \              # Create desktop launcher
  --linux-menu-group "Utilities" \
  --linux-app-category "Utility" \
  --linux-deb-maintainer "your@email.com" \
  ...
```

## Distribution

After a successful release:

1. **Download installers** from the GitHub Releases page
2. **Test each installer** on its target platform
3. **Share the release link** with users:
   ```
   https://github.com/yourusername/yourrepo/releases/latest
   ```

## User Installation Instructions

### Windows
1. Download `Space-X.X.X.msi`
2. Double-click to run installer
3. Follow installation wizard
4. Launch from Start Menu

### macOS
1. Download the appropriate DMG for your Mac:
   - **Intel Macs**: `Space-X.X.X-intel.dmg`
   - **Apple Silicon (M1/M2/M3)**: `Space-X.X.X-arm64.dmg`
   - Not sure? Go to  → About This Mac → check "Chip" or "Processor"
2. Open the DMG file
3. Drag Space to Applications folder
4. Launch from Applications

### Linux (Debian/Ubuntu)
```bash
sudo dpkg -i space_X.X.X_amd64.deb
space
```

### Linux (Fedora/RHEL/CentOS)
```bash
sudo rpm -i space-X.X.X.x86_64.rpm
space
```

### Universal (Any OS with Java 17+)
```bash
java -jar space-fat.jar
```

## Notes

- The bundled JRE increases installer size (~50-100MB per platform)
- Native installers provide better user experience (no Java installation required)
- Fat JAR is smallest but requires users to have Java installed
- All installers are built from the same source code for consistency
