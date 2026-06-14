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
- `...entity` — `Entity` (pooled, reset()-able), `EntityType`, `MovementStrategy`/`AttackStrategy`/
  `AiStrategy`, `EntitySpawner`, `BounceMovementStrategy` (cluster 4, done).
- `...pool` — `ObjectPool<T>` (cluster 4, done).
- `...system` — `MovementSystem`, `CombatSystem` (cluster 4, done).
- `...spatial` — `SpatialCollider` iface, `UniformGridCollider`, `TileType`, `CollisionResult` (cluster 5, done).

## Legacy Code Map
_(remove an entry once its class is migrated/deleted)_
- **main/ProjectShoots** — trivial entry point; constructs GameLoop.
- **GameLoop** — rewritten: `Runnable` on its own `game-loop` thread, fixed-timestep via
  `FixedTimestep`, renders through `Renderer`. `ProjectShoots` now calls `start()`.
- **GameSettings** — GOD CLASS: config + game state + players + rounds + font init.
  Holds `InputBridge` (replaced `KeyboardInput`). `checkPlayerInput(InputBridge)` added.
- **GameFrame/GameCanvas/GameScreen/GameCounter/GamePointer** — AWT panels; `GameFrame`
  now exposes public getters for sub-panels. `GameCanvas` registers `InputBridge` as key listener.
- **GameMenu** — refactored: `checkMenuInput(InputBridge)` replaces raw KeyEvent checks.
- **KeyboardInput** — DEAD (no longer referenced; delete with cluster 3 cleanup).
- **Player** — uses `GameAction` fields instead of raw key codes; `checkPlayerInput(InputBridge)`.
- **ColisionCalculator/ColisionPoint** — SUPERSEDED by `spatial.UniformGridCollider`; still wired
  into legacy `PlayingState`/`Player`. Delete when cluster 6 migrates discs onto `Entity`+collider.
- **MapMatrix** — int[][] grid builder; allocates unused ColisionPoint.
- **PointList/PointField/Round** — capture-point scoring + round timing; O(N²) scan in PointList.
- **ColorScheme/PSConst/MenuEnum/RoundEnum** — palette holder; const enum (UNIT=36,TABLESIZE=25).
- **Drawables/** — Block, Disc, LightEffect, PlayerBase, PlayerCursor, PlayerLaser, PointField.
  All allocate Color/Stroke/Transform per draw.

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

## Open Decisions / Backlog
- **Wire config into logic**: clusters 4-6 must read from `GameConfig` (grid 24/25,
  ballCollisionSize, UNIT, radii, round timing). `AwtRenderer` still wraps legacy panels as-is;
  fold panel layout offsets + arc angles into a render config when entities migrate.
- **Font paths** (GameSettings ~110): Windows-absolute paths → classpath (image path already
  resolved via `ImageCache`).
- **O(N²) collision**: wall-bounce now O(1) via `UniformGridCollider` (cluster 5 done). Remaining:
  PointList capture-scan + wiring the collider into `PlayingState` replacing `ColisionCalculator` (cluster 6).
- **Per-frame allocations in all Drawables** → pooling + cached Stroke/Transform (clusters 4-6).
- **KeyboardInput** — DEAD, no longer wired; safe to delete.
- **Render interpolation** — `FixedTimestep.alpha()` is plumbed to `Renderer.render(state, alpha)`
  but unused until entities carry prev/curr state (clusters 4-6).
- **Wire cluster-4/5 into the loop** (cluster 6): `PlayingState`/`Player` still use legacy
  `Disc`/misspelled `ColisionCalculator`; migrate onto `Entity` + `ObjectPool` +
  `MovementSystem`/`CombatSystem` + `UniformGridCollider`, then delete the legacy "Colision*" classes.
