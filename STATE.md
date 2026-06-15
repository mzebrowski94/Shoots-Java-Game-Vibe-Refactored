# STATE.md — Living Architecture Reference

> Rewrite sections **in place** as the refactor progresses. Don't turn this into a
> history log. Promote permanent items to "Established Contracts"; delete resolved
> ones. Target: whole file under ~80 lines.

## Package Map
- Root: `pl.mzebrows.shoots`. Legacy in `...game.logic` (+ `.Drawables`) and `...game.main`.
- `...config` — immutable config records + loader (c1). Resources: `game.properties`, `fonts/`, `images/`.
- `...input` — `GameAction` enum + `InputBridge` (c2).
- `...state` — `GameState`, `GameStateMachine`, `PlayingState`, `PausedState`, `GameOverState` (c2);
  `PlayingState` now drives `world.PlayWorld` (c8).
- `...loop` — `FixedTimestep` (c3). `...render` — `Renderer`/`AwtRenderer`/`ImageCache` (c3); render now reads
  `PlayWorld` (c10): `render(RoundEnum, alpha, PlayWorld)`.
- `...entity` — `Entity`, `EntityType`, `MovementStrategy`/`AttackStrategy`/`AiStrategy`, `EntitySpawner`,
  `BounceMovementStrategy` (c4); `AimController`, `DiscAttackStrategy`, `LaserPredictor` (c6).
- `...pool` — `ObjectPool<T>` (c4). `...system` — `MovementSystem`, `CombatSystem` (c4); `DiscSystem` (c6).
- `...spatial` — `SpatialCollider`, `UniformGridCollider`, `TileType`, `CollisionResult` (c5); `MapGenerator` (c7).
- `...score` — `CapturePoint`, `CaptureScoring`, `PlayerScore`, `MatchScorer` (c7).
- `...world` — `PlayWorld` facade + `PlayInput` adapter (c8); `MatchFlow` round/match scorer (c9).

## Legacy Code Map (live game off legacy; legacy KEPT as reference during c12 bug fixing)
- **Live AWT shell (kept)**: `GameFrame`/`GameCanvas`/`GameCounter`/`GameScreen`/`GamePointer`/`GameMenu`
  (all draw `PlayWorld`/`MatchFlow`), `GameLoop`, `GameSettings` (window/fonts/round-timing only),
  `Round` (timing only), `ColorScheme`/`PSConst`/`MenuEnum`/`RoundEnum`.
- **Superseded but RETAINED as reference** (c12 bug-fixing; DELETE in c13): `Player`, `Disc`,
  `PlayerLaser`, `PlayerCursor`, `PlayerBase`, `Block`, `PointField`, `PointList`, `MapMatrix`,
  `ColisionCalculator`, `ColisionPoint`, `LightEffect`, `Drawable`, `DrawableEffect`, `KeyboardInput`.
  Not referenced by the live sim/render. **EXCLUDED FROM BUILD** via `maven-compiler-plugin <excludes>`
  in `pom.xml` (`game/logic/{Player,PointList,MapMatrix,ColisionCalculator,ColisionPoint,KeyboardInput}.java`
  + `game/logic/Drawables/*.java`) — they reference GameSettings members removed in c11, so they compile
  only against the old model. To read them is fine; to re-enable, restore those getters. Delete in c13.

## Established Contracts
- **Config = immutable records, AWT-decoupled.** `GameConfig` aggregates `GridConfig`, `DiscConfig`,
  `CollisionConfig`, `RoundConfig`, `ColorPalette` (+ `RgbColor`). `GameConfigLoader.load()` falls back per-key.
- **`GameAction` + `InputBridge`**: all input as `GameAction`; EDT writes, loop reads via `poll()`.
- **`GameState` + `GameStateMachine`**: `update(InputBridge) → GameState`. `PlayingState` owns the round cycle.
- **`FixedTimestep` + `Renderer`**: loop on its own thread; `Renderer.render(RoundEnum, alpha)` is the only entry.
- **Entity = Strategy composition (c4).** Single mutable pooled `Entity` (primitive pos/vel, `prevX/Y`,
  `EntityType`); behaviour via `MovementStrategy`/`AttackStrategy`/`AiStrategy`. `ObjectPool<T>` array-backed,
  `release()` runs `Entity::reset`. `CombatSystem` is the `EntitySpawner`; `MovementSystem`/`DiscSystem` index-loop.
- **`SpatialCollider` (c5).** `tileAt` (OOB→WALL) + `resolve(Entity)→CollisionResult`; `UniformGridCollider`
  flips `directionX/Y` + increments `bounces` in place. `TileType` replaces ints 0/1/2/3.
