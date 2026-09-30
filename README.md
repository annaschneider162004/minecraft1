# Minecraft AI Architect Suite (Fabric 1.20.1)

## 🇻🇳 Tóm tắt

Dự án đã được viết lại từ Forge MVP sang **Fabric-based Minecraft Java 1.20.1 multi-module foundation** theo lộ trình 7 cấp độ:

1. Build Assistant
2. Instant Builder
3. Build Transformer
4. World Generator
5. Real Image Builder
6. AI Architect
7. AI World Creator

MVP hiện tại chạy **offline, deterministic, bounded**, không cần API key và không gọi network mặc định.

**Mới: `large-build` engine cho công trình cực lớn (hàng triệu → hàng chục triệu block).**
Công trình được chia theo chunk/section 16×16×16, sinh block *lười* (không giữ toàn bộ block trong RAM), xây dần theo tick
với giới hạn block/section/thời gian mỗi tick, có tiến độ, huỷ, undo (nhật ký undo tự ghi ra đĩa khi quá lớn), lưu plan
dạng JSON và blueprint dạng file nén `.mcab`. Có pipeline **ảnh → scene plan → blueprint** (bản MVP dùng heuristic,
chưa phân tích pixel) và các interface sẵn cho **nhiều NPC cùng xây** và **camera cinematic**.

---

## 🇬🇧 Summary

This repository is rewritten to a **Fabric 1.20.1 multi-module foundation** for the seven-level Minecraft AI Architect Suite roadmap.

The current MVP is **offline, deterministic, and bounded**, with no API key required and no network calls by default.

---

## Requirements

- Java 17
- Minecraft Java 1.20.1
- Fabric Loader (>= 0.15.11)
- Fabric API (included via Gradle)
- Gradle (or wrapper)

## Build & Test

Install a Java 17 JDK (e.g. Temurin 17) and Gradle 8+ (the build is verified with Gradle 9), then from the repository
root:

```bash
java -version   # must report 17
gradle build    # compiles every module and runs all JUnit tests
gradle :large-build:test :architect-mod:test   # only the large-build engine and command tests
```

This runs compilation and tests (JUnit). Dependencies come from Maven Central (JUnit, and Gson 2.10.1 which is also the
version bundled with Minecraft 1.20.1).

Output mod jar:

```text
architect-mod/build/libs/
```

## Run (development)

Nền tảng hiện tại tập trung vào kiến trúc multi-module + logic deterministic có test.
Mục `architect-mod` đã có `fabric.mod.json` và lớp `ArchitectFabricMod` làm điểm entry nền tảng cho runtime Fabric.
Trong phiên bản foundation này, lệnh build chính là:

```bash
gradle build
```

## Commands (MVP)

- `/architect build <house|castle|temple|village>`
- `/architect shape wall <length> <height> <block>`
- `/architect shape sphere <radius> <block>`
- `/architect shape column <height> <block>`
- `/architect transform <upgrade|style|damage> <template>`
- `/architect world <kingdom>`
- `/architect mega <palace|bridge|terrace|island|waterfall|cherry|garden|path|clouds> [size] [rotation 0|90|180|270] [mirror none|x|z]`
- `/architect image plan <uploads/<file>|placeholder:<name>> <planId> [scale 1..16]`
- `/architect image load <planId>`
- `/architect image preview [planId]`
- `/architect image build [planId]`
- `/architect image export [planId]`
- `/architect image list`
- `/architect blueprint list`
- `/architect blueprint build <name>`
- `/architect queue` / `/architect progress` / `/architect cancel`
- `/architect undo`
- `/architect camera <orbit|flyby|top-down|reveal> [seconds]`
- `/architect help`

`[planId]` defaults to the plan most recently created or loaded by the player. Large builds (mega, image, blueprint)
snap their origin to the chunk grid.

Example:

