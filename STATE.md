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

## Legacy Code Map
_(remove an entry once its class is migrated/deleted)_
- **main/ProjectShoots** — trivial entry point; constructs GameLoop.
- **GameLoop** — refactored: drives `GameStateMachine`, Thread.sleep pacing still used
  (fixed-timestep accumulator is cluster 3). `updateGameLogic()` moved into `PlayingState`.
- **GameSettings** — GOD CLASS: config + game state + players + rounds + font init.
  Holds `InputBridge` (replaced `KeyboardInput`). `checkPlayerInput(InputBridge)` added.
- **GameFrame/GameCanvas/GameScreen/GameCounter/GamePointer** — AWT panels; `GameFrame`
  now exposes public getters for sub-panels. `GameCanvas` registers `InputBridge` as key listener.
- **GameMenu** — refactored: `checkMenuInput(InputBridge)` replaces raw KeyEvent checks.
- **KeyboardInput** — DEAD (no longer referenced; delete with cluster 3 cleanup).
- **Player** — uses `GameAction` fields instead of raw key codes; `checkPlayerInput(InputBridge)`.
- **ColisionCalculator/ColisionPoint** — grid collision + reflection; hard-coded grid bound 24.
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
  `getRenderRoundEnum()` for the legacy renderer. Pause retains internal phase.

## Open Decisions / Backlog
- **Wire config into legacy logic**: clusters 3-6 must read from `GameConfig`
  (grid 24/25, ballCollisionSize, UNIT, radii, round timing). Arc angles + UI layout
  offsets in Drawables/menu → fold into cluster 3 renderer config.
- **Hard-coded font/image paths** (GameSettings ~110, GameFrame ~34): Windows-absolute paths →
  classpath with graceful fallback (cluster 3).
- **Loop in GameLoop ctor + Thread.sleep pacing** → fixed-timestep accumulator +
  interpolation (cluster 3).
- **O(N²) collision** (PlayingState.updateGameLogic, PointList scan) → SpatialCollider (cluster 5).
- **Per-frame allocations in all Drawables** → pooling + cached Stroke/Transform (clusters 3-6).
- **KeyboardInput** — no longer wired as key listener; safe to delete in cluster 3 cleanup.
- **"Colision" misspelling** throughout legacy API — rename to "Collision" when migrating.
- **Strategy-vs-ECS** for cluster 4: plan recommends Strategy-based composition; confirm there.
