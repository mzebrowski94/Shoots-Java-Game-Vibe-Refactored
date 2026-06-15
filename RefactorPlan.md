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

## [x] 6. Migrate Player, Disks & Enemies (decoupled logic only — wiring split to clusters 8-9)
> The AWT-free logic for player aiming, shooting, laser prediction, and the pooled disc
> lifecycle is built and unit-tested here. Wiring it into the live game and deleting the
> superseded legacy classes were split out into clusters 8 (wiring) and 9 (deletion) so each
> stays small and independently verifiable.
- [x] Decoupled aiming + shooting + laser-prediction logic: `AimController` (clamped
      rotation -> shot angle), `DiscAttackStrategy` (`AttackStrategy`, per-owner disc cap),
      and `LaserPredictor` reusing cluster-5 reflection math (`SpatialCollider` +
      `BounceMovementStrategy`), all AWT-free and unit-tested.
- [x] Pooled disc lifecycle on `ObjectPool`+`Entity`+config: `DiscSystem` (move ->
      collide -> retire to pool, `DiscEventSink` for scoring/audio), built on cluster-1
      `DiscConfig` + cluster-4 `CombatSystem`; unit-tested.

## [x] 7. Game-Logic Reconciliation & Cleanup (no behavioural change; pure logic out of legacy)
> Reconciliation pass before final integration: confirm every piece of *game logic* in
> legacy `game.logic` has a home in the refactored model, and extract the remaining
> AWT-free logic (map generation, capture-point + round/match scoring) into tested,
> decoupled classes. Rendering/AWT wiring stays in cluster 8. Audit table lives in STATE.md.
- [x] **Coverage check**: verify each legacy `game.logic` responsibility maps to a
      refactored counterpart or an explicit cluster-8 deletion note; record the
      legacy->new map in `STATE.md` so nothing is silently dropped. Confirmed gaps below.
- [x] **Map generation -> `spatial`**: extract `MapMatrix` block/point-field/base placement
      into an AWT-free `MapGenerator` producing a `TileType[][]` (seedable `Random` for
      deterministic tests); reuse `GridConfig`/`TileType`, drop the unused `ColisionPoint`
      allocations. JUnit tests for border walls, base carve-outs, and block/point fitting.
- [x] **Capture-point scoring -> game-logic module**: extract `PointList`/`PointField`
      capture rules (`chckIfPointFieldErned`, steal-on-equal, cap at 4) into AWT-free,
      record/Strategy logic keyed by tile index, replacing the O(N^2) per-disc scan.
      JUnit tests for neutral->captured, steal, and cap behaviour.
- [x] **Round/match win conditions -> game-logic module**: extract `Round.checkRoundWinner`
      + `GameSettings.checkGameEnd`/`getPlayer` win-logic (and fix the `getPlayer`
      off-by-one) into a tested, AWT-free scorer decoupled from the `GameSettings` GOD class.
      JUnit tests for round winner, match end, and tie handling.

## [x] 8. Render & Input Wiring (drive the live game from the cluster 4-7 model)
> Wire the AWT-free model built in clusters 4-7 into the running game without yet deleting the
> superseded legacy classes (that is cluster 9). Introduce a headless `PlayWorld` facade owning
> the new systems so the whole wired simulation stays unit-testable without a graphics context.
- [x] Build a headless `world.PlayWorld` facade owning per-player `AimController` +
      `DiscAttackStrategy`, the pooled disc list, `UniformGridCollider` (from `MapGenerator`),
      `MovementSystem`/`CombatSystem`/`DiscSystem`, and `CaptureScoring` — exposing
      `applyInput`/`step`/queryable state. JUnit tests for fire-cap, bounce-retire, and capture.
- [x] Wire `PlayingState` onto `PlayWorld` (per-frame `applyInput` + `step`), replacing the
      legacy `Player`/`Disc`/`PlayerLaser`/`ColisionPoint` drive in `updateGameLogic` and the
      raw rotation/shoot input path; keep legacy render panels reading the new state.

