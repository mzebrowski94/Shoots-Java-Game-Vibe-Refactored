# STATE.md — Living Architecture Reference

> Rewrite sections **in place** as the refactor progresses. Don't turn this into a
> history log. Promote permanent items to "Established Contracts"; delete resolved
> ones. Target: whole file under ~80 lines.

## Package Map
- Root: `pl.mzebrows.shoots`. Legacy in `...game.logic` (+ `.Drawables`) and `...game.main`
  (entry `main/ProjectShoots`).
- `...config` — immutable config records + loader (cluster 1, done). Resources:
  `src/main/resources/game.properties` (tunables), `fonts/`, `images/`.
- `...input` — `GameAction` enum + `InputBridge` (cluster 2, done).
- `...state` — `GameState` interface, `GameStateMachine`, `PlayingState`, `PausedState`,
  `GameOverState` (cluster 2, done).
- `...loop` — `FixedTimestep` accumulator (cluster 3, done).
- `...render` — `Renderer` iface, `AwtRenderer`, `ImageCache` (cluster 3, done).
- `...entity` — `Entity`, `EntityType`, `MovementStrategy`/`AttackStrategy`/`AiStrategy`, `EntitySpawner`,
  `BounceMovementStrategy` (c4); `AimController`, `DiscAttackStrategy`, `LaserPredictor` (cluster 6).
- `...pool` — `ObjectPool<T>` (cluster 4, done).
- `...system` — `MovementSystem`, `CombatSystem` (c4); `DiscSystem` (cluster 6).
- `...spatial` — `SpatialCollider`, `UniformGridCollider`, `TileType`, `CollisionResult` (c5); `MapGenerator` (cluster 7).
- `...score` — `CapturePoint`, `CaptureScoring`, `PlayerScore`, `MatchScorer` (cluster 7).