```text
/architect shape sphere 6 minecraft:stone
/architect transform upgrade house

# image-to-blueprint flow (copy sky-palace.png into <dataDir>/uploads/ first)
/architect image plan uploads/sky-palace.png palace 4
/architect image preview
/architect image build
/architect progress
/architect undo

# no image yet? use a placeholder name; keywords in the name select features
/architect image plan placeholder:cherry-garden-waterfall demo 1
```

User-facing errors are returned for unknown plans (`No saved plan 'x'`), invalid or escaping paths
(`Invalid image path ...`), missing files (`Image not found: ...`), a build already running for the player
(`A build is already in progress for you ...`), a full queue, builds outside the world height/border, missing saved
blueprints and unsupported blocks.

## Folder / Module Architecture

```text
minecraft1/
├── architect-domain/      # JSON-friendly blueprint/domain + rotation/mirror/validation
├── build-assistant/       # wall/sphere/column + copy/rotate/mirror/move-ready APIs
├── instant-builder/       # template registry: house/castle/temple/village
├── build-transformer/     # deterministic upgrade/style/damage transformations
├── world-generator/       # bounded deterministic fantasy kingdom generator
├── ai-foundation/         # provider interfaces + validated request models + local fallbacks
├── large-build/           # platform-neutral engine for multi-million-block builds (see below)
│   ├── blueprint/         #   sections, palette, transforms, chunk partitioning, procedural/sectioned blueprints
│   ├── engine/            #   BuildQueue, jobs, tick budget, undo journal, listeners, WorldAccess
│   ├── generator/         #   palace, bridge, terrace, island, waterfall, cherry grove, garden, path, clouds
│   ├── scene/             #   ScenePlan/SceneRegion model, compiler, preview
│   ├── image/             #   image reference validation, analysis SPI, deterministic planner, pipeline
│   ├── persistence/       #   JSON plan store, compact .mcab blueprint codec/store
│   ├── npc/               #   multi-agent interfaces + work partitioner (future NPC builders)
│   └── camera/            #   camera paths, shot planner, recorder interface (future cinematic camera)
└── architect-mod/         # Fabric entrypoint, /architect commands wired to the large-build engine
```

## Key Technical Notes

- **Server-safe incremental placement queue** using configurable `architect.blocksPerTick` (default `64`, clamped 1..16384).
- **Undo** applies to the latest completed (or cancelled/failed) build of each player and restores the previously captured block states; undo itself runs through the queue within the same tick budget.
- **Deterministic & bounded** templates/world generation for repeatable tests.
- **No client-only API usage** in build/undo pipeline.
- **No external AI/network required by default**.

## Test Coverage

Included unit tests:

- `architect-domain`: rotation/mirror/validation logic
- `build-transformer`: deterministic transform behavior
- `large-build`: section key packing/order, chunk partitioning, rotation/mirror (checked against the domain
  `Blueprint`), streamed procedural blueprints vs. brute force, build queue budgets/conflicts/limits/chunk waiting/
  cancel/undo with disk-spilled journals, `.mcab` round-trip and corruption, JSON plan store, upload path safety and
  image header parsing, planner determinism and scaling (scale 16 > 10M blocks), NPC work partitioning, camera shots
- `architect-mod`: legacy commands plus image plan/preview/build/cancel/undo, mega builds with rotation/mirror,
  export + saved blueprint build, and user-facing error messages

## Large-build engine (multi-million-block builds)

### Why a different engine

The original queue kept every block of a structure as a Java object and every placed block in an undo list. That is fine
for 10k blocks but not for 10–50 million. The `large-build` module never materialises a whole structure:

1. **Blueprint sources are streamed by section.** A `BlueprintSource` exposes a palette, bounds, a section count and a
   forward-only `SectionCursor` that fills one reusable 16×16×16 `BlockSection` (4096 `short` palette indices,
   `0` = leave the world untouched). Sections are ordered by packed `SectionKey` = (chunkX, chunkZ, sectionY), so a
   build walks chunk column by chunk column, bottom to top.
