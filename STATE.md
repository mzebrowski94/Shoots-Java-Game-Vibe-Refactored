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
- `...spatial` — `SpatialCollider` iface, `UniformGridCollider`, `TileType`, `CollisionResult` (cluster 5, done).

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

## Open Decisions / Backlog
- **Wire config into logic**: clusters 4-6 must read from `GameConfig` (grid 24/25,
  ballCollisionSize, UNIT, radii, round timing). `AwtRenderer` still wraps legacy panels as-is;
  fold panel layout offsets + arc angles into a render config when entities migrate.
- **Font paths** (GameSettings ~110): Windows-absolute paths → classpath (image path already
  resolved via `ImageCache`).
- **O(N²) collision**: wall-bounce O(1) via `UniformGridCollider` (c5). Remaining: PointList capture-scan.
- **Per-frame allocations in all Drawables** → pooling + cached Stroke/Transform (clusters 4-6).
- **Render interpolation** — `FixedTimestep.alpha()` is plumbed to `Renderer.render(state, alpha)`
  but unused until entities carry prev/curr state (clusters 4-6).
- **Wire new logic into the loop + delete legacy (cluster 7)**: cluster-6 logic (`AimController`/
  `DiscAttackStrategy`/`LaserPredictor`/`DiscSystem`) is built + tested but NOT yet wired into
  `Player`/`PlayingState`, which still drive legacy `Disc`/`PlayerLaser`/`ColisionCalculator`/
  `ColisionPoint`. Cluster 7: rewire `GameScreen`/`PointList`/`LightEffect`/`PlayerCursor`/`MapMatrix`
  onto `Entity`+`DiscSystem`, then delete the legacy "Colision*"/`Disc`/`PlayerLaser` classes.
