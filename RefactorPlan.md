# Refactor Plan (Iterative — Ralph Loop)

> **How this file is used**: each top-level item is a *cluster* of 1-2 tightly-coupled
> sub-tasks. Sub-tasks in the same cluster share an interface/contract, so they are
> done together to avoid designing that contract twice.
>
> Before starting work, read `STATE.md` — it has the current package map and any
> contracts already established by earlier clusters. **Reuse those contracts; don't
> redesign them** unless `STATE.md` lists them under "Open Decisions".
>
> Work clusters top to bottom. Complete *whole* sub-items only. A cluster is `[x]`
> only once every sub-item under it is `[x]`.

## [x] 0. Legacy Code Audit (analysis only — no behavioural changes expected)
- [x] Walk the existing source tree and record the package/class layout plus each
      class's responsibility in `STATE.md` under "Legacy Code Map".
- [x] Note concrete correctness/performance issues found (unbounded allocations,
      O(N²) loops, AWT coupling, etc.) as a short backlog in `STATE.md` under
      "Open Decisions" — later clusters resolve these as they migrate the relevant area.

## [x] 1. Project Setup & Configuration
- [x] Update `pom.xml` to Java 25 (enable the finalized language features in the
      compiler plugin) and add: Lombok, SLF4j, JUnit 5, AssertJ, Mockito.
- [x] Create immutable config records (`GameConfig`, `EntityStats`, disk/bullet-type
      definitions, etc.) loaded from an external `.properties`/JSON file; remove
      magic numbers from game logic.

## [x] 2. Input & Game State Machine
- [x] Implement a `GameAction` enum and a thread-safe input bridge between the AWT
      EDT and the game-loop thread (concurrent structures, no raw key codes in game logic).
- [x] Implement the Game State Machine (`MenuState`, `PlayingState`, `PausedState`,
      `GameOverState`) driving the top-level loop, and wire pause/menu `GameAction`s
      into its transitions.

## [x] 3. Game Loop & Renderer
- [x] Implement `GameLoop`: fixed-timestep update with accumulator, max-delta clamp,
      and render-side interpolation between the previous and current state.
- [x] Extract `Renderer`: `BufferStrategy` (active rendering), `setIgnoreRepaint(true)`,
      image cache via `GraphicsConfiguration.createCompatibleImage()`/`VolatileImage`,
      deliberate `RenderingHints`. Include the round-timer (top panel) and
      score/round side panel from `GameRules.md`.

## [x] 4. ECS Core, Object Pooling & DI ⚠️ highest-leverage cluster — its contracts drive clusters 5-7
- [x] Define the mutable, `reset()`-able entity base plus behaviour-injection
      interfaces (`MovementStrategy`, `AttackStrategy`, optionally `AiStrategy`),
      fully decoupled from AWT/`Graphics2D`.
      - *Default direction*: given the project's size (a handful of entity
        archetypes — base/player, disk, block, capture point, future enemies),
        prefer **Strategy-based composition over a full separate-component-array
        ECS** — it still satisfies "composition over inheritance" and "runtime-
        swappable behaviour" with far less machinery. Record the final choice in
        `STATE.md` as a binding contract.
- [x] Implement `ObjectPool<T>` (pre-allocated, array-backed, `acquire()`/`release()`,
      zero `new` in hot paths) and wire it into constructor-injected systems
      (`MovementSystem`, `CombatSystem`).

## [x] 5. Spatial Partitioning & Physics Tests
- [x] Implement a `SpatialCollider` interface with a Uniform Grid (default) or
      QuadTree implementation, replacing O(N²) collision checks.
- [x] JUnit 5 + AssertJ tests for disk-bounce reflection math and grid/collision
      queries, verifying deterministic results without a graphics context.

## [ ] 6. Migrate Player, Disks & Enemies
- [ ] Migrate `Player`/base behaviour onto the new systems: aiming + shooting via
      injected strategies, laser-prediction reusing the reflection math validated
      in cluster 5.
- [ ] Migrate legacy disk/bullet and enemy classes onto `ObjectPool` + the config
      records from cluster 1; delete the old classes once migrated.

## [ ] 7. Audio & Final Integration
- [ ] Implement `SoundManager` with a small `javax.sound.sampled` clip pool so
      overlapping SFX (shots, hits, explosions) don't cut each other off.
- [ ] Final integration pass: wire remaining loose ends, delete dead legacy code,
      run `./mvnw clean test` end-to-end.
