# Modular, seeded architectural layouts

## Why scenes repeated

The previous desktop text path sent the prompt to the mod, but `DesktopBridge` converted it to a
short `placeholder:` filename. `HeuristicImageAnalysisProvider` inspected filename keywords, not pixels:
castle, fortress, temple, tower and kingdom all implied `PALACE_CORE`. Its fallback enabled the entire
fantasy vocabulary; keyword matches also forced islands, palace and bridges. `DeterministicScenePlanner`
then always placed a central floating island and palace surrounded by an island grid. Changing the
description or palette could not change that architecture.

This patch introduces an offline, bounded architectural vocabulary and six ground-based geometries.
It is **not an LLM, pixel analysis, arbitrary natural-language design, or a terrain survey**.

## Integration and compatibility

Text path:

1. Desktop **Describe it** captures the full prompt and layout/style/seed choices.
2. `PlanGenerationModel` validates inputs and resolves an empty seed once for the new input selection.
3. `LinkRequest.PLAN` carries the original prompt and optional `generation` object.
4. `DesktopBridge` calls `ArchitectCommandEngine.createTextPlan` directly, without a placeholder slug.
5. `MultiLayoutScenePlanner` parses, selects and generates a `ScenePlan`.
6. The command engine compiles the geometry against the existing section budget and checks block support
   **before** saving it and changing the loaded-plan selection.
7. Existing preview/build/export operations load that saved geometry. They do not rerun selection.

Image uploads and `/architect image plan` retain their existing metadata-only floating-kingdom defaults.
Text layout controls do not apply to image plans; protocol requests combining image sources and generation
choices are rejected. Image filenames, dimensions and content hashes remain the only analysis inputs.

The public `ScenePlanner.plan(ImageAnalysis, PlanOptions)` contract remains. The two-argument
`PlanOptions(String, int)` and ten-argument `ScenePlan` constructors remain available.
`DeterministicScenePlanner` remains the legacy planner. New structural region types are additive;
the old heuristic fallback explicitly lists its original nine types rather than including new enum entries.

### Persistence

Plan JSON retains `formatVersion: 1`, its existing top-level `seed`, and all stored `regions`.
An optional `metadata` object adds layout, generator ID/version, effective style, original prompt,
subject, terrain, features, exclusions, weights and generation parameters. Ordered maps make serialization
reproducible. No second seed field competes with `ScenePlan.seed`.

Old files without metadata still load and compile their stored geometry unchanged. Loading never migrates
their coordinates or regenerates them. Creating another plan with the same ID deliberately replaces that
file. Old application binaries are not guaranteed to understand the new structural enum values:
**update the desktop and mod together**.

## Layouts and real geometry

| Layout | Architectural silhouette |
| --- | --- |
| RADIAL | Open circulation hub with cross-axis spokes connecting surrounding districts; no mandatory palace. |
| LINEAR | Long main street with buildings and side access on both sides; elongated footprint. |
| TERRACED | Multiple broad, solid-supported platforms ascending in steps, linked by walkable stairs. |
| RING | Rectangular perimeter streets and surrounding districts, with an open central court and no cross-road or central building. |
| GRID | Intersecting streets, inhabited blocks and access paths on a continuous foundation, not an island grid. |
| CLIFF | Vertically stacked districts against one narrow rock facade, with supported ledges, outer pillars and alternating switchback stair flights. |
| LEGACY | Original floating kingdom, explicitly selectable; rejected when it conflicts with recognized exclusions. |

The grounded primitives use minimum-corner origins and real bounding boxes: foundations/rock, roads,
buildings/towers, ledges, stairs, walls, explicit air clearance and bounded decorations. Buildings have floors, walls, doors,
windows and roofs. Styles affect actual block materials and supported building/decorative vocabulary.
Legacy circular-region origin and dimension semantics remain unchanged.
LEGACY deliberately retains the original generator palette: its style is a stored hint, not a claim that
the floating palace has been restyled. Its summary explicitly identifies the compatibility geometry/palette.

## Parsing, precedence and limitations

Parsing separates subject, style, terrain, requested features, exclusions and layout. Recognized negative
clauses such as **no floating islands**, **without a central palace**, and **avoid grid** are removed from
positive matching. They are not accidentally interpreted as requests for islands or palaces.

Selection precedence:

1. Explicit Layout control / `PlanOptions.layout`.
2. Recognized layout in the prompt (including `layout: ring`).
3. Seeded weighted sampling over compatible generators.

The Style control overrides inferred style. With no recognized style, neutral is used; a factory subject
can imply industrial. Unknown explicit styles/layouts fail with a clear message. Unsupported vocabulary
and constraints are reported in plan notes/summary, not represented as successfully implemented detail.
Terrain and layout exclusions filter candidates before sampling. An explicit incompatible choice fails
instead of falling back to floating-palace geometry.

