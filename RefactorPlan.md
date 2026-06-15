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

## [x] 12. Playtest Bug Fixing (fix gameplay bugs found while playing the migrated game)
> The migrated model now drives the live game, but manual playtesting surfaced gameplay bugs.
> Old legacy classes are intentionally KEPT (reverted from stubs) during this cluster so we can
> compare behaviour against the original implementation while diagnosing. Each reported bug becomes
> a sub-item below with a short repro + root cause + fix; add a regression test where practical.
> Do NOT delete legacy classes until this cluster is complete (deletion is the next cluster).
- [x] **Crash after disc hits a block.** `DiscSystem.update` cached list size and iterated forward; the retire sink removed the disc mid-loop -> `IndexOutOfBoundsException`. Fix: iterate back-to-front.
- [x] **Tunables `disc.maxBounces` / `disc.maxPerPlayer`.** Moved hard-coded per-player cap (3) into `DiscConfig`/`game.properties`; both now drive `PlayWorld` pool size + fire cap.
- [x] **Discs/laser stuck inside a block at 45 deg.** `UniformGridCollider` ignored diagonal-only corners; now reflects both axes for them.
- [x] **Tunable `laser.maxBounces=4`.** Laser preview length is config-driven; `GameScreen` sizes its polyline to `1 + laserMaxBounces` (was hard-coded 16).
- [x] **One disc maxed a capture point + discs didn't vanish on capture.** `CapturePoint.tryCapture` now adds one level per hit (no bounce-count jump); a consumed hit retires the disc, an ineffective one passes through.
- [x] **Player bases: invisible, blocks spawning on them, wrong flank geometry.** `GameScreen` never drew bases -> added `drawBases` (two counter-rotating dashed rings in player colour, per legacy `PlayerBase`). `MapGenerator` now carves each base's box + forward firing lane (two passes so P1/P2 and P3/P4 shared lanes don't clear each other) and places a flanking wall exactly one tile to each side of the base centre. Tests in `MapGeneratorTest`.
- [x] **Player bases (round 2, vs real refactored-build screenshot).** Base graphic and disc spawn were in different places: `BasePlacement.pixelX/pixelY` were swapped vs the gameplay convention (entity X = first tile index = screen X), so discs spawned at the wrong tile. Fixed `locateBases` to spawn at the base tile CENTRE (`i*unit+unit/2`, `j*unit+unit/2`). Enlarged base rings to 25/15 px (legacy size). Moved flanking blocks to TWO tiles aside (`FLANK_OFFSET=2`). The 5x5 area around each base is cleared so the shooting area is free. Tests: `baseSpawnPixelIsTheCentreOfTheBaseTile`, `tilesImmediatelyAroundEachBaseAreClearOfBlocks`, updated flank test.
- [x] **Player bases (round 3): too far from border, P3/P4 never spawned, colour check.** Moved all four base centres to 1 tile from their border (`{12,23}`,`{12,1}`,`{1,12}`,`{23,12}`) so the flanking blocks sit against the border. P3/P4 didn't work because `PlayWorld` was built once from `game.properties` (playerNumber=2), ignoring the menu choice — `PlayingState.doRestartGame` now rebuilds the world from `settings.getPlayerNumber()` (1-4) and re-pushes it to the panels. Confirmed `color.player1..4` are read from `game.properties` per player (was a symptom of P3/P4 not spawning). Tests: `threeAndFourPlayerMapsPlaceTheExtraBases`, `fourPlayerWorldHasFourDistinctBases`, all-4-colour check.
- [x] **Firing path no longer carved across the whole map.** `MapGenerator.carveBaseArea` cleared a 3-wide lane from each base to the far wall, which removed the blocks needed for indirect bounce shots (the core mechanic). Now it only clears the small spawn box (+-2 tiles) around the base; the path ahead keeps its blocks. Test renamed `onlyTheSpawnAreaAheadOfEachBaseIsCleared`.
- [x] **Block-hit flash animation (ported from legacy `LightEffect`).** When a disc bounces off a wall/block, the block briefly flashes: a short grey ramp-up then the disc owner's colour fading out (matches the legacy two-phase effect seen in `preview/*.gif`). `DiscEventSink.onWallHit` reports the bounce; `world.BlockHitEffect` (AWT-free, reusable) holds the per-tile animation state; `PlayWorld` spawns/advances/prunes them in `step()` (retriggers in place on repeat hits) and exposes `blockHits()`; `GameScreen.drawBlockHits` overlays the flash on the wall tiles. Tests: `BlockHitEffectTest`, `wallBounceRegistersABlockHitFlash`.
- [x] **Capturing a controlled point should erode the owner first, not steal on the first hit.** A foreign disc hitting an owned capture point used to flip ownership immediately (reset to level 1 under the attacker). Per the game rules (tug-of-war) it must instead DECREMENT the owner's level by one per hit, and only the hit that would drop the owner below level 1 retakes the point for the attacker at level 1. Fixed `CapturePoint.tryCapture`; the disc-consume contract is unchanged (any effective erode/level/capture hit still returns true). Tests in `CaptureScoringTest` cover erode-before-take, the emptying hit, and eroding a maxed point.
- [x] **Round HUD panels bled through the menu background.** The menu IS the `ROUND_PAUSED` render state, and all three panels filled only the near-transparent `color.menuStandard` (alpha 10) tint in that state, so the stale `BufferStrategy` back buffer (round timer bar + score panels) showed through. Fix: each panel's `drawRoundPaused` now clears to an opaque `backgroundColor` first, then applies the menu tint (`GameScreen`/`GameCounter`/`GamePointer`).
- [x] **Window overflowed / was truncated on small (laptop) screens.** `GameFrame` packed to its fixed ~1044x1044 size and never positioned against the usable screen. Added `fitAndCenterToScreen()`: centres within the usable desktop (screen bounds minus taskbar insets) and, when the window is larger than the screen, anchors the top-left to the usable origin so the top counter stays visible. Also `setResizable(false)` (the active-rendering surface must not be resized).
- [x] **Moving the window froze the game (needed a restart).** The panels cached one `Graphics`/`g2d` from `getDrawGraphics()` at init and reused it every frame; a window move recreates the surface and invalidates that context, so rendering silently stopped. Fix: `drawUpdate` in all three panels now re-acquires the draw graphics each frame and loops on `contentsRestored()`/`contentsLost()` (the standard active-rendering pattern), disposing per frame. `GameCounter` re-applies its title font each frame since the init-time font no longer persists.
- [x] **Menu transparency refined per state.** The first menu-bleed fix made ALL menu backgrounds opaque, which lost the nice translucent look on the pause and win-score screens (where you should see the frozen game through the menu). Now each panel checks whether a game exists behind the menu (`world != null && (actualRoundNumber > 0 || gameEnd)`): on pause/win it redraws the frozen field/counter/stats and overlays the alpha-10 tint (translucent); on a fresh start (nothing behind) it clears to an opaque background. `GameScreen`/`GameCounter`/`GamePointer`.
- [x] **Menu text legibility (UX pass, keeping the purple/green palette).** Menu/score text was hard to read over the (now more opaque) frozen game. Added three contrast aids in `GameMenu` that DO NOT change any text colour: a dark rounded translucent backdrop panel behind the menu block, a drop shadow under every glyph (`shadowString`), and a soft highlight bar behind the selected row. Applied across the main menu, the selection highlight, and the game-end/winner screen (the animated green winner colour is preserved, just shadowed).
- [x] **Menu backdrop / scoreboard alignment + `ColorScheme` refactor.** The menu backdrop and selection bar were centred on the screen while the menu text is left-anchored at `textPostion`, so they drifted left of the text; the game-end scoreboard used screen-absolute coords and fell outside the panel. Backdrop, highlight bar, and scoreboard are now all anchored to `textPostion` (panel width measured from the widest row via `FontMetrics`), so they line up. Also moved the WINNER colour pulse out of the per-player loop (it advanced N times/frame) and dropped unused fields. `ColorScheme` refactored to convention: immutable `final` fields + Lombok `@Getter`, no setters, no `@author` (127 -> 32 lines; getter API unchanged).
- [x] **Game-end backdrop width + `GameSettings` refactor.** The menu backdrop was sized to the widest menu row, so on the scoreboard screen the player columns overflowed it. `panelWidth` is now game-end aware: it widens to contain all four player columns (always sized for `MAX_PLAYERS=4` so the layout is stable regardless of count), with the scoreboard column offsets/step promoted to shared constants. `GameSettings` refactored to convention: Lombok `@Getter`/`@Setter` (read-only `@Setter(NONE)` on `actualRound`/`previousRound`), `@Slf4j` logging replacing `printStackTrace`, no `@author` (244 -> ~112 lines; getter/setter API unchanged, including the `getDEFAULT_*` window-size accessors).
- [x] **Menu backdrop centring + 4-player scoreboard fit.** The panel was anchored to the left-aligned menu text and the scoreboard used fixed left-to-right column offsets, so the backdrop drifted off-centre and P3/P4 columns ran off the right edge (P4 missing for 4 players). Reworked the geometry: the panel is now centred on the menu block and clamped on-screen (`panelLeft`/`panelWidth` with a screen margin), and `drawGameEnd` distributes up to `MAX_PLAYERS` score columns evenly across the panel's inner width, each cell centred, with the WINNER title centred over the panel too. All four columns now fit inside the centred backdrop.

