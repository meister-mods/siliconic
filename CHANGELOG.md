# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## [0.2.2] - 2026-09-02

### Changed

- Limited the GitHub Actions build workflow to pushes targeting the `master` branch.

## [0.2.1] - 2026-09-02

### Fixed

- Invalidated wafer undo history after material-changing or external edits to prevent component duplication.
- Matched configurable generator extraction limits to per-connection transfer limits.
- Prevented integer overflow while summing demand across large power networks.
- Restored release metadata and continuous-integration files that were missing from the working tree.
- Added Forge GameTest execution to continuous integration.

### Documentation

- Standardized all project documentation except `README.kr.md` in English.
- Expanded the data-pack process format and validation notes.

## [0.2.0] - 2026-09-02

### Added

- An energy buffer with storage, redistribution, interaction status, and comparator output.
- Logistics port allow/deny filters, priorities from `-2` through `+2`, and device or coordinate search.
- Cleanroom scan failure coordinates.
- Data-pack recipe types and JSON definitions for machine and reprocessing processes.
- Automated GameTests for power distribution and wafer circuit rules.
- A GitHub Actions build that uploads the runnable mod JAR.

### Changed

- Added a shared power-network topology cache and fair distribution for generators and buffers.
- Staggered cleanroom and logistics scans and moved major balance values into the common configuration.
- Added wafer drag editing, resource-safe undo and redo, and design-format migration.
- Standardized displayed energy units as SE.

### Fixed

- Avoided chunk-loading lookups while scanning connected networks.
- Preserved logistics filter settings and migrated legacy endpoint configuration.
