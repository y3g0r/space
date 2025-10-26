# GitHub Actions Workflows

## Release Workflow

The `release.yml` workflow automates building and releasing the Space application for multiple platforms.

### Quick Start

1. **First time setup**: Just commit the workflow file (it's already done!)

2. **Create your first release**:
   ```bash
   git add .
   git commit -m "Ready for first release"
   git tag v1.0.0
   git push origin main
   git push origin v1.0.0
   ```

3. **Check the progress**:
   - Go to GitHub → Actions tab
   - Watch the workflow run
   - When complete, check the Releases page

### What Gets Built

- ✅ **Fat JAR** - Universal Java application
- ✅ **Windows MSI** - Windows installer with bundled JRE
- ✅ **macOS DMG** - macOS disk image with bundled JRE
- ✅ **Linux DEB** - Debian/Ubuntu package
- ✅ **Linux RPM** - Fedora/RHEL/CentOS package

### Triggers

This workflow runs when:
- A tag starting with `v` is pushed (e.g., `v1.0.0`, `v2.1.3`)
- Manually triggered via GitHub Actions UI

### Build Matrix

| Platform | Runner | Output | Size |
|----------|--------|--------|------|
| Windows | windows-latest | .msi | ~100MB |
| macOS | macos-latest | .dmg | ~100MB |
| Linux | ubuntu-latest | .deb, .rpm | ~100MB each |
| Universal | ubuntu-latest | .jar | ~5MB |

### Customization

#### Update Version Numbers

When releasing a new version, update:
1. `pom.xml` - Line 9: `<version>X.X.X</version>`
2. `release.yml` - All `--app-version X.X.X` parameters

#### Add Icons

Place icons in `src/main/resources/icons/`:
- `icon.ico` - Windows (256x256 or multiple sizes)
- `icon.icns` - macOS (iconset)
- `icon.png` - Linux (512x512 PNG)

See `src/main/resources/icons/README.md` for detailed instructions.

#### Modify Build Options

Edit the jpackage commands in `release.yml` to customize:
- Installer type (msi/exe for Windows, dmg/pkg for macOS, deb/rpm for Linux)
- Application metadata (vendor, description, etc.)
- Platform-specific options (shortcuts, menu groups, etc.)

### Performance

Typical build times:
- Fat JAR: ~2 minutes
- Windows: ~5 minutes
- macOS: ~5 minutes
- Linux: ~6 minutes (builds both DEB and RPM)
- Total: ~8-10 minutes (jobs run in parallel)

### Troubleshooting

**Workflow doesn't trigger**
- Ensure tag starts with 'v'
- Check that workflow file is on the main branch
- Verify GitHub Actions is enabled for the repository

**Build fails**
- Check Actions logs for specific errors
- Verify pom.xml is valid
- Ensure all dependencies are available

**Release not created**
- Check that all build jobs completed successfully
- Verify repository permissions allow creating releases
- Ensure GITHUB_TOKEN has write access

### Security

- The workflow uses GITHUB_TOKEN (automatically provided)
- No secrets need to be configured
- Builds run in isolated GitHub-hosted runners
- Each build job is independent and sandboxed

### Cost

- GitHub Actions is free for public repositories
- For private repositories: 2,000 minutes/month free (Ubuntu), 10x multiplier for macOS/Windows
- Typical release uses ~20 minutes of Linux + ~10 minutes of Windows + ~10 minutes of macOS
- That's ~120 equivalent Linux minutes per release

For more details, see `RELEASE.md` in the root directory.
