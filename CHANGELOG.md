# Changelog

## [Unreleased]
### Added
- Logcat Recorder tool window with start/stop controls, configurable log directory, and log file naming by project/branch/timestamp.
- Note metadata now stores Git branch with branch-aware filtering and search scope selection.

### Changed
- Replace the embedded Mock API server with JDK HttpServer to improve IDE compatibility.
- Embed Logcat Recorder in the Mock API tool window with a single toggle button and double-click to reveal logs.
- Improve search robustness with branch/file path filtering in the index and safer query parsing fallbacks.
- Add Lucene environment diagnostics and fallback behavior for incompatible IDE search classes.

## [0.1.3]
### Added
- Initial Quick Note plugin release.