## [x] 13. Legacy Deletion (remove superseded classes once nothing references them)
> Deferred until after playtest bug fixing (c12) so legacy code stays available as a behavioural
> reference while diagnosing gameplay bugs. Pure removal pass; no behavioural change.
- [x] **Migration gate (do FIRST):** re-verify every legacy `game.logic` responsibility is fully
      migrated to the new model and that NO production/test code references a deletion target except
      the targets themselves. If anything is still wired in, STOP — add a cluster (or sub-items) to
      finish that migration and do it before any deletion. Record the check result in `STATE.md`.
      **[DONE — gate PASSED: only Javadoc `{@code}` mentions remain; 153 tests green. Recorded in STATE.md.]**
- [x] Delete the now-unreferenced legacy classes (`Disc`/`ColisionCalculator`/`ColisionPoint`/
      `MapMatrix`/`PointList`/`PointField`/`PlayerLaser`/`PlayerCursor`/`LightEffect`/`KeyboardInput`,
      `Drawable`/`DrawableEffect`/`Block`/`PlayerBase`, `Player`) + residual dead fields/getters on
      `Round`/`GameSettings`. **[DONE — user deleted all 15 files on Windows; `pom.xml` `<excludes>`
      removed; 153 tests green, no behavioural change.]**
- [x] Final integration pass: wire remaining loose ends, run `./mvnw clean test` end-to-end. **[DONE — 153 tests green.]**