## Legacy Code Map
_(remove an entry once its class is migrated/deleted)_
- **GameLoop/ProjectShoots** — rewritten: `Runnable` on `game-loop` thread, fixed-timestep, `Renderer`.
- **GameSettings** — GOD CLASS (config+state+players+rounds+fonts); holds `InputBridge`, `checkPlayerInput`.
- **GameFrame/GameCanvas/GameScreen/GameCounter/GamePointer/GameMenu** — AWT panels; bridge-driven input.
- **Player** — `GameAction` fields; still drives legacy `Disc`/`PlayerLaser`/`PlayerCursor` (cluster 7).
- **ColisionCalculator/ColisionPoint** — SUPERSEDED by `UniformGridCollider`; delete in cluster 7.
- **MapMatrix/PointList/PointField/Round** — int grid + capture scoring/round timing (O(N²) PointList).
- **ColorScheme/PSConst/MenuEnum/RoundEnum** — palette/const (UNIT=36,TABLESIZE=25).
- **Drawables/** — Block, Disc, LightEffect, PlayerBase, PlayerCursor, PlayerLaser, PointField; alloc per draw.
- **KeyboardInput** — DEAD; safe to delete.

## Established Contracts
- **Config = immutable records, AWT-decoupled.** `GameConfig` aggregates `GridConfig`,
  `DiscConfig`, `CollisionConfig`, `RoundConfig`, `ColorPalette` (+ `RgbColor`). Compact
  ctors validate ranges. Colours via `RgbColor.toAwt()` at render boundary only.
- **`GameConfigLoader`** (static): `load()` / `load(name)` / `fromProperties(Properties)`
  / `defaults()`. Missing/invalid keys fall back per-key to defaults.
- **`GameAction` + `InputBridge`**: all player/menu input expressed as `GameAction` enums.
  `InputBridge` is the single `KeyListener`; EDT writes, game loop reads via `poll()`.
  Keys map to `EnumSet<GameAction>` so one physical key can trigger multiple actions.
- **`GameState` + `GameStateMachine`**: `update(InputBridge) → GameState` (null = quit).
  `PlayingState` owns the ROUND_BEGIN/CONTINUES/ENDS sub-cycle and exposes
  `getRenderRoundEnum()` for the renderer. Pause retains internal phase.
- **`FixedTimestep` + `Renderer`**: loop runs on its own `game-loop` thread. `FixedTimestep`
  (AWT-free) does `accumulate(dt)` → `consumeStep()` loop → `alpha()` interp factor; built via
  `ofRate(ups, maxCatchUp)`. `Renderer.render(RoundEnum, alpha)` is the only render entry; AWT
  impl `AwtRenderer` owns active rendering. Images load once via classpath `ImageCache`.
- **Entity = Strategy composition (cluster 4).** Single mutable `Entity` (Lombok
  `@Getter/@Setter` + `reset()`, primitive pos/vel, `prevX/Y` for interp, `EntityType` tag) — NOT a
  component-array ECS. Behaviour injected via `MovementStrategy`/`AttackStrategy`/`AiStrategy`.
  `ObjectPool<T>` (array-backed, `acquire()`/`release()`, no `new` in hot loop). `CombatSystem`
  implements `EntitySpawner`, spawns/retires discs from a pool using `DiscConfig`; `MovementSystem`
  dispatches per-entity strategies via index loop + `snapshot()`.
- **`SpatialCollider` (cluster 5).** Broad-phase iface: `tileAt(x,y)` (OOB→WALL) + `resolve(Entity)
  →CollisionResult`. `UniformGridCollider` is the default — O(1) tile lookup over `TileType[][]`
  (`fromLegacyMatrix` adapts the int grid). On a wall/border hit it flips the entity's
  `directionX/Y` in place + increments `bounces` (the reflection contract `BounceMovementStrategy`
  expects); replaces legacy stateful `colisionType` hysteresis with stateless travel-direction
  neighbour tests. `TileType` (EMPTY/WALL/CAPTURE_POINT/PLAYER_BASE) replaces magic ints 0/1/2/3.
- **Player/disc logic = decoupled strategies (cluster 6).** `AimController` holds clamped cursor
  rotation and yields `currentAngle()` (base dir + rotation). `DiscAttackStrategy` (`AttackStrategy`)
  fires via `EntitySpawner.spawnDisc` up to a per-owner cap; `onDiscRetired()` frees a slot.
  `LaserPredictor` walks a scratch `Entity` through `SpatialCollider`+`MovementStrategy`, filling
  caller-supplied int[] with reflection points (no alloc). `DiscSystem` runs the per-step disc
  lifecycle (snapshot→move→`collider.resolve`→retire spent via `CombatSystem`), reporting
  capture-point hits + retirements through `DiscSystem.DiscEventSink` (NO_OP for headless). All AWT-free.
- **Game-logic reconciliation (cluster 7), all AWT-free + tested.** `MapGenerator(GridConfig,Random)`
  -> seedable `TileType[][]` (border/blocks/capture-points/bases) feeding `UniformGridCollider`,
  replacing `MapMatrix`. `CaptureScoring` indexes `CapturePoint`s by packed tile key; `resolveHit`
  is O(1) (capture/steal-on-equal/cap-4 in `CapturePoint.tryCapture`), replacing `PointList`'s O(N^2)
  scan. `MatchScorer(RoundConfig)` over `PlayerScore`s: `finishRound` (bank+award, ties award all),
  `isMatchOver`, `resolveMatchWinners` (most rounds, points tiebreak) — off the `GameSettings` GOD class.

## Open Decisions / Backlog
- **Render layer (cluster 8)**: `AwtRenderer` still wraps legacy panels; `Drawables` alloc per draw;
  `FixedTimestep.alpha()` is plumbed but unused until entities carry prev/curr state. Fold panel
  offsets/arc angles into a render config and pool cached Stroke/Transform during the rewire.
- **Font paths** (GameSettings ~110): Windows-absolute → classpath (images already via `ImageCache`).
- **Cluster 8 (wiring+deletion)**: c6/c7 logic is built+tested but not wired; `Player`/`PlayingState`
  still drive legacy `Disc`/`PlayerLaser`/`Colision*`. Rewire panels onto `Entity`/systems, then
  delete legacy `Colision*`/`Disc`/`PlayerLaser`/`MapMatrix`/`PointList` (see Coverage Map below).

## Legacy Logic Coverage Map (drives cluster 8 wiring/deletion)
- Disc physics / wall collision / aiming / laser / firing -> `Entity`+`BounceMovementStrategy`+
  `DiscSystem` / `UniformGridCollider` / `AimController` / `LaserPredictor` / `DiscAttackStrategy`
  (built+tested in c6; wiring+legacy deletion = cluster 8).
- `MapMatrix` -> DONE `spatial.MapGenerator` (c7, tested); legacy delete = c8.
- `PointList`/`PointField` rules -> DONE `score.CaptureScoring`/`CapturePoint` (c7, tested); delete = c8.
- `Round`/`GameSettings` win logic -> DONE `score.MatchScorer`/`PlayerScore` (c7, tested); delete = c8.
- Rendering (`GameScreen`/`Drawables`/panels) -> stays legacy AWT until cluster 8.
