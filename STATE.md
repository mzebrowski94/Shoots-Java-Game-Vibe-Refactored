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
- `...loop` — `FixedTimestep` (c3). `...render` — `Renderer`, `AwtRenderer`, `ImageCache` (c3).
- `...entity` — `Entity`, `EntityType`, `MovementStrategy`/`AttackStrategy`/`AiStrategy`, `EntitySpawner`,
  `BounceMovementStrategy` (c4); `AimController`, `DiscAttackStrategy`, `LaserPredictor` (c6).
- `...pool` — `ObjectPool<T>` (c4). `...system` — `MovementSystem`, `CombatSystem` (c4); `DiscSystem` (c6).
- `...spatial` — `SpatialCollider`, `UniformGridCollider`, `TileType`, `CollisionResult` (c5); `MapGenerator` (c7).
- `...score` — `CapturePoint`, `CaptureScoring`, `PlayerScore`, `MatchScorer` (c7).
- `...world` — `PlayWorld` facade + `PlayInput` adapter (c8).

## Legacy Code Map
_(remove an entry once its class is migrated/deleted)_
- **GameSettings** — GOD CLASS (config+state+players+rounds+fonts); still holds round flow used by PlayingState.
- **GameFrame/GameCanvas/GameScreen/GameCounter/GamePointer/GameMenu** — AWT panels; still rendered (delete=c9).
- **Player** — legacy stats/`Disc`/`PlayerLaser`/`PlayerCursor`; no longer drives sim (PlayWorld does); delete=c9.
- **Disc/ColisionCalculator/ColisionPoint/MapMatrix/PointList/PointField** — SUPERSEDED; no live callers; delete=c9.
- **PlayerLaser/PlayerCursor** — rotation paths dead (AimController/LaserPredictor); draw stubs remain; delete=c9.
- **ColorScheme/PSConst/MenuEnum/RoundEnum** — palette/const. **KeyboardInput** — DEAD; delete=c9.

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

## Open Decisions / Backlog
- **ENV (this session)**: the workspace mount silently truncates/corrupts in-place file writes and the
  `target/` dir is lock-broken; verified via a clean copy in `/tmp/build` (`./mvnw test` = BUILD SUCCESS,
  124 tests). Real-tree `./mvnw test` blocked only by the phantom `target/` (delete `target/` Windows-side).
- **Render layer (c9/c10)**: `AwtRenderer`/`GameScreen` still draw legacy `Drawables` (alloc per draw) and
  read legacy `Player`/`Round`; rewire them to read `PlayWorld` state, then delete legacy. `FixedTimestep.alpha()`
  plumbed but unused until panels interpolate `Entity` prev/curr. Font paths in GameSettings still Windows-absolute.

## Legacy Logic Coverage Map (drives cluster 9 deletion)
- Disc physics/bounce/aim/laser/fire -> DONE `world.PlayWorld`(+`Entity`/`DiscSystem`/`UniformGridCollider`/
  `AimController`/`LaserPredictor`/`DiscAttackStrategy`), wired into `PlayingState` (c8); legacy delete = c9.
- `MapMatrix` -> DONE `spatial.MapGenerator` (used by PlayWorld); legacy delete = c9.
- `PointList`/`PointField` -> DONE `score.CaptureScoring`/`CapturePoint` (used by PlayWorld); delete = c9.
- `Round`/`GameSettings` win logic -> DONE `score.MatchScorer`/`PlayerScore` (tested; wire into round flow + delete = c9).
- Rendering (`GameScreen`/`Drawables`/panels) -> stays legacy AWT until c9/c10.