## [x] 9. Round/Match Flow Wiring (drive round + win logic from the new scorer)
> AUDIT FINDING (see STATE.md "Open Decisions"): cluster 9 was scoped as pure deletion, but the
> render layer and the round/score-display flow still read the LEGACY model live, so the refactor
> is not actually complete. `score.MatchScorer`/`PlayerScore` were built+tested in c7 but never
> wired in — `PlayingState` still calls the legacy `Round.checkRoundWinner` + `GameSettings.checkGameEnd`
> GOD-class path (with the off-by-one c7 fixed). This cluster wires the new scorer into the live round
> flow so capture results, round winners, and match end come from `PlayWorld`/`MatchScorer`, not legacy.
- [x] Wire `PlayingState` round/end/win flow onto `MatchScorer`/`PlayerScore` fed by
      `PlayWorld.scoring()`: replace `settings.getActualRound().checkRoundWinner()` and
      `settings.checkGameEnd()`/`getPlayer` with the tested c7 scorer (via `world.MatchFlow`);
      expose per-player round + match totals as a queryable contract for the render layer.
      JUnit tests for the wired flow. **[DONE — `world.MatchFlow`, 133 tests green.]**

## [x] 10. Render Migration (draw the live game from PlayWorld, not the legacy model)
> AUDIT FINDING: `AwtRenderer → GameScreen/GamePointer` still render the LEGACY model every frame
> (`Player.getPlayerDiscs()`/`getPlayerLaser()`, `PointList.getPointFields()`, legacy `Player` points),
> while `PlayingState` steps `PlayWorld`. The rendered model is therefore stale — a correctness gap,
> not just dead code. This cluster repoints rendering at the new model so deletion (c11) becomes a
> pure no-op removal.
- [x] Migrate `GameScreen.drawRoundContinues` to render from `PlayWorld` (pooled discs, capture
      points/`CaptureScoring`, predicted laser via `LaserPredictor`, blocks via `MapGenerator`/tiles)
      with no per-frame allocations; drop the `LightEffect`/`ColisionPoint` coupling.
- [x] Migrate `GamePointer` (side score panel) + round timer to read the c9 scorer (`world.MatchFlow`) /
      `PlayWorld` state instead of `GameSettings.getPointList()` and legacy `Player` points; keep the panel layout.

## [x] 11. Legacy Model Decommission (sever the live game from legacy Player/Round/GameSettings state)
> AUDIT FINDING: the c4-10 model fully drives the live sim + render, but the legacy `Player`/`Round`/
> `GameSettings` objects are still constructed and partially read: `GamePointer`/`GameScreen` keep a
> `world==null` legacy fallback, `GameSettings.checkPlayerInput`/`startNewRound` still call
> `Player.checkPlayerInput`/`resetPlayerCursor`, and `Round.savePlayerPoints`/`getPointList` linger.
> Deleting the Drawable/physics legacy (c12) requires gutting `Player` (it builds Disc/PlayerLaser/
> PlayerCursor/PlayerBase/ColisionPoint), so that decoupling is split out here as its own verifiable step.
- [x] Remove the legacy `world==null` fallbacks in `GamePointer`/`GameScreen` so the panels read
      `PlayWorld`/`MatchFlow` unconditionally (panels source player count/colour/name/points from the
      new model; pass the world into the panels even while paused, or guard draw when absent).
- [x] Strip the legacy drive from `GameSettings`/`Round`: drop `checkPlayerInput`→`Player`, the
      `MapMatrix`/`ColisionCalculator`/`PointList` ownership + `startNewRound` legacy reinit, and
      `Round.savePlayerPoints`/`checkRoundWinner`/`getPointList`; keep only round-number/timing state
      the panels still need. JUnit-cover any nontrivial logic that moves.