- **Player/disc logic = decoupled strategies (c6).** `AimController` (clamped rotation → `currentAngle()`),
  `DiscAttackStrategy` (per-owner cap, `onDiscRetired()` frees a slot), `LaserPredictor` (alloc-free reflection
  walk), `DiscSystem` (snapshot→move→resolve→retire; `DiscEventSink` reports capture hits + retirements).
- **Live wiring = `world.PlayWorld` facade (c8), AWT-free + tested.** Owns the map (`MapGenerator`→
  `UniformGridCollider`), pooled disc list, per-player `AimController`+`DiscAttackStrategy`, `CombatSystem`/
  `DiscSystem`, `CaptureScoring`, `LaserPredictor`. API: `applyInput(player,AimInput,shoot)`, `step()`,
  `fire()`, `predictLaser()`, `resetRound()`, + queries (`discs()`, `tiles()`, `scoring()`, `baseOf`, etc.).
  **Owner tracked by identity** (`IdentityHashMap`) because `CombatSystem.retire` resets the entity (wiping
  `ownerId`) before the sink runs. `PlayInput` maps polled `GameAction`s → per-player intent (held=aim,
  just-pressed=shoot, left wins ties). `PlayingState.updateContinues` = `PlayInput.apply` + `world.step()`;
  round-begin calls `world.resetRound()`.
- **Round/match scoring = `world.MatchFlow` (c9), wired.** Owns one `PlayerScore`/player + `MatchScorer`;
  `PlayWorld.step()` syncs current points from `CaptureScoring`. `PlayingState` ENDS phase calls
  `world.finishRound()` then `world.isMatchOver()`/`resolveMatchWinners()`; restart calls `world.resetMatch()`.
  Replaces legacy `Round.checkRoundWinner`+`GameSettings.checkGameEnd` for all win decisions.
- **Rendering = AWT panels draw from `PlayWorld` (c10).** `Renderer.render(RoundEnum, alpha, PlayWorld)`;
  `GameLoop` holds `PlayingState` so the world is stable across pause. `AwtRenderer` pushes it via
  `GameScreen.setWorld(world,alpha)`/`GamePointer.setWorld(world)`. `GameScreen` draws walls (`tiles()`),
  capture points (`scoring().points()`), discs (`discs()`, interpolated ring), lasers (`predictLaser`,
  reused arrays). `GamePointer`/`GameMenu` read `matchFlow().scoreOf(p)` + `playerColor(p)` (no legacy
  fallback). Helpers: `config()`/`unit()`/`playerColor`. Convention: tile[i][j] -> pixel (i*unit, j*unit).

## Open Decisions / Backlog
- **DONE c9-c11** (134 tests, BUILD SUCCESS): round/win wiring, render migration, legacy model
  decommission. Live game runs entirely on the `world`/`score`/`entity`/`spatial` model.
- **NOW: c12 Playtest Bug Fixing** — user playtests and reports gameplay bugs; legacy classes are
  RETAINED as a behavioural reference (see Legacy Code Map). Deletion deferred to c13. Audio moved to
  `NewFeatures.md` (cluster A). Fixed (136 tests): (1) `DiscSystem.update` crash — iterates discs
  back-to-front so the retire sink can `discs.remove` mid-step. (2) `UniformGridCollider` 45-degree
  corner bug — diagonal-only corners now reflect both axes (no penetration). (3) Tunables in
  `DiscConfig`/`game.properties`: `disc.maxBounces`, `disc.maxPerPlayer` (was 3 in `PlayWorld`), and
  `laser.maxBounces=4` (`GameScreen` sizes laser polyline to `1 + laserMaxBounces`, was 16).
- **BUILD ENV**: `./mvnw` auto-detects the vendored offline toolchain in `tools/` (JDK 26 +
  Maven 3.9 + pre-seeded `.m2`) and builds fully offline IN THE SANDBOX. Always run `./mvnw test`
  from the project root to verify — do NOT assume it can't run. (System `java` is 11; ignore it.)
- Carryover: `GameScreen` interpolates discs with `alpha`. Font paths in `GameSettings.initializeFont()`
  are now relative (`src/main/resources/fonts/...`). Remaining after bug fixing: c13 legacy deletion;
  `NewFeatures.md` A = audio.

## Legacy Logic Coverage Map
- All legacy `game.logic` responsibilities migrated to `world`/`score`/`entity`/`spatial` (c4-c11); legacy retained for c12 ref, deleted c13.
