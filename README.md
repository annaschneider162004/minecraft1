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
chưa phân tích pixel), **camera cinematic tự động quay quá trình xây** và **NPC thợ xây hiện hình quanh khu vực đang xây**.

**Mới: app Windows "Minecraft Architect" (giao diện dễ dùng).** Mở app → nhập mô tả, chọn ảnh hoặc chọn mẫu →
bấm **Preview** để xem sơ đồ → bấm **Build in Minecraft**; có nút **Pause / Cancel / Undo**, thanh tiến độ và nhật ký.
App tự kết nối với mod qua mạng nội bộ của máy (127.0.0.1, có mã bảo mật), không cần tài khoản hay API key.
Xem hướng dẫn tiếng Việt ở mục [Desktop app for Windows](#desktop-app-for-windows--ứng-dụng-windows).

---

## 🇬🇧 Summary

This repository is rewritten to a **Fabric 1.20.1 multi-module foundation** for the seven-level Minecraft AI Architect Suite roadmap.

The current MVP is **offline, deterministic, and bounded**, with no API key required and no network calls by default.

A **Windows desktop companion app** (`architect-desktop`, Java Swing with the native Windows look) lets non-technical
users type a prompt, pick a picture or a template, preview the plan and start/pause/cancel/undo builds. It talks to the
mod in real time over the **Architect Link protocol** (`architect-link`: JSON lines over a token-protected TCP socket on
`127.0.0.1` only). See [Desktop app for Windows](#desktop-app-for-windows--ứng-dụng-windows).

---

## Requirements

- Minecraft Java **1.20.1** with **Fabric Loader** (>= 0.16.10) and **Fabric API** for 1.20.1 (to play)
- **JDK 25** to *build the mod jar* (Fabric Loom 1.18 runs only on JDK 25; the mod itself targets Java 17, the
  version Minecraft 1.20.1 runs on)
- JDK 17+ for everything else (desktop app, tests, demo server)
- Gradle 9 (or the wrapper: `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain …`)

## Build & Test

Install a Java 17 JDK (e.g. Temurin 17) and Gradle 8+ (the build is verified with Gradle 9), then from the repository
root:

```bash
java -version   # must report 17
gradle build    # compiles every module and runs all JUnit tests
gradle :large-build:test :architect-mod:test   # only the large-build engine and command tests
gradle :architect-link:test :architect-desktop:test   # protocol/schema and desktop input validation
```

This runs compilation and tests (JUnit). Dependencies come from Maven Central (JUnit, and Gson 2.10.1 which is also the
version bundled with Minecraft 1.20.1).

### Two build modes of `architect-mod`

| mode | when | what you get |
|---|---|---|
| **Fabric** | Gradle runs on **JDK 25** (default there) | Fabric Loom downloads Minecraft 1.20.1, yarn mappings, Fabric Loader and Fabric API, compiles against the real game and writes the **installable mod jar** |
| **Headless** | Gradle runs on JDK 17–24, or `-Parchitect.stubs=true` | compiles against `fabric-stubs` (offline, nothing downloaded); tests and `runLinkDemo` work, **no mod jar** is produced (`:architect-mod:jar SKIPPED`) |

Gradle prints `:architect-mod builds in headless mode …` when it is not building the real mod. Passing
`-Parchitect.stubs=false` on an older JDK fails with an explanation instead of a cryptic class-version error.

### Build the mod jar

```bat
rem Windows: point JAVA_HOME at a JDK 25 for this window only (adjust the folder to your install)
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.0-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
gradle :architect-mod:build
```

```bash
# Linux/macOS
JAVA_HOME=/path/to/jdk-25 gradle :architect-mod:build
```

The first run downloads Minecraft and the Fabric toolchain (a few hundred MB, several minutes). The jar to install is

```text
architect-mod/build/libs/architect-mod-2.0.0-fabric.jar
```

It is remapped to Fabric's intermediary names (so it loads in a normal Fabric install) and contains every
platform-neutral module (`large-build`, `architect-link`, …); Gson comes from Minecraft. Ignore
`architect-mod/build/devlibs/` — those jars are for the development environment only. Versions are set in
`gradle.properties` (`minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_api_version`, `loom_version`).

## Run (development)

```bash
gradle build                         # all modules + tests (headless on JDK 17, real Fabric build on JDK 25)
gradle :architect-mod:runLinkDemo    # demo server: simulated world + player "DemoPlayer", no Minecraft needed
gradle :architect-mod:runClient      # JDK 25 only: starts Minecraft 1.20.1 with the mod from source (Loom)
```

On JDK 25, add `-Parchitect.stubs=true` to `runLinkDemo` to skip the Minecraft download.

## Install in Minecraft 1.20.1 (Fabric) / Cài vào Minecraft thật

1. **Fabric Loader:** run the Fabric installer from <https://fabricmc.net/use/installer/>, choose *Minecraft 1.20.1*,
   click *Install*. The launcher now has a profile **fabric-loader-1.20.1**.
2. **Fabric API:** download the Fabric API file for **1.20.1** (e.g. `fabric-api-0.92.x+1.20.1.jar`) from Modrinth or
   CurseForge and put it into the `mods` folder: `%APPDATA%\.minecraft\mods` on Windows (`~/.minecraft/mods` on
   Linux, `~/Library/Application Support/minecraft/mods` on macOS). Create the folder if it does not exist.
3. **Architect:** copy `architect-mod/build/libs/architect-mod-2.0.0-fabric.jar` into the same `mods` folder.
4. Start the **fabric-loader-1.20.1** profile and open (or create) a single-player world. The game log shows
   `[Architect] Desktop link started on 127.0.0.1:47821` and the mod writes
   `%APPDATA%\.minecraft\config\architect\desktop-link.json`.
5. In chat: `/architect build house` — the house is built next to you over the next ticks; `/architect undo` removes
   it. `/architect help` lists every command.
6. Start the desktop app (see below). It finds the link file automatically and shows
   **Connected to Minecraft - player <your name>**.

Dedicated server: put the same two jars (Fabric API + Architect) into the server's `mods` folder. The link file is
written to `<server>/config/architect/desktop-link.json` and accepts connections from the same machine only.

**Real game or demo?** The status line of the app shows the player name. **`DemoPlayer`** means you are connected to
`runLinkDemo` (an in-memory world, nothing appears in Minecraft): stop the demo and in **Settings…** set the link file
back to the default `…\.minecraft\config\architect\desktop-link.json`. Your own Minecraft name means the real world.

### Troubleshooting

| Problem | Cause / fix |
|---|---|
| Minecraft says *requires fabric-api* / *Incompatible mods found* mentioning `fabric-api` | Fabric API is missing or for another version: install Fabric API **for 1.20.1** into `mods`. |
| *Mod 'Minecraft AI Architect Suite' requires minecraft 1.20.1* | Wrong game version or profile: start the **fabric-loader-1.20.1** profile. |
| The mod is not listed / `/architect` is unknown | The jar is not in the `mods` folder of the profile you start, or you copied a jar from `build/devlibs/` instead of `build/libs/`. |
| Gradle: *Building the Fabric mod jar needs Gradle to run on JDK 25* or no jar in `build/libs` | Gradle runs on JDK 17 (headless mode). Set `JAVA_HOME` to a JDK 25 and build again. |
| App: *No player is in a world yet* | The game is on the title screen, or (dedicated server) nobody is online. Join the world. With several players online the app acts for the alphabetically first one unless you enter your name in **Settings… → Player name**. |
| App: *Minecraft did not answer in time*, builds stop | Single-player is **paused** (Esc menu or window lost focus): the integrated server does not tick, so nothing is built. Close the menu, press **F3 + P** to disable pause-on-lost-focus, or *Open to LAN*. |
| App connects to `DemoPlayer` | You are on the demo, see *Real game or demo?* above. |
| `/architect …` answers *Architect commands must be run by an in-game player* | It was run from the server console or a command block; run it as a player. |

**Tóm tắt tiếng Việt:** build jar bằng JDK 25 (`gradle :architect-mod:build`) → chép
`architect-mod\build\libs\architect-mod-2.0.0-fabric.jar` và Fabric API 1.20.1 vào `%APPDATA%\.minecraft\mods` →
mở profile *fabric-loader-1.20.1* → vào world → gõ `/architect build house` hoặc mở app desktop: app hiện **tên nhân
vật của bạn** (không phải `DemoPlayer`) là đã nối vào game thật. Game bị tạm dừng (Esc / chuyển cửa sổ) thì không xây —
nhấn **F3 + P**.

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
- `/architect pause` / `/architect resume` (a paused build keeps its place, stops placing blocks and releases its chunk tickets)
- `/architect undo`
- `/architect camera <auto|orbit|follow|wide|stop|status>` (live cinematic camera, see below)
- `/architect camera <flyby|top-down|reveal> [seconds]` (plans an offline shot path around the last build)
- `/architect npc <on|off|status>`
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
│   ├── npc/               #   multi-agent model, work partitioner, crew coordinator (visible NPC builders)
│   └── camera/            #   camera paths, shot planner, director + live cinematic camera state machine
├── architect-link/        # Architect Link protocol v1: shared request/response schema, validation, localhost server/client
├── architect-mod/         # Fabric entrypoint, /architect commands wired to the large-build engine
│   ├── link/              #   DesktopBridge (desktop requests -> commands, progress push), headless LinkDemoServer
│   └── runtime/           #   ArchitectServerRuntime (engine + bridge lifecycle for the Fabric adapter), config
├── video-studio/          # prompt -> story -> clip plan -> narration (Piper) -> MP4 (FFmpeg); ExportFlow runs
│   │                      #   recording + narration in parallel and auto-exports; no Minecraft dependency
│   ├── story/             #   prompt analysis, deterministic templates (EN/VI), optional local Ollama writer, fallback
│   ├── voice/             #   voice-pack discovery/validation, TTS engine SPI, Piper engine, narrator, narration track
│   ├── record/            #   recorder SPI, FFmpeg screen recorder (gdigrab / avfoundation / x11grab)
│   └── render/            #   FFmpeg locator/probe, segment renderer, subtitles
└── architect-desktop/     # Windows desktop companion app (Swing), talks to the mod via architect-link
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
  cancel/undo with disk-spilled journals, jobs staying in the world they were started in, `.mcab` round-trip and corruption, JSON plan store, upload path safety and
  image header parsing, planner determinism and scaling (scale 16 > 10M blocks), NPC work partitioning, camera shots,
  the cinematic camera state machine (modes, stale updates, pause, terminal states) and the NPC crew coordinator
  (bounded crews, task assignment, pause/resume, cleanup)
- `architect-mod`: legacy commands plus image plan/preview/build/cancel/undo, mega builds with rotation/mirror,
  export + saved blueprint build, pause/resume, `camera`/`npc` commands (live modes per player, legacy shot planning),
  camera packet round-trips and stale-job rejection, and user-facing error messages; `DesktopBridge` end-to-end over a real
  socket (hello/token, missing player, prompt plan, picture upload, template preview, build/pause/resume/cancel/undo,
  progress push, port fallback); `fabric.mod.json` (expanded version, dependencies, entrypoints) and a check that no
  common class references client-only code. Headless mode additionally runs `src/stubTest`: the Fabric entrypoint
  driven through stubbed Fabric events (hook registration, `/architect build house` placing blocks and undoing them,
  console/failed commands as feedback, `desktop-link.json` in `<config>/architect` created on start and deleted on
  stop, the desktop app seeing the real player name, fresh runtime after reopening a world, chunk tickets released on
  completion and shutdown), the builder NPC crew against stub entities (bounded tagged crews, no block writes, removal
  on every terminal state and on shutdown, orphan sweep, job isolation), `FabricBlockWorld` (ids, height/border limits,
  tickets, server-thread guard) and `FabricPlayerDirectory` (named/single/multiple/no players, current world)
- `architect-link`: request validation (ids, plan names, sources, prompt/scale limits, base64 image size, player
  names), JSON codec round-trips and lowercase wire names, link-file read/write, server/client handshake, wrong token,
  oversized lines, timeouts and connection-refused messages
- `architect-desktop`: plan-name suggestion/validation, picture checks (type, empty, too large), safe upload names and
  default link-file locations on Windows/macOS/Linux; Video Studio build timeline (milestones relative to the recording),
  settings round-trip (incl. export mode and record length) and diagnostics for missing FFmpeg/Piper/screen recording and
  invalid voice packs
- `video-studio`: prompt analysis (length, language, timelapse), deterministic template stories and the fallback when the
  local AI is absent or answers garbage, Ollama URL/model validation, segment planning from milestones (ordering,
  trimming, timelapse speed-up, fades), voice-pack discovery/validation (missing config, bad JSON, unsupported engine,
  duplicates, symlink escapes), narration hooks, export orchestration (narrated, non-narrated when TTS fails, title cards
  without footage, missing FFmpeg, temp-file cleanup), the three export modes (Record only, Narrate only, Auto-export
  when both complete), recording and narration running in parallel with automatic muxing, failures (missing FFmpeg or
  voice backend, recording/narration/mux errors) that keep finished outputs, cancellation, EN/VI status labels, screen
  grabber selection, the narration track and, when FFmpeg is installed, a real MP4 render

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
5. **Listeners** (`BuildListener`) are notified when a job starts, a section completes and a job finishes – this drives
   the builder NPC crews, the camera director and progress HUDs.

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

### Cinematic camera and visible builder NPCs

Both features are live in the Fabric runtime and are built on the same abstractions as before.

**Cinematic camera.** `BuildCameraDirector` (a `BuildListener`) tracks, per player, the job being built, the bounds
completed so far and the section at the build frontier. `ServerCameraCoordinator` sends that snapshot to the player's
client as a coalesced `architect:camera_state` packet (at most one every `cameraUpdateTicks` ticks — never one per
placed block), and `architect:camera_mode` carries the chosen mode. On the client, `ClientCinematicCamera` feeds the
packets into the platform-neutral `CinematicCameraController`, which produces a smoothed pose; the pose is applied to an
invisible, client-only armour stand used as the render view entity through `MinecraftClient.setCameraEntity`. **The
player body is never moved or teleported.**

| command | shot |
|---|---|
| `/architect camera auto` | switches between orbit, follow and wide every `cameraAutoShotSeconds` |
| `/architect camera orbit` | circles the area completed so far |
| `/architect camera follow` | stays close to the section currently being built |
| `/architect camera wide` | establishing shot framing the whole planned bounds |
| `/architect camera stop` | stops filming and restores your view |
| `/architect camera status` | prints the current mode |

The original view, perspective and HUD are restored when you stop the camera, when the job completes, is cancelled or
fails, when the recording is stopped, when you leave the world and when the client disconnects. Updates belonging to an
older job are ignored, so a new build never gets filmed with stale geometry. Camera commands only affect the player who
ran them.

**Visible builder NPCs.** `BuildCrewCoordinator` (the `NpcBuildCoordinator` implementation) creates one crew per build
job: a bounded number of workers (`npcMaxWorkers`, and at most one per `npcSectionsPerWorker` sections, so small builds
get a small crew), each given a slice of the job's sections by `AgentWorkPartitioner`. `VillagerWorkerFactory` turns
those agents into villagers named after their role (Foreman, Builder, Decorator, Material Runner), which are
repositioned on a ring around the active section every `npcUpdateSections` completed sections and look at the blocks
being placed. Workers are AI-disabled, invulnerable, silent, weightless, cannot pick up loot and are tagged
`architect_worker`, so they never open doors, trample crops, wander off or interfere with gameplay — and the cleanup
sweep only ever touches entities this mod created. They are removed when the job completes, is cancelled, fails or
leaves the queue, when NPCs are disabled and when the server stops; a sweep on server start also removes any worker left
behind by a crash. **NPCs are purely cosmetic: every block is still placed by the budgeted `BuildQueue`.**

Use `/architect npc off` (or the desktop toggle) to disable them; they are also skipped for chunks the build has not
prepared yet, because cosmetic entities never force chunks to load.

### How the Fabric runtime is wired

- `ArchitectFabricMod` (`main` entrypoint) registers `/architect [<command…>]` with `CommandRegistrationCallback` and
  forwards the raw text, the player UUID, the player's current world and block position to
  `ArchitectCommandEngine.execute`; failures become red chat feedback, never exceptions.
- `ServerLifecycleEvents.SERVER_STARTING` creates one `ArchitectServerRuntime` per server (so per opened world) with
  `<config>/architect` as data directory and starts the desktop link; `SERVER_STOPPING`/`SERVER_STOPPED` close it
  (deleting `desktop-link.json`) and release every chunk ticket. Nothing is kept in static fields, so switching worlds
  starts fresh.
- `ServerTickEvents.END_SERVER_TICK` calls `runtime.tick(...)` once per tick: desktop requests, the build queue and
  progress events all run on the server thread.
- `FabricBlockWorld` backs `BlockWorld` with `ServerWorld`: ids are validated through `Registries.BLOCK` (clear errors
  for malformed or unknown ids, positions outside the height range or world border), blocks are placed with
  `setBlockState(pos, defaultState, Block.NOTIFY_ALL)` and mutations off the server thread are refused.
  `prepareChunk` adds a `FORCED` chunk ticket and reports whether the chunk is loaded (no synchronous loading); tickets
  are released when the section is done and on completion, cancel, pause, failure and shutdown.
- Every build/undo job remembers the world it was started in, so it keeps building there even if its owner changes
  dimension; the overworld is only the fallback for the tick call.
- `FabricPlayerDirectory` resolves the desktop app's player through `server.getPlayerManager()`: the named player, the
  only player online, or — with several players and no name — the alphabetically first one.
- `ArchitectClientMod` (`client` entrypoint) handles the optional ReplayMod recording channel and the cinematic camera
  packets; no common class references client-only code (a test enforces it), so dedicated servers start without it.

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
| `camera` | true | `false` disables the cinematic camera server-side |
| `cameraMode` | `off` | `off`, `auto`, `orbit`, `follow`, `wide` — mode used before a player chooses one |
| `cameraUpdateTicks` | 10 | 2..200 (how often a camera state packet may be sent) |
| `cameraOrbitDistance` | 18 | 3..128 |
| `cameraOrbitHeight` | 12 | 1..96 |
| `cameraOrbitSpeed` | 9 | 1..90 degrees per second |
| `cameraAutoShotSeconds` | 12 | 3..120 |
| `cameraMaxSpeed` | 18 | 1..64 blocks per second |
| `cameraMaxDistance` | 192 | 16..512 |
| `npcBuilders` | false | `true` enables builder NPCs at startup; the desktop setting can also enable them |
| `npcMaxWorkers` | 4 | 1..12 |
| `npcSectionsPerWorker` | 64 | 16..100000 |
| `npcUpdateSections` | 2 | 1..64 |
| `link` | true | `false` disables the desktop link |
| `linkPort` | 47821 | 1024..65535 (the next free port is used if busy) |

## Desktop app for Windows / Ứng dụng Windows

### Technology choice

| Part | Choice | Why |
|---|---|---|
| Desktop UI | **Java 17 + Swing** with the native Windows look-and-feel (`architect-desktop`) | Same language, JDK and Gradle build as the mod; no extra UI framework or Node/Rust/.NET toolchain; reuses the shared protocol classes directly; packaged into a normal Windows program with `jpackage` (bundled Java runtime). |
| Connection | **Architect Link protocol v1** over **TCP on 127.0.0.1** (`architect-link`) | Real-time two-way (requests + pushed progress), no HTTP/WebSocket library to shade into the mod, never reachable from other computers. |
| Schema | Java records serialised with **Gson 2.10.1** (the version Minecraft 1.20.1 already ships) | One source of truth used by both the mod and the app, validated on both sides. |

### Install and launch (Windows)

1. Install **Java 17** (e.g. *Eclipse Temurin 17* from adoptium.net, choose "Add to PATH") and **Gradle 8+** — or skip
   both and use a packaged build (step 4).
2. Download the repository (green **Code → Download ZIP** on GitHub, then extract) or `git clone` it.
3. Open **Command Prompt** in the extracted folder and run:
   ```bat
   gradle :architect-desktop:installDist
   architect-desktop\build\install\architect-desktop\bin\architect-desktop.bat
   ```
   The **Minecraft Architect** window opens. Create a desktop shortcut to that `.bat` file for one-click start.
4. *(Optional, for sharing with friends)* build a stand-alone program that does not need Java installed:
   ```bat
   gradle :architect-desktop:packageApp
   ```
   Copy the folder `architect-desktop\build\jpackage\Minecraft Architect\` anywhere and double-click
   **Minecraft Architect.exe**. (`jpackage` comes with the JDK 17; it builds for the OS it runs on.)

### Connect to Minecraft

1. Install the Architect mod in Minecraft 1.20.1 (Fabric) and start the game (see
   [Install in Minecraft 1.20.1](#install-in-minecraft-1201-fabric--cài-vào-minecraft-thật)).
2. Open a single-player world (or join the server where the mod runs). The mod listens on `127.0.0.1:47821` and writes
   `%APPDATA%\.minecraft\config\architect\desktop-link.json` (port + a random secret token, recreated each start).
3. Start the app. The dot at the top turns **green – "Connected to Minecraft - player …"** within a few seconds. The app
   retries automatically every 5 seconds, so the order of starting does not matter.
4. **Pause game tip:** single-player Minecraft pauses when its window loses focus, which also pauses building. Press
   **F3 + P** in game (disables pause-on-lost-focus) or use *Open to LAN* before switching to the app.

Other launchers (CurseForge, Prism, MultiMC, Modrinth): open **Settings…** in the app and choose
`<instance folder>\config\architect\desktop-link.json` with **Browse…**. On a server with several players enter your
Minecraft name in **Player name**. Settings are remembered.

### Camera & NPC Settings

Open **Settings… → Camera & NPC Settings** to save these controls locally:

| control | default | safe range / effect |
|---|---:|---|
| Enable cinematic camera | Off | Allows the selected player's camera to film during **Build + Record**; disabling restores the normal view. |
| Show builder NPCs | Off | Spawns cosmetic workers around active build sections; disabling removes them. |
| Maximum NPCs | 4 | 0–12 per build; 0 prevents spawning even when NPCs are enabled. |
| Camera height | 12 | 5–80 blocks above the framed build. |
| Rotation speed | 9 | 1–30 degrees per second around the build. |

**Save and apply** sends the bounded values through the local desktop link when connected. Changes made offline
remain saved and are sent on reconnect (or when a player enters the world on an already-connected link). The camera
remains off until filming starts; the server's `-Darchitect.camera=false` still prevents activation. NPC limits
apply to the selected player's running builds as well as new ones, without changing other players' crews. On a dedicated server the desktop link must run on the same machine as
the mod; no client-only classes are loaded by the server.

| What you see | What to do |
|---|---|
| *Minecraft is not running or the Architect mod is not loaded* (grey/red dot) | Start Minecraft with the mod and open a world; check the link-file path in **Settings…**. |
| *No player is in a world yet* (orange dot) | Enter a world. You can already prepare plans and previews. |
| *Minecraft did not answer in time* | The game is paused — press **F3 + P** or open to LAN. |
| *Connection refused / token rejected* | Minecraft was restarted; the app reconnects by itself with the new token. |
| *Recording unavailable (ReplayMod not active)* | ReplayMod is optional: install it for 1.20.1 to record, or keep building without it — the cinematic camera still works and the recording buttons only show a message. |
| The cinematic camera does not activate | Check `/architect camera status`, make sure a build is running (the camera needs job bounds), that the mod is installed **on the client** too, and that the server does not run with `-Darchitect.camera=false`. |
| No workers appear around the build | Enable them in **Camera & NPC Settings** (or `/architect npc on`); workers only spawn in chunks the build already prepared, and their number scales with the job size (`npcSectionsPerWorker`). |
| Port 47821 is used by another program | The mod uses the next free port automatically; the app reads it from the link file. Or set `-Darchitect.linkPort=<port>` in the launcher's JVM arguments. |

### How to use (UI flow)

```text
┌───────────────────────────────────────────────────────────────┐
│ ● Connected to Minecraft - player Anna   [Disconnect][Settings][Help] │
├──────────── 1. What do you want to build? ──┬── 2. Preview ─────┤
│ [Describe it] [From a picture] [Template]   │  top-down map of  │
│  prompt text / drag&drop picture / list     │  regions, legend, │
│ Size: ──●────── (scale 1..16, block estimate)│  "+" = you        │
│ Name: white-palace          [ Preview ]     │  plan summary text│
├──────────── 3. Build it in Minecraft ───────┴───────────────────┤
│ [Build in Minecraft] [Build + Record] [Pause/Resume] [Cancel]   │
│ ████████░░░░ 42% (running)          notice line                 │
│ Recording: ● idle [Start Recording][Stop & Save]                │
│            [x] Cinematic camera  [x] Builder NPCs   camera info │
├──────────── Activity log ───────────────────────────────────────┤
└───────────────────────────────────────────────────────────────┘
```

1. **Describe it** – type e.g. *white palace with waterfalls, bridges and cherry trees* (keywords shape the layout), or
   **From a picture** – drag a PNG/JPG/GIF/WebP (≤ 8 MiB) onto the box or click *Choose picture…*, or **Template** –
   house, castle, temple, village.
2. Move **Size** (bigger = more blocks; the label shows an estimate) and keep or change the plan **Name**.
3. **Preview** – the app uploads the picture if needed, asks the mod to create the plan and draws a top-down map with
   the plan summary (size, sections, estimated blocks). Nothing is placed yet.
4. **Build in Minecraft** – builds at your current position (large plans snap to the chunk grid). The progress bar
   and log update live. **Pause/Resume** stops and continues, **Cancel** stops for good (placed blocks stay and can be
   undone), **Undo last build** restores the previous blocks. Cancel and Undo ask for confirmation.

5. **Build + Record** – one click that (1) starts the in-game recording, (2) switches your view to the automatic
   cinematic camera (if **Cinematic camera** is ticked), (3) submits the build, (4) follows it to the end and (5) stops
   and saves the recording, which also returns you to your normal view. If the build cannot be started, the camera is
   switched off again. **Start Recording** / **Stop & Save** do the same steps without building.
   **Builder NPCs** toggles the visible workers for the whole server (same as `/architect npc on|off`).

Recording uses **ReplayMod**, an *optional* dependency reached only through reflection in `ReplayModBackend`: if it is
not installed, the recording controls report "Recording unavailable" instead of failing, and the cinematic camera and
builder NPCs keep working.

Everything works offline; no account or API key is needed. Try it without Minecraft (two terminals):

```bat
gradle :architect-mod:runLinkDemo        & rem simulated server + player "DemoPlayer" (in-memory world)
gradle :architect-desktop:runWithDemo    & rem the app, connected to the demo
```

### Video Studio: narrated build videos / Video tự động + lồng tiếng

Click **Video Studio...** in the connection bar. Everything runs on this computer: no account, API key or upload.

1. **Record the build** – **Build + Record** (ReplayMod) or any screen recorder (OBS…). For ReplayMod open the replay and
   use *Render* to save an MP4 into `.minecraft\replay_videos`. While a build runs, the app notes its milestones
   (start, every 10 %, finish) relative to the recording start; the story uses them to pick matching footage.
2. **Write a prompt** – e.g. `60 second video: a kingdom rises from a deserted island`. A length in the text
   (`45 seconds`, `2 phút`) wins over the *Length* box, `timelapse`/`fast` gives more sped-up footage, Vietnamese text (or
   a Vietnamese voice) gives a Vietnamese story.
3. **Add footage** – **ReplayMod videos...** or **Add videos...** (MP4/MKV/MOV/WebM…). Without footage the video is made
   of title cards. Unreadable files are skipped with a warning.
4. **Pick a voice** – choose from the list and click **Preview voice**.
5. **Write story** (editable scene list: title, narration, footage moment) → **Export video**. The output folder
   (default `Videos\Minecraft Architect`) receives `<title>-<date>.mp4` (H.264 + AAC), `.srt` subtitles and
   `-story.txt`. Temporary clips go to a `architect-video-*` temp folder that is deleted afterwards.

#### Export modes and automatic voice-over export / Chế độ xuất và tự động lồng tiếng

Step **5** of the Video Studio runs the whole job without a manual export. Pick a **Mode**, set **Record (seconds)** and
click **Start** (the story is written first if needed):

| Mode | What it does | Output (in the output folder) | Needs |
|---|---|---|---|
| **Record only** | records the screen and saves the raw video; no narration, no muxing | `<title>-<date>-recording.mp4` | FFmpeg |
| **Narrate only** | speaks the story with the selected voice; nothing is recorded | `<title>-<date>-narration.wav`, `-story.txt`, `.srt` | Piper + a voice |
| **Auto-export when both complete** | records **and** narrates in parallel; once both are finished the narration is muxed into the recording and the final MP4 is exported automatically | `<title>-<date>.mp4` plus the recording, narration, script and subtitles | FFmpeg, Piper + a voice |

Status labels and order / Nhãn trạng thái và thứ tự:

| English | Tiếng Việt | When |
|---|---|---|
| Checking dependencies... | Đang kiểm tra phụ thuộc... | output folder writable, FFmpeg, screen recorder, voice backend |
| Recording video... | Đang quay video... | screen recording runs (in parallel with narration) |
| Generating narration... | Đang tạo giọng đọc... | the local voice speaks every scene (in parallel with recording) |
| Waiting for remaining job... | Đang chờ tác vụ còn lại... | one job finished first; its output is kept while the other finishes |
| Muxing audio and video... | Đang ghép audio và video... | both outputs are ready: narration + recording → final MP4 |
| Export complete | Xuất video hoàn tất | the final file exists; **Open video** / **Open folder** |
| Export failed: &lt;reason&gt; | Xuất video thất bại: &lt;reason&gt; | e.g. *FFmpeg not found*, *Narration backend unavailable*, *Recording failed*, *Narration failed*, *Failed to mux audio and video*; **Retry** starts again |

`Idle → Checking dependencies → Recording + Narration → Waiting for remaining job → Muxing audio and video → Export complete`.
The recording and narration have their own status lines. Labels follow the story language (Vietnamese prompt →
Vietnamese labels).

- If either job fails, the other is cancelled and **no final video** is written; a job that already finished keeps its
  file (`-recording.mp4` / `-narration.wav`) so nothing is lost. Half-written recordings are deleted.
- **Cancel** stops both jobs (FFmpeg/Piper are terminated); it is shown as *Cancelled*, not as a failure.
- Unlike **Export video**, auto-export does not fall back to a silent video: a missing voice backend stops it up front.
- Recording uses FFmpeg's screen grabber: Windows `gdigrab` (whole desktop), macOS `avfoundation` (screen 0, allow
  *Screen Recording* for the app in System Settings), Linux `x11grab` (`$DISPLAY`; Wayland-only sessions are not
  supported). Keep the Minecraft window visible (full screen works best) and start the build in the main window after
  clicking **Start**. Scenes are spread evenly over the new recording.
- Code: `ExportFlow` (orchestration), `ExportMode`, `FlowStage`/`FlowStatus` (labels), `record.ScreenRecorder`
  (`FootageRecorder` SPI, so another recorder can be plugged in), `voice.NarrationTrackWriter` (narration track).

Pipeline stages (module `video-studio`, usable without the game): prompt analysis → story (`TemplateStoryGenerator`, or
`LocalLlmStoryGenerator` with automatic fallback) → narration (`Narrator` + `TtsEngine`) → scene lengths fitted to the
narration → `SegmentPlanner` (clip selection, ordering, trimming, timelapse up to 200×, 0.5 s fades) →
`VideoRenderer` (FFmpeg) → `VideoPipeline` writes the files.

**Optional programs** (paths can also be chosen in **Tools...**):

| Program | Needed for | Without it |
|---|---|---|
| [FFmpeg](https://ffmpeg.org) (on PATH, or `ARCHITECT_FFMPEG`) | exporting any video | Export shows "FFmpeg was not found" with install steps; story writing still works |
| [Piper](https://github.com/rhasspy/piper) (on PATH, or `ARCHITECT_PIPER`) + a voice pack | narration | the video is exported **without narration** (warning in the log) |
| [Ollama](https://ollama.com) (`ollama pull llama3.2`, tick **Use local AI**) | AI-written story | the built-in templates are used (also when Ollama is down, slow or answers nonsense) |

Ollama is only contacted on `localhost`/`127.0.0.1`/`::1`.

**Voice packs** – the voices folder is `%APPDATA%\MinecraftArchitect\voices` (macOS:
`~/Library/Application Support/MinecraftArchitect/voices`, Linux: `~/.minecraft-architect/voices`; override with
`ARCHITECT_VOICES_DIR` or in **Tools...**). Add a voice without any code change:

1. Download a Piper voice from <https://huggingface.co/rhasspy/piper-voices>, e.g. `vi_VN-vais1000-medium.onnx` **and**
   `vi_VN-vais1000-medium.onnx.json` (Vietnamese) or `en_US-amy-medium.*` (English).
2. Copy both files into the voices folder (**Open voices folder**), directly or in one sub-folder, and click **Refresh**
   (voices are also discovered at startup and listed in the log).
3. Optional `<id>.voice.json` next to the model to set metadata:
   `{"name": "Bà kể chuyện", "language": "vi-VN", "engine": "piper", "description": "warm"}`.

A pack is rejected (and listed with the reason in the log / **Tools...**) when the config is missing, the model is empty
or larger than 2 GB, the JSON is invalid or over 1 MB, the engine is not `piper`, the language or sample rate is invalid,
the id is already used or the files point outside the voices folder.

**Troubleshooting**

- *"FFmpeg was not found"* – install FFmpeg (`winget install ffmpeg`), restart the app or pick `ffmpeg.exe` in **Tools...**.
- *"Narration skipped: …"* – Piper or the voice is missing/broken; the MP4 is still exported. Check **Tools...**, which
  lists every program and every rejected voice pack.
- *"No voices yet"* – the folder must contain both `.onnx` and `.onnx.json` with the same name.
- *"Local AI unavailable, using templates"* – start Ollama (`ollama serve`) and pull the model, or untick **Use local AI**.
- *Export failed: Recording failed* – **Tools...** shows whether screen recording is available (FFmpeg found, X11
  display on Linux, screen-recording permission on macOS). The log has FFmpeg's message.
- *Export failed: Narration backend unavailable* – pick a voice and install Piper (see above); use **Record only** if
  you only need the video.
- *Export failed* – the log shows the last FFmpeg lines. Re-render the replay to a standard MP4, check free disk space
  and that the output folder is writable.

**Tiếng Việt:** bấm **Video Studio...** → gõ yêu cầu (ví dụ *Video 60 giây kể chuyện một vương quốc mọc lên từ đảo
hoang*) → **ReplayMod videos...** để thêm video đã render → chọn giọng, bấm **Preview voice** để nghe thử → **Write
story** → **Export video**. Cần cài **FFmpeg**; muốn có giọng đọc thì cài **Piper** và chép giọng (`.onnx` + `.onnx.json`)
vào thư mục voices rồi bấm **Refresh**. Không có Piper/giọng thì video vẫn được xuất nhưng không có lời đọc; không có
Ollama thì dùng kịch bản mẫu. Muốn tự động: ở bước **5** chọn **Mode** → **Auto-export when both complete**, đặt
**Record (seconds)**, bấm **Start** rồi xây trong Minecraft: app vừa quay màn hình vừa tạo giọng đọc, xong cả hai thì tự
ghép và xuất MP4 (không cần bấm Export). **Record only** chỉ quay video thô; **Narrate only** chỉ tạo file giọng đọc
`.wav`. Nếu một bên lỗi thì không xuất video cuối, phần đã xong được giữ lại; bấm **Retry** để thử lại.

### Hướng dẫn nhanh (tiếng Việt)

1. Cài **Java 17** (Temurin 17) và **Gradle**, tải repo về (Code → Download ZIP) rồi giải nén.
2. Mở **Command Prompt** trong thư mục repo, chạy `gradle :architect-desktop:installDist`, rồi nhấp đúp
   `architect-desktop\build\install\architect-desktop\bin\architect-desktop.bat` (có thể tạo shortcut ra Desktop).
   Muốn có file **Minecraft Architect.exe** chạy không cần cài Java: `gradle :architect-desktop:packageApp`.
3. Mở Minecraft 1.20.1 có mod Architect, vào thế giới. App tự kết nối (chấm **xanh lá**). Nhấn **F3 + P** trong game
   để game không tự tạm dừng khi chuyển sang app.
4. Chọn **Describe it** (gõ mô tả), **From a picture** (kéo thả ảnh) hoặc **Template** (mẫu có sẵn) → chỉnh **Size**
   → bấm **Preview** để xem sơ đồ → bấm **Build in Minecraft**. Dùng **Pause/Resume**, **Cancel**, **Undo last build**
   khi cần; theo dõi thanh tiến độ và nhật ký ở dưới.
5. Dùng launcher khác (CurseForge, Prism…)? Bấm **Settings…** và chọn file
   `<thư mục instance>\config\architect\desktop-link.json`.

### Architect Link protocol v1

- Transport: one JSON object per line (UTF-8, `\n`) over TCP, bound to the loopback address only.
- Discovery: the mod writes `desktop-link.json` = `{"protocol":1,"host":"127.0.0.1","port":47821,"token":"<48 hex>","modVersion":"…"}`
  into its data directory (owner-only permissions where supported) and deletes it on shutdown.
- Handshake: the first request must be `hello` with the token (checked in constant time) within 10 s, otherwise the
  connection is closed. At most 4 desktop connections.
- Requests (`LinkRequest`): `{"v":1,"id":"7","type":"plan","prompt":"white palace","planId":"white-palace","scale":2}`.
  Types: `hello`, `status`, `upload_image` (`fileName`, base64 `data` ≤ 8 MiB), `plan` (`source` or `prompt`, `planId`,
  `scale`), `preview` / `build` (`mode` = `template`|`plan`, `template` or `planId`), `pause`, `resume`, `cancel`, `undo`.
- Messages (`LinkMessage`): `kind` = `response` (same `id`, `ok`, `message`, optional `server`, `plan`, `job`, `source`),
  `progress` (pushed `job` status: state, percent, sections, blocks changed) or `log`.
- Every request is validated by `RequestValidator` on both sides and executed on the server thread through the regular
  `/architect` command engine, so the desktop app can do exactly what chat commands can do, and nothing more.

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
- Building the mod jar needs JDK 25 (a requirement of the current Fabric Loom); on older JDKs `architect-mod` builds in
  headless mode (tests and demo only).
- **Image-to-blueprint is heuristic.** Pixels are not analysed; the scene comes from the file hash, aspect ratio and
  file-name keywords. It will not reproduce a specific picture until an AI/CV provider is plugged in.
- Plans are compiled and previewed synchronously when the command runs (sub-second even at scale 24); exports run in
  the background.
- Queued/running jobs and undo history are kept in memory: a server restart drops unfinished builds (placed blocks stay)
  and undo history (spilled journal files are left in `journals/`). Resume-after-restart is future work.
- Undo restores block states only (no block entities/NBT); generators only emit plain blocks.
- The cinematic camera avoids terrain with a bounded upward raycast only: it can still clip through overhangs, glass or
  very dense builds, and its distance, altitude and speed are clamped rather than path-planned.
- Builder NPCs are cosmetic: they are teleported around the frontier instead of pathfinding, they are not pushable or
  damageable, they carry no items and they do not persist across a restart (they are removed on shutdown).
- Video Studio does not look at the footage: scenes are matched to build progress and time, not to what is on screen.
  The first version cuts, speeds up and fades clips; it has no music, overlays or burned-in captions (an `.srt` file is
  written instead). FFmpeg and Piper are not bundled.
- The picture uploaded from the desktop app is analysed with the same heuristic planner (see above); the preview is a
  top-down region map, not a 3D render.