2. **Three kinds of sources:**
   - `ProceduralBlueprint` – composed of `PlacedStructure`s (generator + offset + rotation/mirror). At construction it only
     computes the sorted list of candidate section keys and a chunk-column → structures index; each section's blocks are
     generated on demand when the builder reaches it (later structures override earlier ones).
   - `StoredBlueprint` – an `.mcab` file decoded lazily while building.
   - `SectionedBlueprint` – sparse in-memory sections, used to adapt the classic `Blueprint` templates.
3. **`BuildQueue`** (server thread only) runs FIFO jobs, at most one per player, at most `maxQueuedJobs` in total, and
   shares a per-tick budget between `maxConcurrentJobs` running jobs:
   - `blocksPerTick` (cells examined/placed), `sectionsPerTick` (sections generated or decoded) and
     `tickBudgetMillis` (wall-clock cap) – whichever is hit first ends the job's work for that tick;
   - before touching a section the job asks `WorldAccess.prepareChunk` for its chunk columns; `false` means "not ready"
     and the job simply retries next tick (no blocking chunk loads). Held columns are released when the job moves on
     or finishes;
   - `submit` validates the section limit, world height (`-64..319`) and world border up front;
   - progress (`JobProgress`: sections done/total, blocks changed, waiting-for-chunks) is available at any time;
   - `cancel` stops a build and keeps what was placed undoable; a job that throws (e.g. corrupt file) is marked
     `failed` instead of crashing the tick.
4. **Undo journal.** Previous block states are recorded in primitive arrays and, above `journalMemoryEntries`
   (default 262,144), spilled to a gzip file in `<dataDir>/journals/`. Undo streams the journal back through the queue.
   Only blocks that actually change are journaled, so a 20M-block build keeps its undo data on disk, not in the heap.
5. **Listeners** (`BuildListener`) are notified when a job starts, a section completes and a job finishes – this is the
   hook for NPC animation, the camera director and progress HUDs.

### Persistence

- **Plans**: `<dataDir>/plans/<planId>.json` (Gson, pretty printed, atomic write). Ids are restricted to
  `[a-z0-9][a-z0-9_-]{0,63}` so they cannot escape the directory. Invalid files produce `Saved plan 'x' is invalid: ...`.
- **Blueprints**: `<dataDir>/blueprints/<name>.mcab`, a compact streaming format: an uncompressed header
  (magic `MCAB`, version, name, bounds, palette, section count) followed by a gzip body of non-empty sections, each
  run-length encoded (`varint run, short palette index`). Typical fantasy scenes need well under one byte per block.
  Exports run on a background thread (only file IO, no world access) and are capped by
  `architect.maxExportSections`; loading validates magic, version, palette, section bounds/order and run lengths.
- `<dataDir>` is `-Darchitect.dataDir=...` (the Fabric adapter should point it at e.g. `config/architect` or the world
  save); without it a temporary directory is used.

### Scene plans and generators

A `ScenePlan` (JSON-serialisable, provider-neutral) is a list of `SceneRegion`s: type, position, size, rotation,
mirror and seed. Region types: `palace`, `bridge`, `terrace`, `waterfall`, `island`, `path`, `garden`, `cherry`,
`clouds`. `SceneCompiler` maps each region to a deterministic, bounded generator via `RegionGeneratorFactory` and
returns a `ProceduralBlueprint`; `ScenePreview` reports size, sections, chunk columns and a sampled block estimate without
generating the scene.

Generators (`generator/`) implement `StructureGenerator.blockAt(x, y, z)` as pure functions of local coordinates and a
seed, so any section can be generated independently and in any order. Parameters are range-checked (e.g. palace
half-width 12..128, island radius 6..512, clouds up to 16384×16384). Rotation (0/90/180/270, clockwise seen from above,
90° maps +X to +Z) and mirroring (X or Z, applied before rotation) are handled by `Transform`, consistent with the
domain `Blueprint.mirrored(..).rotated(..)`.