The parser is intentionally English and vocabulary-based. It does not understand arbitrary negation,
orientation, asymmetry, real-world landmarks, moving gears, custom materials, narrative semantics or every
architectural style. The desktop protocol limits prompts to 500 characters; the core parser is also bounded.
Generators do not survey or conform to the existing landscape. Building interiors and access corridors
deliberately write air within their bounds; this is local clearance, not terrain-aware excavation.
The ring is orthogonal, not a mathematical circular wall. Sparse block estimates are approximate.
Recognized subjects include village, town, city, castle, fortress, factory, oasis and harbor. Terrain hints
include cliff/cliffside, mountain/hillside, river/riverside, coast/coastal and desert. Feature vocabulary includes
buildings/houses, towers, gardens/parks/courtyards, trees, walls, water and waterfalls. A harbor subject does
not imply a working dock. Palace, floating-island, cloud and bridge requests are reported as unsupported
in grounded layouts rather than forcing the old fantasy skeleton. A requested waterfall is a bounded,
supported fountain cascade, not an inferred waterfall in the existing landscape.

## Styles and weighted selection

Registry order is fixed: **RADIAL, LINEAR, TERRACED, RING, GRID, CLIFF**.
AUTO and LEGACY are not automatic sampling candidates.

| Preset | RADIAL | LINEAR | TERRACED | RING | GRID | CLIFF | Example materials |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| fantasy | 4 | 1 | 1 | 1 | 1 | 2 | Quartz, dark oak, purple concrete |
| medieval | 1 | 2 | 1 | 4 | 1 | 1 | Stone bricks, oak, cobblestone |
| desert | 1 | 1 | 4 | 2 | 1 | 1 | Sandstone, cut/red sandstone |
| industrial | 1 | 3 | 1 | 1 | 5 | 0 | Bricks, iron, gray concrete |
| neutral | 1 | 1 | 1 | 1 | 1 | 1 | Stone bricks, oak, stone slab |

Weights are relative probabilities after compatibility filtering, not percentages. Each weight must be
finite and nonnegative; the remaining compatible total must be positive. Zero-weight and incompatible
generators cannot be selected. Invalid totals and absent candidates produce errors, never a legacy fallback.

Desktop **Settings → Plan generation** exposes a comma-separated override field, persisted in Java
Preferences. For example:

```text
RADIAL=0,LINEAR=3,TERRACED=0,RING=1,GRID=0,CLIFF=0
```

Names are case-insensitive; duplicate keys, unknown keys, negative, NaN and infinite values are rejected.
Unspecified entries retain the selected style's defaults. These overrides affect Auto sampling, not an
explicit layout. Do not assign weights to AUTO or LEGACY.

The optional link payload uses the same choices:

```json
{
  "generation": {
    "layout": "RING",
    "style": "desert",
    "seed": -42,
    "weights": {"LINEAR": 3, "RING": 1}
  }
}
```

Existing request factories and constructors remain compatible; null optional fields are omitted.

## Seeds and reproducibility

Seed is a signed 64-bit integer, including `-9223372036854775808` and `9223372036854775807`.
Blank chooses fresh entropy **once when resolving a new plan**, not on every preview/build.
**New variation** validates the current inputs, chooses another seed and requires a new preview.
An invalid seed or weights entry cannot replace a saved valid plan. Revision guards prevent obsolete
asynchronous preview responses from replacing the current selection.

Core image planning uses the real `ImageAnalysis.seed` unless explicitly overridden. Text planning resolves
one seed and passes it through the same analysis/planning model. Selection and generator randomness derive
separate sub-seeds; no generator random calls advance the selector. Same normalized input, options,
resolved seed and generator version reproduce geometry. Seeds vary building dimensions and supported
layout parameters; they do not promise an entirely different archetype when layout is explicit.
`SceneSeeds` uses fixed v1 domain salts and a SplitMix64-style avalanche function. Selection uses
`mix(seed XOR 0x73656c6563747631)`; each generator uses
`mix(seed XOR 0x67656e6572617631 XOR its fixed layout salt)`. `SplittableRandom` samples weights and
generator details from those independent seeds; enum ordinals and unordered collection hashes are not
used. Metadata parameters retain both derived seeds and the selection source. Sampled dimensions live
in the stored regions, avoiding a second competing geometry-parameter representation.

For lasting reproducibility, retain the JSON geometry, not just a prompt. Algorithm changes require a
generator-version increment; older versions are not automatically replayed by a future generator.

## Desktop usage and reproducible examples