## [x] 14. Package Restructure (retire `game.logic` grab-bag)
> The legacy `game.logic` package is now just the live AWT shell + app wiring mixed together. Split it
> into cohesive packages so structure matches responsibility. Do this BEFORE the Javadoc/var passes so
> those land on final locations. Pure move + import/package-line updates; no behavioural change.
- [x] Create `...ui` and move the AWT panels/frame into it: `GameFrame`, `GameCanvas`, `GameCounter`,
      `GamePointer`, `GameScreen`, `GameMenu`. Update package lines + all imports + the file-path header
      comments. Verify clean compile + 153 tests. **[DONE — 153 tests green.]**
- [x] Create `...app` and move app/loop/config-state classes into it: `GameLoop`, `GameSettings`,
      `Round`; UI enums/palette (`MenuEnum`, `RoundEnum`, `PSConst`, `ColorScheme`) placed in `...ui`
      (recorded in STATE.md). `game.logic` emptied to inert stubs (Windows-locked; user deletes the
      folder on Windows). Verify clean compile + tests. **[DONE — 153 tests green.]**

## [x] 15. Naming Audit (class + variable names to convention)
> Sweep the WHOLE codebase for names that don't follow Java/Clean-Code convention. No pattern-driven
> renames here (that's cluster 16c) — just plain convention fixes. Behaviour unchanged.
- [x] Fix non-conventional CLASS names that are abbreviations/unclear (e.g. `PSConst` -> a clear name
      like `GameUnits`/`GameDimensions`). Update all references; record old->new in STATE.md.
      **[DONE — `PSConst` -> `GameDimensions`; enum const `TABLESIZE` -> `TABLE_SIZE`.]**
- [x] Fix non-conventional VARIABLE/field/constant names: SCREAMING_SNAKE used on non-static fields,
      remaining abbreviations, and any misspellings missed earlier. Static finals stay UPPER_SNAKE;
      instance fields/locals become lowerCamelCase. Verify clean compile + tests.
      **[DONE — `GameSettings.DEFAULT_*`/`SIZE`/`UNIT` instance fields -> camelCase (Lombok getters
      now `getDefaultWidth()` etc.); `gS` -> `gameSettings` (100 refs); `gd2` -> `g2d`; `var` applied
      to obvious-type locals. 153 tests green.]**