### Image → plan → blueprint

```text
uploads/<file>  ──ImageReferenceResolver──▶ ImageReference (validated, sha256, format, width/height)
                ──ImageAnalysisProvider───▶ ImageAnalysis   (seed, features, style, confidence, notes)
                ──ScenePlanner────────────▶ ScenePlan       (saved as JSON, previewable, editable)
                ──SceneCompiler───────────▶ ProceduralBlueprint ──BuildQueue──▶ world
```

- `ImageReferenceResolver` accepts `uploads/<relative path>` (or just the relative path) inside `<dataDir>/uploads/`,
  or `placeholder:<name>`. It rejects absolute paths, `..`, backslashes, drive letters, symlinks that escape the
  uploads directory, unsupported extensions, files over 32 MiB and files whose signature is not PNG/JPEG/GIF/WebP. Only
  the header is parsed (dimensions); pixels are never decoded and no desktop/AWT classes are used.
- `HeuristicImageAnalysisProvider` (the MVP) derives the seed from the file hash, the aspect ratio from the header and
  region types from keywords in the file name (`palace`, `castle`, `bridge`, `terrace`, `waterfall`, `island`,
  `cloud`, `garden`, `cherry`/`sakura`, `path`/`road`, ...). No keywords → the full fantasy scene.
- `DeterministicScenePlanner` lays out a "floating kingdom": a palace on stepped terraces on a central island, rings of
  floating islands on a grid stretched to the image aspect ratio, a spanning tree of bridges, paths, gardens, cherry
  groves, waterfalls and a cloud layer. `scale` is the number of island rings. Measured with the full feature set
  (`placeholder:dream`, 16:9):

  | scale | regions | footprint | sections | est. blocks |
  |------:|--------:|----------:|---------:|------------:|
  | 1  | 38    | 385 × 385     | 1,801   | ~0.5 M  |
  | 4  | 190   | 1297 × 721    | 8,013   | ~1.8 M  |
  | 8  | 808   | 2721 × 1761   | 35,686  | ~7.5 M  |
  | 16 | 2,717 | 5281 × 3041   | 116,665 | ~25 M   |
  | 24 | 6,159 | 7841 × 4641   | 262,110 | ~59 M   |

  The command caps scale at `architect.maxImageScale` (default 16); the planner API accepts up to 24. Planning and
  compiling a scale-24 scene takes well under a second; building it is paced by the tick budget (at 16,384 blocks/tick
  and 20 TPS a 25M-block scene takes roughly 1–2 minutes if the per-tick time cap allows; at the default 64 blocks/tick it takes about 5.5 hours).

#### Plugging in a real AI / computer-vision provider

1. Implement `ImageAnalysisProvider` (image → `ImageAnalysis`) and/or `ScenePlanner` (analysis → `ScenePlan`). A vision
   model can emit region types, positions and sizes directly; the planner can also post-process an LLM's JSON into
   `SceneRegion`s (the record constructors validate every value).
2. Construct `new ImageToBlueprintPipeline(resolver, yourAnalyzer, yourPlanner)` instead of `deterministic(...)`.
3. Run the provider **off the server thread** (e.g. `CompletableFuture` on an executor), save the resulting plan with
   `ScenePlanStore`, then let the player `image preview` / `image build` it. Never call a network service inside the tick.
4. Keep API keys in server config/environment, never in the repository. Nothing in this module requires network access.

### NPC and camera readiness

- `npc/`: `BuildAgent` (id, role, position, `assign(AgentTask)`), `AgentRole` (planner, builder, decorator, road worker,
  camera assistant), `NpcBuildCoordinator` (a `BuildListener` that registers agents and distributes work) and
  `AgentWorkPartitioner`, which splits a blueprint's sorted section keys (`ProceduralBlueprint.sectionKeys()`) into
  balanced, spatially coherent slices that never share a chunk column. NPCs are meant to *visualise* work (walk to their
  slice, play placement animations); block writes stay in the budgeted `BuildQueue`.
