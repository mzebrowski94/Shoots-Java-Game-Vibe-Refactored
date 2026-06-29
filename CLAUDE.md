# CLAUDE.md

Guidance for AI agents working in this repo. Keep this file lean — it loads into
every request. Describe the *map*, not the territory; read the linked files for depth.

## Project

**Project Shooots** — a top-down, physics-based "bullet hell" shooter for 1–4 players
(human or AI). Players control fixed bases and capture control points by firing disks
that bounce off walls. A years-old Java 8 / AWT game, now fully refactored into a clean,
performant, testable engine.

- **Language:** Java 25 (release 25). Use finalized modern features (records, pattern
  matching for switch/instanceof, record patterns, `var`, unnamed `_`, exhaustive
  switches). **No preview/incubator APIs** (no Structured Concurrency, no Vector API).
- **GUI:** AWT — `Graphics2D` + `BufferStrategy`, active rendering.
- **Build:** Maven (wrapper `./mvnw`). **Libraries:** Lombok, Apache Commons (non-hot-path
  utilities only), SLF4j.
- **Phase:** Refactor is DONE. Current work = **new features** (see `NewFeatures.md`).

## Build & Test

- Verify with **`./mvnw test`** from the project root. That is the only command you need.
- `./mvnw` auto-detects the vendored offline toolchain in `tools/` (JDK 26 + Maven 3.9 +
  pre-seeded `.m2`) and builds **fully offline**. The system `java`/`javac` is JDK 11 and
  there is no system `mvn` — **ignore them**; never conclude "can't build here" from
  `java -version`. Never `curl`/`wget` dependencies (network is firewalled).
- Full details and failure modes: **`tools/AgentWorkflow.md`** (read it before building).

## Architecture (read STATE.md for the authoritative map + contracts)

`refactor/STATE.md` is the **living architecture reference** — the package map and every
established contract live there. Consult it before designing anything, and reuse existing
contracts instead of redesigning them.

Root package `pl.mzebrows.shoots`. High-level layout:

- `config` — immutable config records + `GameConfigLoader` (loads `game.properties` + `graphic.properties`).
- `input` — `GameAction` enum + `InputBridge` (EDT writes, loop reads via `poll()`).
- `state` — `GameStateMachine` + `PlayingState`/`PausedState`/`GameOverState`.
- `loop` / `render` — `FixedTimestep`; `Renderer`/`AwtRenderer`/`ImageCache`. `render.object` —
  per-map-object renderers (`MapObjectRenderer`) driven by `GameScreen`; add a renderer to add a map object.
- `entity` — pooled `Entity` + Strategy interfaces (`MovementStrategy`/`AttackStrategy`/
  `AiStrategy`), `AimController`, `DiscAttackStrategy`, `LaserPredictor`.
- `pool` — `ObjectPool<T>` (array-backed, `reset()` on release).
- `system` — `MovementSystem`/`CombatSystem`/`DiscSystem` (ECS-style, tick over entities).
- `spatial` — `SpatialCollider`/`UniformGridCollider`, `TileType`, `MapGenerator`.
- `score` — `CapturePoint`, `CaptureScoring`, `PlayerScore`, `MatchScorer`.
- `world` — **`PlayWorld` facade** (headless, AWT-free, tested) + `PlayInput` adapter +
  `MatchFlow`. This is the main entry point over the systems/collider/scoring.
- `ui` / `app` — AWT shell (`GameFrame`/`GameCanvas`/`GameMenu`/...) and lifecycle
  (`GameLoop`/`GameSettings`/`Round`). Draws from `PlayWorld`.
- `ai` — AI players: `AiDifficulty`/`AiSkills`/`AiSkillsFactory`, `PlayerAiController`,
  `AiTargeting`, `AiPlayers`. An AI is **just another input source** — it drives the same
  `PlayWorld.applyInput`/`fire` API a human does; nothing special-cases it in the hot loop.

## Conventions (the non-obvious ones that apply to NEW-feature work)

**Architecture**
- Composition over inheritance: lightweight ECS / Strategy, no deep class hierarchies.
- Program to interfaces; hide impls behind them. Runtime-swappable behaviour via interfaces/
  lambdas. Manual constructor DI — no Spring/IoC.
- Immutable **records** for static config/definitions; **mutable pooled classes** (Lombok
  `@Getter/@Setter` + `reset()`) for runtime entities — mutate in place, never replace a
  pooled instance.
- New gameplay logic goes through `PlayWorld` and stays **AWT-free and unit-testable**.
- Small, cohesive modules (SRP). One class per file. Don't over-engineer small additions.

**Performance (hot loop = per-frame entity/disc/collision updates)**
- **Zero `new` in the hot loop** — use object pools; primitive pos/vel fields, no autoboxing,
  no `List<Integer>`/wrappers.
- Classic `for`/enhanced-`for` in the hot loop (no streams, no lambda capture there). Streams
  are fine for setup/business logic.