## [ ] 12. Playtest Bug Fixing (fix gameplay bugs found while playing the migrated game)
> The migrated model now drives the live game, but manual playtesting surfaced gameplay bugs.
> Old legacy classes are intentionally KEPT (reverted from stubs) during this cluster so we can
> compare behaviour against the original implementation while diagnosing. Each reported bug becomes
> a sub-item below with a short repro + root cause + fix; add a regression test where practical.
> Do NOT delete legacy classes until this cluster is complete (deletion is the next cluster).
- [x] **Crash: `IndexOutOfBoundsException` in `DiscSystem.update` after a disc hits a block.**
      Repro: fire discs; when one exhausts its bounce budget on a wall it is retired, and the
      retirement sink (`PlayWorld`) calls `discs.remove(disc)` mid-iteration. Root cause: the loop
      cached `n = discs.size()` and iterated forward, so the post-removal shrink left `discs.get(i)`
      reading past the end. Fix: iterate from the end (`i = size()-1; i >= 0; i--`) so a removal only
      shifts the already-visited tail. Regression test in `DiscSystemTest`.
- [x] **Tunable: max disc bounces + max discs per player externalized to `game.properties`.**
      `disc.maxBounces` already existed and was wired (`DiscConfig`->`CombatSystem`); added a comment.
      `disc.maxPerPlayer` was hard-coded as `MAX_DISCS_PER_PLAYER = 3` in `PlayWorld` — moved into
      `DiscConfig` (validated >= 1), wired through `GameConfigLoader`, defaulted to 3, and now read by
      `PlayWorld` for both the pool size and per-player `DiscAttackStrategy` cap.
- [x] **Bug: discs/laser hitting a block corner at 45 degrees pass into the block and bounce stuck
      inside.** Root cause: `UniformGridCollider.resolve` only handled a corner when the diagonal AND
      both orthogonal neighbours were solid; a clean 45 deg hit on a block corner (only the diagonal
      tile solid) matched no branch and returned `none()`, so the disc kept its diagonal heading into
      the solid tile. Fix: when in the tolerance band on both axes, reflect both axes for an inner
      corner (both sides solid) OR a diagonal-only corner (diagonal solid, both sides empty); else
      fall through to the single-axis branches. Regression test `diagonalOnlyCornerFlipsBothAxes...`.
- [x] **Tunable: aiming-laser preview length externalized as `laser.maxBounces=4`.** Added
      `laserMaxBounces` to `DiscConfig` (validated >= 0), wired via `GameConfigLoader`; `GameScreen`
      lazily sizes its laser polyline buffers to `1 + laserMaxBounces` (was hard-coded 16) so the
      preview shows exactly that many predicted reflections.

## [ ] 13. Legacy Deletion (remove superseded classes once nothing references them)
> Deferred until after playtest bug fixing (c12) so legacy code stays available as a behavioural
> reference while diagnosing gameplay bugs. Pure removal pass; no behavioural change.
- [ ] **Migration gate (do FIRST):** re-verify every legacy `game.logic` responsibility is fully
      migrated to the new model and that NO production/test code references a deletion target except
      the targets themselves. If anything is still wired in, STOP — add a cluster (or sub-items) to
      finish that migration and do it before any deletion. Record the check result in `STATE.md`.
- [ ] Delete the now-unreferenced legacy classes (`Disc`/`ColisionCalculator`/`ColisionPoint`/
      `MapMatrix`/`PointList`/`PointField`/`PlayerLaser`/`PlayerCursor`/`LightEffect`/`KeyboardInput`,
      `Drawable`/`DrawableEffect`/`Block`/`PlayerBase` if dead, `Player`) + any residual dead
      fields/getters on `Round`/`GameSettings`. Verify clean compile + tests; no behavioural change.
- [ ] Final integration pass: wire remaining loose ends, run `./mvnw clean test` end-to-end.