- `camera/`: `CameraKeyframe`/`CameraPath` (with interpolation), `ShotType` (orbit, fly-by, top-down, reveal),
  `CameraShotPlanner` + `DeterministicShotPlanner` (pure math, Minecraft yaw/pitch conventions), `BuildCameraDirector`
  (a `BuildListener` that tracks the target and the completed area per player) and `CameraRecorder` (playback interface;
  the implementation belongs in client-only or spectator-camera code). `/architect camera orbit` already plans a shot
  around the player's current/last build.

### Wiring into Fabric (next step)

The engine only needs a `WorldAccess` and a tick call. The Fabric adapter (not included, see limitations) is expected to:

- implement `ModInitializer` in `ArchitectFabricMod`, register `/architect` with `CommandRegistrationCallback`
  (forwarding the raw command, the player UUID and block position to `ArchitectCommandEngine.execute`), and call
  `engine.tick(world)` from `ServerTickEvents.END_SERVER_TICK`;
- back `WorldAccess` with `ServerWorld`: map ids through `Registries.BLOCK`, place with `setBlockState(pos, state,
  Block.NOTIFY_LISTENERS | Block.FORCE_STATE)` (skip neighbour updates for speed), and in `prepareChunk` add a chunk
  ticket and return whether the chunk is loaded (never force a synchronous load); remove the ticket in `releaseChunk`;
- close the engine on `ServerLifecycleEvents.SERVER_STOPPING`.

### Configuration (`-Darchitect.<name>=<value>`)

| property | default | range |
|---|---:|---|
| `blocksPerTick` | 64 | 1..16384 |
| `sectionsPerTick` | 8 | 1..256 |
| `tickBudgetMillis` | 20 | 0..45 (0 = no time cap) |
| `maxQueuedJobs` | 16 | 1..256 |
| `maxConcurrentJobs` | 2 | 1..16 |
| `maxExportSections` | 1,000,000 | 1..4,000,000 |
| `maxImageScale` | 16 | 1..24 |
| `dataDir` | temp dir | any writable directory |

## Roadmap Mapping Status

- ✅ Level 1 — Build Assistant foundation
- ✅ Level 2 — Instant Builder foundation
- ✅ Level 3 — Build Transformer foundation
- ✅ Level 4 — World Generator foundation
- ✅ Level 5 — Real Image Builder interfaces + deterministic fallback
- ✅ Level 6 — AI Architect interfaces + deterministic fallback
- ✅ Level 7 — AI World Creator orchestration interface + bounded fallback plan

## Current Limitations

- AI orchestration is local deterministic fallback only (full LLM-driven world-scale generation is future work).
- Copy/paste selection from live world is not implemented yet (API readiness exists through structure operations and blueprint transforms).
- The world generator is intentionally bounded to avoid server freeze and to keep results testable/reproducible.
- **Fabric runtime adapter not wired yet.** The build does not apply Fabric Loom (the Fabric Maven was not reachable from
  the development environment), so `architect-mod` compiles against the platform-neutral `BlockWorld`/`WorldAccess`
  abstractions and the jar is not yet a loadable mod. See "Wiring into Fabric" for the remaining adapter.
- **Image-to-blueprint is heuristic.** Pixels are not analysed; the scene comes from the file hash, aspect ratio and
  file-name keywords. It will not reproduce a specific picture until an AI/CV provider is plugged in.
- Plans are compiled and previewed synchronously when the command runs (sub-second even at scale 24); exports run in
  the background.
- Queued/running jobs and undo history are kept in memory: a server restart drops unfinished builds (placed blocks stay)
  and undo history (spilled journal files are left in `journals/`). Resume-after-restart is future work.
- Undo restores block states only (no block entities/NBT); generators only emit plain blocks.
- NPC and camera systems are interfaces and planning logic only; no entities are spawned and no camera is moved yet.
