# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.1] - 2026-10-17

### Added

- Added a bilingual Patchouli field guide with one chapter for each survival machine and utility device, crafting pages, and links to related chapters.
- Granted the guide once per player when first playing in survival, with a crafting recipe for replacement copies.
- Required Patchouli for in-game documentation.

### Fixed

- Set the minimum Forge version to 47.3.19, which provides the resource-location factory methods used during startup.
- Restricted the declared Minecraft compatibility range to the supported 1.20.1 release.

### Documentation

- Revised the English and Korean guides, README files, JEI descriptions, and machine status messages for consistent terminology, grammar, and machine behavior.
- Expanded guidance on cleanrooms, circuit editing, material recovery, logistics, and data-pack process validation.

## [0.3.0] - 2026-10-16

### Changed

- Advanced the release version from 0.2.9 to 0.3.0.

## [0.2.9] - 2026-10-16

### Fixed

- Preserved every pending industrial output when multiple byproducts share output slots.
- Kept completed batch input consumption independent of later data-pack recipe changes or removal.
- Ignored malformed saved consumption plans instead of discarding reusable inputs.
- Reset unfinished work and Siemens recovery only when a reloaded recipe's definition changes.
- Refreshed client process views and new menu layouts within the recipe synchronization tick.
- Refreshed built-in tag ingredients and saved recipe definitions after registry tag reloads.
- Kept industrial menus usable when data-pack overrides leave their machine with no process definitions.
- Prevented shift-click transfers from bypassing locked machine inputs or inserting into duplicator outputs.
- Persisted merged and partial shift-click transfers, including idle generator fuel, and reset progress when process inputs change.
- Closed machine menus when players change dimensions, the owner becomes unavailable, or industrial input slots change after a recipe reload.
- Sent logistics search page selections without truncating them into vanilla's one-byte buttons.
- Rejected spectator wafer mutation packets and prevented vanilla menu opening without required machine data.
- Rejected invalid wafer edit indices before allocating circuit history snapshots.
- Bounded restored wafer redstone outputs and ignored malformed circuit preview arrays.
- Bounded malformed preview names without changing saved wafer names.
- Isolated wafer save and update snapshots from live signal arrays.
- Restored pending duplicator results as one item per substrate and energy charge.
- Migrated legacy wafer traces before counting components, mirroring designs, or checking blank substrates.
- Normalized legacy and simulated wafer metadata when comparing duplication materials.
- Mirrored implicit default wafer pin modes, including nested circuits, and matched restored default pins as equivalent components.
- Stopped cached power and logistics transfers when a cable or pipe route loses chunk availability, and restored routes after loading.
- Discovered receiver capabilities enabled after cable placement when refreshing the power network.
- Respected directional inventory capability denials in normal logistics while preserving explicit forced access and legacy container compatibility.
- Preserved cable connections while neighboring chunks are unavailable.
- Kept derived cable and pipe shape refreshes from loading neighboring chunks.
- Preserved unloaded cleanroom diagnostics and excluded unavailable or removed conditioners from room effects and shared groups.
- Ignored non-positive external energy transfer requests across machine capabilities and direct wafer charging.
- Rejected fractional and overflowing JSON process integers instead of silently changing their values.
- Rejected impossible shapeless inputs before allocating a matching graph and reduced matching cost for large valid recipes.
- Honored data-pack recycler overrides in comparator signals.
- Displayed shapeless fabrication inputs in JEI and kept large ingredient lists accessible in scroll grids.
- Prevented overlapping wafer screen titles, inverter labels, and JEI shapeless markers, and used locale-independent wafer tooltip keys.

### Changed

- Omitted empty wafer runtime fields and sent compact circuit previews while preserving complete server saves.
- Compressed large wafer item synchronization tags losslessly, with bounded decoding and deterministic encoding for strict NBT ingredients.
- Updated the network protocol to version 2; clients and servers need matching Siliconic versions.
- Checked the size of newly embedded wafer circuits before consuming materials, including space for later runtime state.
- Reserved metadata added by future wafer mirroring within the embedding size limit.
- Reduced nested feedback simulation copies and allocation while preserving circuit state and rollback behavior.
- Reused inverter transformations for unchanged inputs and avoided recipe matching for unrelated menu fields.
- Reused component comparison snapshots within duplicator material checks and requirement aggregation.
- Indexed cleanroom claims by nearby sections to bound machine membership and cleanliness queries.