1. Open **Describe it**, enter a prompt, choose Layout and Style or leave Auto.
2. Enter a seed, or leave it blank for a new resolved seed.
3. Preview; inspect chosen layout/style/seed, real bounds and unsupported-request notes.
4. Build uses that saved plan. Pause/cancel/undo and recording operate through their unchanged interfaces.
5. Use **New variation**, then Preview again, to replace the geometry deliberately.

| Prompt | Layout | Style | Seed |
| --- | --- | --- | ---: |
| `A medieval linear town with gardens. No floating islands, no central palace.` | LINEAR | medieval | -42 |
| `A desert trading city around an open court, no palace.` | RING | desert | 2026 |
| `An industrial grid factory district with roads.` | GRID | industrial | 123 |
| `A hillside village with towers and stairs.` | TERRACED | medieval | 7 |
| `A cliffside fortress with ledges, no floating islands.` | CLIFF | medieval | 81 |
| `A fantasy radial town with gardens and towers.` | RADIAL | fantasy | 19 |

Use scale 1 to begin. The existing range remains 1..24; scale changes district counts/extents, and
larger plans can exceed the configured section budget or the available world height at your origin.
The mod additionally preserves `architect.maxImageScale` (default 16) for text and image creation;
the standalone planner accepts up to 24.

## Safety, build and verification

Bounds come from compiled generators, including roofs, stairs and supports—not idealized layout centers.
Air-clearance bounds remain part of build validation but are not drawn as opaque structures in the desktop map.
The existing queue validates translated Y bounds, vanilla hard horizontal limits and section budgets
before starting. A new border hook also checks the active Fabric world border's corners before submission.
Errors explain the bounds/limit conflict; geometry is not silently clipped. A border changing while a job
is already running is still subject to the existing per-block world checks.

No cloud API, LLM, paid dependency or new library is required. Queue scheduling, undo journaling,
pause/cancel, camera framing and recording interfaces are retained.

### Changed-file inventory

All paths below identify this repository checkout. New classes are small, platform-neutral additions;
existing persistence and blueprint streaming formats need no replacement implementation.

**Core layout/geometry and compatibility**

- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/LayoutType.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/LayoutGenerator.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/LayoutGeneratorRegistry.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/GroundedLayoutGenerator.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/MultiLayoutScenePlanner.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/OfflinePromptParser.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/PromptAnalysis.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/StylePreset.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/SceneSeeds.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/PlanOptions.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/image/HeuristicImageAnalysisProvider.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/generator/GroundedStructureGenerator.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/scene/GenerationMetadata.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/scene/ScenePlan.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/scene/SceneRegion.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/scene/RegionType.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/scene/RegionGeneratorFactory.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/scene/SceneCompiler.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/engine/WorldAccess.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/main/java/com/annaschneider/minecraft1/largebuild/engine/BuildQueue.java`

**Desktop and transport**

- `/home/runner/work/minecraft1/minecraft1/architect-desktop/src/main/java/com/annaschneider/minecraft1/desktop/MainWindow.java`
- `/home/runner/work/minecraft1/minecraft1/architect-desktop/src/main/java/com/annaschneider/minecraft1/desktop/PlanGenerationModel.java`
- `/home/runner/work/minecraft1/minecraft1/architect-desktop/src/main/java/com/annaschneider/minecraft1/desktop/DesktopSettings.java`
- `/home/runner/work/minecraft1/minecraft1/architect-desktop/src/main/java/com/annaschneider/minecraft1/desktop/SettingsDialog.java`
- `/home/runner/work/minecraft1/minecraft1/architect-link/src/main/java/com/annaschneider/minecraft1/link/PlanGenerationOptions.java`
- `/home/runner/work/minecraft1/minecraft1/architect-link/src/main/java/com/annaschneider/minecraft1/link/LinkRequest.java`
- `/home/runner/work/minecraft1/minecraft1/architect-link/src/main/java/com/annaschneider/minecraft1/link/RequestValidator.java`
- `/home/runner/work/minecraft1/minecraft1/architect-link/src/main/java/com/annaschneider/minecraft1/link/LinkCodec.java`

**Mod integration, safety and supported materials**

- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/main/java/com/annaschneider/minecraft1/mod/command/ArchitectCommandEngine.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/main/java/com/annaschneider/minecraft1/mod/link/DesktopBridge.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/main/java/com/annaschneider/minecraft1/mod/runtime/BlockCatalog.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/main/java/com/annaschneider/minecraft1/mod/runtime/BlockWorld.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/main/java/com/annaschneider/minecraft1/mod/runtime/BlockWorldAccess.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/main/java/com/annaschneider/minecraft1/mod/fabric/FabricBlockWorld.java`

