# STATE.md — Living Architecture Reference

> Rewrite sections **in place** as the refactor progresses. Don't turn this into a
> history log. Promote permanent items to "Established Contracts"; delete resolved
> ones. Target: whole file under ~120 lines.

## Package Map
- Root: `pl.mzebrows.shoots`. Entry point in `...game.main` (`ProjectShoots`).
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
- `...world` — `PlayWorld` facade + `PlayInput` adapter (c8); `MatchFlow` round/match scorer (c9); `BlockHitEffect` (c12).
- `...ui` (c14) — AWT shell: `GameFrame`, `GameCanvas`, `GameCounter`, `GamePointer`, `GameScreen`, `GameMenu`,
  + UI enums/palette `MenuEnum`/`RoundEnum`/`PSConst`/`ColorScheme`.
- `...app` (c14) — lifecycle/loop/state: `GameLoop`, `GameSettings`, `Round`.

## Kept AWT Shell (c13 standards pass; c14 repackaged)
- Live shell in `...ui`/`...app` draws `PlayWorld`/`MatchFlow`. `GameSettings` = window/fonts/round-timing
  only; `Round` = timing only (`@Getter`/`@Setter`). c13: removed `@author`, fixed misspellings, Lombok-ified
  getters, dropped dead `Round.playerPointsList` + `GameCounter.getRoundTimeInSeconds`. All legacy
  `game.logic`/`Drawables` classes DELETED (c13).
- **c14 leftover:** the 13 moved files left inert Windows-locked stubs in `game/logic/` (package line only).
  **ACTION FOR USER (Windows):** delete the whole `src/main/java/pl/mzebrows/shoots/game/logic/` folder.

## Design-Pattern Audit (c16)
- **Strategy**: `MovementStrategy`/`AttackStrategy`/`AiStrategy` (+ impls `BounceMovementStrategy`,
  `DiscAttackStrategy`) — swappable behaviour behind an interface.
- **State**: `GameState` (+ `PlayingState`/`PausedState`/`GameOverState`) driven by `GameStateMachine`.
- **Object Pool**: `ObjectPool<T>` — pre-allocated reusable instances, no per-frame `new`.
- **Facade**: `PlayWorld` — one headless entry point over the systems/collider/scoring.
- **Adapter**: `PlayInput` (GameAction->intent), `AwtRenderer` (model->AWT panels).
- **Game Loop**: `GameLoop` + `FixedTimestep` — fixed-timestep update/render with interpolation.
- **Spatial Partition**: `UniformGridCollider` behind `SpatialCollider`.
- **Observer (callback/event-sink)**: `DiscSystem.DiscEventSink` — decoupled hit/score/audio events.
- **Factory / Spawner**: `EntitySpawner` (impl `CombatSystem`); `MapGenerator` builds the tile grid.
- **System (ECS-style)**: `MovementSystem`/`CombatSystem`/`DiscSystem` operate over entities each tick.
- Already-clear (no rename): `AimController`, `LaserPredictor`, `MapGenerator`, `GameConfigLoader`,
  `ImageCache` (Cache), `MatchFlow`/`MatchScorer`. Renames in c16c: none needed — names already encode role.

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
  walk), `DiscSystem` (snapshot→move→resolve→retire; `DiscEventSink.onCapturePointHit` returns whether the
  hit changed the point — a consumed hit retires the disc, an ineffective one passes through).
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
- **DONE c1-c17.** Full migration + cleanup complete. Live game runs entirely on the
  `world`/`score`/`entity`/`spatial`/`ui`/`app` model; legacy deleted.
  - c15: `PSConst`->`GameDimensions` (`TABLESIZE`->`TABLE_SIZE`); `GameSettings` SCREAMING_SNAKE
    instance fields -> camelCase; `gS`->`gameSettings`, `gd2`->`g2d`; `var` on obvious-type locals.
  - c16: design-pattern audit table (above).
  - c17: 153 tests green throughout.
- **c18 (playtest bug):** menu round-limit was ignored — every match ended at 2 rounds. The menu
  set `GameSettings.roundLimit`, but match-end reads `RoundConfig.roundLimit()` (loaded from
  `game.properties`). Fix: `PlayingState.rebuildWorldForSelectedPlayers` overlays the selected
  round limit + round time onto `RoundConfig` (`applySelectedRoundSettings`) before building
  `PlayWorld`. Tests: `PlayingStateRoundLimitTest`.
- **USER ACTION (Windows, leftover stubs):** delete the inert Windows-locked stub files I could not
  remove from the sandbox: the whole `src/main/java/pl/mzebrows/shoots/game/logic/` folder and
  `ui/PSConst.java`. Build is green with them present (they hold only a package line).
- **BUILD ENV**: `./mvnw` auto-detects the vendored offline toolchain in `tools/` (JDK 26 + Maven 3.9 +
  pre-seeded `.m2`); builds fully offline in the sandbox. Verify with `./mvnw test` from project root.
  **Mount gotcha:** editor/`rm` on existing files is Windows-locked; writes can append NUL bytes — write
  via bash heredoc/python, verify, and hand file deletions to the user on Windows.

## Legacy Logic Coverage Map
- All legacy `game.logic` responsibilities migrated to `world`/`score`/`entity`/`spatial` (c4-c11); legacy retained for c12 ref, deleted c13.
