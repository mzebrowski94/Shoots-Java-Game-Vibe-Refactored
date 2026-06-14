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
- `...world` — `PlayWorld` facade + `PlayInput` adapter (c8); `MatchFlow` round/match scorer (c9).

## Legacy Code Map
_(remove an entry once its class is migrated/deleted)_
- **GameSettings/Round** — GOD CLASS + round timing still used; win/score flow SUPERSEDED by `MatchFlow` (c9); delete=c11.
- **GameFrame/GameCanvas/GameScreen/GameCounter/GamePointer/GameMenu** — AWT panels; still rendered; migrate=c10, delete=c11.
- **Player** — legacy stats/`Disc`/`PlayerLaser`/`PlayerCursor`; no longer drives sim (PlayWorld does); delete=c11.
- **Disc/ColisionCalculator/ColisionPoint/MapMatrix/PointList/PointField** — SUPERSEDED; no live callers; delete=c11.
- **PlayerLaser/PlayerCursor** — rotation paths dead (AimController/LaserPredictor); draw stubs remain; delete=c11.
- **ColorScheme/PSConst/MenuEnum/RoundEnum** — palette/const. **KeyboardInput** — DEAD; delete=c11.

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

## Open Decisions / Backlog
- **AUDIT (this session)**: refactor NOT complete — plan re-scoped. Logic is migrated+tested, but never
  wired: (1) live render `AwtRenderer→GameScreen/GamePointer` still draws the LEGACY model every frame
  (`Player.getPlayerDiscs/getPlayerLaser`, `PointList.getPointFields`, legacy `Player` points) while
  `PlayingState` steps `PlayWorld` → rendered model is STALE (correctness gap, not dead code);
  (2) `MatchScorer`/`PlayerScore` (c7, tested) never wired — round/win flow still uses legacy
  `Round.checkRoundWinner`+`GameSettings.checkGameEnd`. Old c9 (pure delete) could not compile.
  Re-scoped: **9 round/match-flow wiring [DONE, 133 tests] → 10 render migration → 11 deletion → 12 audio.**
- **BUILD ENV**: `./mvnw` auto-detects the vendored offline toolchain in `tools/` (JDK 26 +
  Maven 3.9 + pre-seeded `.m2`) and builds fully offline IN THE SANDBOX. Always run `./mvnw test`
  from the project root to verify — do NOT assume it can't run. (System `java` is 11; ignore it.)
- Carryover: `FixedTimestep.alpha()` plumbed but unused until panels interpolate `Entity` prev/curr.
  Font paths in `GameSettings.initializeFont()` still Windows-absolute (externalize in c10/c12).

## Legacy Logic Coverage Map (drives cluster 11 deletion)
- Disc physics/bounce/aim/laser/fire -> DONE `world.PlayWorld`(+`Entity`/`DiscSystem