### Documentation

- Explained wafer embedding complexity limits in both gameplay guides.

### Known limitations

- A menu containing 164 nested wafers with highly varied designs can still exceed vanilla's 8 MiB packet limit after item compression.

## [0.2.8] - 2026-10-04

### Fixed

- Preserved data-pack fabrication byproducts and paused processing when their output space is unavailable.
- Allowed fabrication results to fill available space across multiple output stacks.
- Preserved all pending fabrication outputs across reloads while retaining support for older single-result saves.
- Allowed fabrication outputs without contaminated variants to be produced at any cleanroom cleanliness.

## [0.2.7] - 2026-09-04

### Fixed

- Returned detached power-cable items when one of two supporting blocks is removed, while preserving the remaining connection.
- Closed stale logistics menus when their connected endpoint list changes and rejected controls on invalid menus.
- Locked wafer duplicator inputs while a completed result waits for output space, preserving its contamination outcome.
- Removed control and formatting characters from completed wafer names without splitting Unicode characters.
- Refreshed machine and reprocessing recipe caches when data packs replace the recipe manager during a tick.
- Removed empty logistics endpoint settings after options are cleared, preventing unused entries from accumulating.

### Changed

- Upgraded GitHub Actions to Node.js 24-based releases.
- Removed the Forge GameTest server run from CI while keeping it available for local validation.

### Documentation

- Added a Korean counterpart for the data-pack machine-process reference.
- Added language navigation for the machine-process documentation and aligned Korean gameplay wording with the English guide.

## [0.2.6] - 2026-09-03

### Fixed

- Persisted external energy extraction from coal generators to prevent energy restoration after a chunk reload.
- Invalidated logistics endpoint caches immediately when connected pipes or inventories change.
- Clamped energy-buffer receiver demand to the configured per-connection transfer limit.

## [0.2.5] - 2026-09-03

### Fixed

- Preserved completed fabrication results by locking process inputs while output space is unavailable.
- Prevented power-cable connection updates from loading neighboring chunks.
- Prevented durability damage from overflowing for large data-pack process values.
- Bounded nested wafer simulation work per signal update to protect server tick time.
- Preserved power-cable attachments while an adjacent supporting chunk is temporarily unavailable.
- Exposed every machine input slot needed by shapeless data-pack processes.
- Preserved data-pack process progress when block entities reload before their level is attached.
- Restored saved cleanroom occupancy immediately when conditioner chunks reload.
- Prevented forced logistics from modifying locked industrial process inputs.
- Bounded nested wafer transformations to valid tier relationships and canonical grid cells.
- Restricted wafer mutation packets to the client-to-server direction.
- Rejected negative logistics endpoint counts while opening the controller menu.

## [0.2.4] - 2026-09-02

### Fixed

- Preserved large data-pack process durations and energy costs across menu synchronization.
- Prevented overflow in process duration, phase, and progress-bar calculations.
- Saturated phase-specific energy adjustments instead of wrapping at the integer limit.
- Avoided loading chunks while resolving linked cleanroom conditioners.
- Ignored restored cleanroom scans without rebuilt interior positions when selecting a shared-state updater.

### Documentation

- Refocused the bilingual READMEs on project, installation, development, contribution, and license information.
- Moved detailed in-game progression and machine behavior into separate English and Korean gameplay guides.

## [0.2.3] - 2026-09-02

### Fixed

- Validated data-pack machine input slots and machine or reprocessing output stacks during recipe loading.
- Correctly matched and consumed overlapping shapeless ingredients without counting an item more than once.
- Treated damage-input counts as durability damage instead of required item counts.
- Displayed shaped fabrication recipes in their actual JEI grid slots.
- Checked every connected energy face while deduplicating shared receiver capabilities.
- Made recipe type parameters explicit for stricter IDE Java analysis.
- Prevented total-energy calculations from overflowing 32-bit integers.

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
