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
- [x] Decoupled aiming + shooting + laser-prediction logic: `AimController` (clamped
      rotation -> shot angle), `DiscAttackStrategy` (`AttackStrategy`, per-owner disc cap),
      and `LaserPredictor` reusing cluster-5 reflection math (`SpatialCollider` +
      `BounceMovementStrategy`), all AWT-free and unit-tested.
- [ ] Wire the above into `Player`/`PlayingState`, replacing legacy
      `PlayerCursor`/`PlayerLaser` rotation + raw input handling (render layer). [cluster 8]
- [x] Pooled disc lifecycle on `ObjectPool`+`Entity`+config: `DiscSystem` (move ->
      collide -> retire to pool, `DiscEventSink` for scoring/audio), built on cluster-1
      `DiscConfig` + cluster-4 `CombatSystem`; unit-tested.
- [ ] Delete legacy `Disc`/`ColisionCalculator`/`ColisionPoint` + rewire
      `GameScreen`/`PointList`/`LightEffect`/`PlayerCursor`/`MapMatrix`. [cluster 8]

## [ ] 7. Game-Logic Reconciliation & Cleanup (no behavioural change; pure logic out of legacy)
> Reconciliation pass before final integration: confirm every piece of *game logic* in
> legacy `game.logic` has a home in the refactored model, and extract the remaining
> AWT-free logic (map generation, capture-point + round/match scoring) into tested,
> decoupled classes. Rendering/AWT wiring stays in cluster 8. Audit table lives in STATE.md.
- [ ] **Coverage check**: verify each legacy `game.logic` responsibility maps to a
      refactored counterpart or an explicit cluster-8 deletion note; record the
      legacy->new map in `STATE.md` so nothing is silently dropped. Confirmed gaps below.
- [ ] **Map generation -> `spatial`**: extract `MapMatrix` block/point-field/base placement
      into an AWT-free `MapGenerator` producing a `TileType[][]` (seedable `Random` for
      deterministic tests); reuse `GridConfig`/`TileType`, drop the unused `ColisionPoint`
      allocations. JUnit tests for border walls, base carve-outs, and block/point fitting.
- [ ] **Capture-point scoring -> game-logic module**: extract `PointList`/`PointField`
      capture rules (`chckIfPointFieldErned`, steal-on-equal, cap at 4) into AWT-free,
      record/Strategy logic keyed by tile index, replacing the O(N^2) per-disc scan.
      JUnit tests for neutral->captured, steal, and cap behaviour.
- [ ] **Round/match win conditions -> game-logic module**: extract `Round.checkRoundWinner`
      + `GameSettings.checkGameEnd`/`getPlayer` win-logic (and fix the `getPlayer`
      off-by-one) into a tested, AWT-free scorer decoupled from the `GameSettings` GOD class.
      JUnit tests for round winner, match end, and tie handling.

## [ ] 8. Audio & Final Integration
- [ ] Implement `SoundManager` with a small `javax.sound.sampled` clip pool so
      overlapping SFX (shots, hits, explosions) don't cut each other off.
- [ ] Final integration pass: wire remaining loose ends, delete dead legacy code,
      run `./mvnw clean test` end-to-end.