- Fixed-timestep loop with accumulator + max-delta clamp + render interpolation. Don't break this.
- **Never log inside per-frame/per-entity loops.** SLF4j: `info` for lifecycle, `debug` for
  init/config, `error`+stack trace for exceptions. Resource-load failures log + fall back
  gracefully, never crash.
- Collision via spatial partition behind `SpatialCollider` (uniform grid) — no O(N²) scans.

**Clean code**
- `var` only where the type is obvious. Externalize tunables into config records / `game.properties`
  (logic) or `graphic.properties` (rendering) — **no magic numbers** in game logic or rendering.

**Config / `.properties` (the two files are the single source of truth — NO code defaults)**
- `GameConfigLoader` has **no `defaults()`**: every key it reads is **required**. A missing/unparseable key
  throws `ConfigException`, which `ProjectShoots.main` logs before `System.exit(1)`. Same rule for
  `GameplayLimits`. So adding a config field = add the key to the right `.properties` file too, or the game
  won't start. The only fixed-in-code values are the map geometry constants (`GameConfigLoader.GRID_UNIT` /
  `TABLE_SIZE`).
- **`game.properties` = gameplay logic; `graphic.properties` = rendering/UI** (colours, window tiles, disc
  geometry, menu layout/theme, object styles). The loader merges both, so a logic record may read a key that
  physically lives in `graphic.properties` (e.g. `disc.bigRadius`).
- **Keep comments minimal** in both files — short section headers only; the keys are self-describing.
- **Order** `game.properties` by category: online + run/seed first, then round/match, then physics/gameplay
  (disc, laser, power, disruption, collision, ai) last.
- **Naming = dotted hierarchy** `<group>.<name>` (e.g. `round.timeSeconds`, `disc.speed`). A player-editable
  GAMEPLAY-OPTIONS value carries its caps **inline beneath it** as `<key>.min` / `<key>.max` / `<key>.step`
  (read by `GameplayLimits`). Power-shot speed/bounces are NOT separate options — they scale off the disc via
  `power.speedFactor` / `power.maxBouncesFactor`.
- One-line Javadoc on public classes/methods; minimal inline comments — code explains itself.
  No `@author` tags.
- Tests: JUnit 5 + AssertJ + Mockito for key logic (targeting, scoring, determinism).
  Useful tests, not 100% coverage. Logic must be testable without a graphics context.
- Consistent vocabulary — reuse the existing terms (disc, capture point, base, tile, knob).

## ⚠️ Mount-write gotcha (critical — read before editing files)

This repo is a Windows folder exposed to the Linux sandbox over a mount. Full detail +
every failure mode lives in `tools/AgentWorkflow.md` (owner). The essentials:

1. **You cannot delete/overwrite mount files from Linux** — even files created this session
   ("Operation not permitted"). This is the *reliable* failure mode. Hand deletions to the user
   on Windows; neutralise stray files in place (truncate/make inert) meanwhile.
2. **Writes: trust-but-verify.** The native Edit tool is fine for surgical edits; heredoc for
   full rewrites. Corruption (truncation / NUL bytes) is rare — the build catches it in `.java`
   (compiler errors), so let `./mvnw test` verify code. Manually check NUL only on non-built
   files: `grep -aP '\x00' FILE | wc -l` must be 0. If corrupted: `git show HEAD:PATH > PATH`, redo.
3. **The user owns git** — don't run `git add`/`commit`/`push`; just summarise what changed.
4. "My change had no effect on tests" → suspect stale classes → `./mvnw clean test`.

## Key references

- `GameRules.md` — game mechanics & UI. **Read before touching game logic.**
- `refactor/STATE.md` — authoritative architecture map + established contracts.
- `NewFeatures.md` — current feature backlog (work top-to-bottom, cluster by cluster).
- `OnlineMode.md` — online multiplayer design (host-authoritative deterministic lockstep) + staged backlog.
- `tools/AgentWorkflow.md` — build/sandbox workflow & every known failure mode.
- `tools/OpenRewritePlan.md` — OpenRewrite codemod plan for AI-readiness (dormant in `pom.xml`).
- `refactor/RefactorPrompt.md` — original refactor brief (historical; conventions distilled above).

## Doc ownership (one source of truth per topic)

Each topic has ONE authoritative home — read it there, don't trust a copy elsewhere:

- **Build / sandbox / mount gotcha** → `tools/AgentWorkflow.md`
- **Architecture map + established contracts** → `refactor/STATE.md`
- **Conventions for new-feature work** → this file (`CLAUDE.md`)
- **Game mechanics & UI** → `GameRules.md`
- **Feature backlog** → `NewFeatures.md`
- **Online multiplayer design + protocol** → `OnlineMode.md`
- **Codemods / OpenRewrite** → `tools/OpenRewritePlan.md`
- *Historical (do not follow as live guidance):* `refactor/RefactorPlan.md`, `refactor/RefactorPrompt.md`, `notes/`