**Tests and documentation**

- `/home/runner/work/minecraft1/minecraft1/large-build/src/test/java/com/annaschneider/minecraft1/largebuild/MultiLayoutPlanningTest.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/test/java/com/annaschneider/minecraft1/largebuild/BuildQueueTest.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/test/java/com/annaschneider/minecraft1/largebuild/ImagePlanningTest.java`
- `/home/runner/work/minecraft1/minecraft1/large-build/src/test/resources/scene-v1.json`
- `/home/runner/work/minecraft1/minecraft1/architect-link/src/test/java/com/annaschneider/minecraft1/link/PlanGenerationOptionsTest.java`
- `/home/runner/work/minecraft1/minecraft1/architect-desktop/src/test/java/com/annaschneider/minecraft1/desktop/PlanGenerationModelTest.java`
- `/home/runner/work/minecraft1/minecraft1/architect-desktop/src/test/java/com/annaschneider/minecraft1/desktop/DesktopSettingsTest.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/test/java/com/annaschneider/minecraft1/mod/link/DesktopBridgeTest.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/test/java/com/annaschneider/minecraft1/mod/command/ArchitectLargeBuildCommandsTest.java`
- `/home/runner/work/minecraft1/minecraft1/architect-mod/src/stubTest/java/com/annaschneider/minecraft1/mod/fabric/FabricRuntimeTest.java`
- `/home/runner/work/minecraft1/minecraft1/docs/multi-layout-generation.md`
- `/home/runner/work/minecraft1/minecraft1/README.md`

The affected runtime targets are **large-build**, **architect-link**, **architect-mod** and
**architect-desktop**. Rebuild/reinstall **both mod and desktop**:

```powershell
# Run the mod build with JAVA_HOME pointing to JDK 25.
gradle :architect-mod:build
# Desktop distribution; shared modules target Java 17.
gradle :architect-desktop:installDist
```

Tests use the project's existing Gradle/JUnit infrastructure:

```text
gradle :large-build:test
gradle :architect-link:test :architect-desktop:test
gradle :architect-mod:test
```

Coverage includes structural layout signatures, real region/compiler bounds, deterministic geometry/JSON,
seed variation, reproducible weighted batches, exclusion/compatibility filtering, input diagnostics,
metadata roundtrips and legacy fixtures. Desktop/protocol tests cover manual seed limits, once-only blank
resolution, New variation, invalid-input handling and old request compatibility. Mod integration tests cover
full-prompt transport, saved preview/build consistency, real completed build/undo, and rejection before
save/queue for budget, world-height and border violations.

### Verification environment

Java 17 / Gradle 9.8 headless testing uses the existing Fabric stubs. The separate real-Fabric compilation
attempt ran on installed Temurin JDK 25.0.4.1, but failed during build configuration: DNS could not resolve
`maven.fabricmc.net` to download `net.fabricmc:fabric-loom:1.18-SNAPSHOT`. Consequently, no installable
Fabric jar or in-game visual validation is claimed for this environment.

| Verification | Result |
| --- | --- |
| Full `:large-build:test` module | 58 tests passed, including existing planner/build tests |
| Full `:architect-link:test` module | 19 tests passed |
| Full `:architect-desktop:test` module | 14 passed; one existing Swing Video Studio test skipped in headless mode |
| Full `:architect-mod:test` module | 58 tests passed, including Fabric-stub tests |
| Existing headless link demo | Six explicit layouts created/saved/previewed; build/pause/resume/cancel reused saved geometry |
| Secret scan | No secrets detected in changed files |
| Real Fabric JDK 25 compilation | Blocked before compilation by Fabric Maven DNS, as described above |

No skipped layout test, weakened unrelated assertion, or successful real-game run is claimed. The initial integration
compile gap and waterfall assertion failure were corrected; subsequent full mod tests passed.

## Tóm tắt tiếng Việt

Bản cũ luôn dựng cung điện trên đảo bay, nên đổi prompt vẫn gần giống nhau. Bản mới có sáu bố cục thật:
RADIAL, LINEAR, TERRACED, RING, GRID và CLIFF; LEGACY vẫn dùng được cho cảnh cũ.
Trong **Describe it**, chọn Layout/Style, nhập Seed hoặc bấm **New variation**, rồi Preview.
Preview, lưu và Build dùng cùng hình học/seed đã chốt; không tự tạo lại khi xây.
Đây là bộ sinh offline theo từ khóa, **không phải LLM và không phân tích pixel ảnh**.
Ràng buộc không hỗ trợ sẽ được báo; ảnh vẫn giữ hành vi legacy.
Cần build/cài lại **cả mod lẫn desktop**; mod jar thật cần JDK 25.
