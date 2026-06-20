# IMPORTANT: Refactor phase finished — historical chronicle
> The durable architecture / clean-code / performance conventions from this brief have been
> distilled into `CLAUDE.md` (the live owner — read there for new-feature work). The
> ITERATIVE WORKFLOW / ralph-loop section below is refactor-only and **must not** be followed
> now. Kept for chronicle purposes.

You are an elite Game Development expert and Senior Java Developer specializing in ultra-performant code. Perform a deep refactor of a top-down "bullet hell" shooter, originally written in Java/AWT by a beginner. The result must sustain 60+ FPS, follow strict Clean Code principles, and be architected so new mechanics (enemies, bullet patterns, power-ups) can be added easily.

### GAME RULES AND MECHANICS
- Game rules and mechanics are described in GameRules.md file.

### GAME DESIGN IMPLICATIONS (FOR IMPLEMENTATION)
- The game is **not movement-based**, but **aiming + physics-based**
  Requires:
- efficient handling of many bouncing objects
- deterministic and stable collision handling
- fast spatial queries (for collisions and point detection)
  Gameplay depends on:
- precision
- predictability of physics
- low latency and stable frame timing

### TECH STACK & CONSTRAINTS
- Language: **Java 25 (LTS)**. The "modern" features you'd reach for - pattern matching for switch/instanceof with record patterns, records, unnamed variables (`_`), Stream Gatherers, exhaustive switch expressions - are all finalized as of Java 25. Do not use preview/incubator APIs (e.g. Structured Concurrency, the Vector API) unless I explicitly ask; they need extra compiler flags and can still change.
- GUI: AWT - Graphics2D, BufferStrategy.
- Build: Maven.
- Libraries: Lombok, Apache Commons (for non-hot-path utilities), SLF4j.

### ARCHITECTURE
- Composition over inheritance: a lightweight ECS (entity = id + components, systems operate on components) or Strategy pattern - avoid deep class hierarchies.
- Runtime-swappable behavior: movement/attack/AI logic injected as interfaces or lambdas, so behavior can change mid-game.
- Lightweight manual DI via constructors - no Spring or IoC containers.
- Program to interfaces; hide implementations behind them.
- Explicit Game State Machine (Menu / Playing / Paused / GameOver / ...) driving the top-level loop.
- Data split: **immutable records** for static config (stats, colors, bullet-type definitions); **mutable, reusable classes** (Lombok `@Getter/@Setter` + a `reset()` method) for pooled runtime entities - mutate fields in place, never replace pooled instances.
- Small, cohesive modules (SRP). Scale the architecture to the size of the code I give you - don't turn a 50-line snippet into a 20-class framework.

### CLEAN CODE
- `var` where the type is obvious from context; Lombok to cut boilerplate.
- Streams for setup/business logic; classic `for` / enhanced-`for` in the hot game loop (entity & bullet updates) - no per-frame allocations or lambda-capture overhead there.
- Externalize tunables (HP, speed, damage, colors, spawn timing, bullet patterns) into config records, JSON, or Properties - no magic numbers in game logic.
- One-line Javadoc on public classes/methods; minimal inline comments - the code should explain itself.
- SLF4j: `info` for lifecycle events (start/stop, wave/level changes, boss spawns), `debug` for init/config loading, `error` + stack trace for exceptions. Never log inside per-frame/per-entity loops. Resource-load failures (missing sprite/sound) should log and fall back gracefully, not crash the game.
- Decouple game logic (movement, collisions, state) from AWT rendering so it's unit-testable in JUnit 5 without a graphics context.
- Write test with JUnit, AsserrtJ and Mockito for key logic components (I don't need full test coverage).

### PERFORMANCE & GAME-DEV PRACTICES
- Object pooling: pre-allocated pools for bullets, particles, and enemies - zero `new` in the hot loop.
- Avoid autoboxing in hot paths - primitive fields/arrays for position and velocity, not `List<Integer>` or wrapper types.
- Fixed-timestep loop with an accumulator (decoupled update/render), a max-delta clamp to avoid the "spiral of death", and render-side interpolation between the previous and current state.
- Active rendering only: `setIgnoreRepaint(true)` plus a multi-buffer `BufferStrategy`; never rely on `paint()`/`repaint()`.
- Threading: the game loop runs on its own thread, while AWT input listeners fire on the EDT - synchronize key/mouse state through thread-safe structures (concurrent collections or atomic flags), ideally behind a small `GameAction` enum instead of raw key codes.
- Caching: load images once via `GraphicsConfiguration.createCompatibleImage()` / `VolatileImage`; cache fonts and pre-computed geometry/transforms; set `RenderingHints` deliberately (e.g. antialiasing off if hundreds of bullets tank FPS).
- Collision: replace O(N²) checks with spatial partitioning behind a common interface - a uniform grid (usually the better default when most entities move every frame) or a QuadTree.
- Audio: SFX/music via `javax.sound.sampled` with a small clip pool so overlapping sounds (shots, hits, explosions) don't stutter or get dropped.

### Other:
- Remove @author from Javadoc

### ITERATIVE WORKFLOW
This refactor runs cluster-by-cluster via a ralph loop (see `RefactorPlan.md` and `STATE.md`), not as a single one-shot rewrite. Every rule above (architecture, clean code, performance, ...) applies to every cluster. Per-cluster instructions - which cluster to pick, how to verify, how to record progress - live in `ralph-once.sh`, not here.

When working on a cluster:
- Read `STATE.md` first; reuse contracts it already establishes instead of redesigning them.
- Keep package structure / file paths consistent with `STATE.md`'s package map; one class per file, file path as a header comment.
- Note in your commit message anything you removed or changed because it was actively hurting performance or correctness.