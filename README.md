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

From repository root:

```bash
gradle build
```

This runs compilation and tests (JUnit).

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
- `/architect undo`
- `/architect help`

Example:

```text
/architect shape sphere 6 minecraft:stone
/architect transform upgrade house
```

## Folder / Module Architecture

```text
minecraft1/
├── architect-domain/      # JSON-friendly blueprint/domain + rotation/mirror/validation
├── build-assistant/       # wall/sphere/column + copy/rotate/mirror/move-ready APIs
├── instant-builder/       # template registry: house/castle/temple/village
├── build-transformer/     # deterministic upgrade/style/damage transformations
├── world-generator/       # bounded deterministic fantasy kingdom generator
├── ai-foundation/         # provider interfaces + validated request models + local fallbacks
└── architect-mod/         # Fabric entrypoint, /architect commands, server build queue, undo
```

## Key Technical Notes

- **Server-safe incremental placement queue** using configurable `architect.blocksPerTick` (default `64`, clamped 1..512).
- **Undo** only applies to the latest completed session for each player and restores previously captured block states for those placed positions.
- **Deterministic & bounded** templates/world generation for repeatable tests.
- **No client-only API usage** in build/undo pipeline.
- **No external AI/network required by default**.

## Test Coverage

Included unit tests:

- `architect-domain`: rotation/mirror/validation logic
- `build-transformer`: deterministic transform behavior

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
