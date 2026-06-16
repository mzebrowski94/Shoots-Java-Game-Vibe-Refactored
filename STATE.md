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
- `...ai` (C1-C2) — `AiDifficulty`, `TargetMode`, `AiSkills` + `AiSkillsFactory` (seed-derived skills);
  `AiTargeting` (bounce-path reach walk) + `PlayerAiController` (utility targeting, drives applyInput/fire)
  + `AiPlayers` (owns controllers, fills high slots, staggered). Config: `config.AiConfig` + `disc.maxPerShot`.

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
  `CollisionConfig`, `RoundConfig`, `ColorPalette` (+ `RgbColor`) + `AiConfig` + a resolved `long seed` (master run
  seed for map + AI; `GameConfigLoader.resolveSeed` reads optional `game.seed`, blank=fresh time seed).
  `GameConfig` has `withSeed`/`withPlayerNumber`/`withRound` copy helpers. `GameConfigLoader.load()`
  falls back per-key. `PlayWorld(GameConfig)` builds from `config.seed()`; `PlayWorld(GameConfig,long)`
  seeds map+AI and exposes `seed()`; `PlayWorld(GameConfig,Random)` is the test ctor (records seed 0).
- **AI skills = seed-derived knob bundle (C1).** `AiSkillsFactory.create(AiDifficulty,seed,playerId)`
  -> immutable `AiSkills` (normalised knobs in [0,1] + concrete disc counts / tick intervals +
  `TargetMode`). EASY..VERY_HARD interpolate weak->strong endpoints by `ladderFraction()`, nudged by a
  per-AI deviation; `RANDOM` draws a wide band. RNG seeded on `mix(seed,playerId)` so skills are
  reproducible per (seed,player) yet independent across players. C2 reads these; C5 will let
  `game.properties` override the endpoints/deviation.
- **AI control = `PlayerAiController` (C2).** Reads `PlayWorld` (baseOf/aimOf/scoring/collider/config) and
  drives ONLY `applyInput`/`fire` -- AI is just another input source. `think(world)` per tick: on a
  `decisionIntervalTicks` cadence it scans `scanAngles` candidate angles via `AiTargeting.reach`, filters
  own-flank-first-hit angles (never shoot flank blocks), scores reachable `CapturePoint`s by knobs/
  `TargetMode`, caches the best angle (+ Gaussian aim error scaled by 1-accuracy = real misses); other
  ticks nudge aim (cursorSpeedFactor) and fire when aligned, honouring volley pacing + disc caps. Seeded
  RNG -> reproducible. `PlayWorld.collider()` added for the reach walk.
- **AI wiring (C3-C5).** `AiPlayers.build(world,aiCount,difficulty,scanAngles)` fills the HIGHEST
  slots (humans low), clamps count to player count, clamps each AI's disc caps to `disc.maxPerPlayer`/
  `disc.maxPerShot`, staggers first decisions. `PlayingState.updateContinues` calls `aiPlayers.think(world)`
  after human `PlayInput.apply` (so AIs are inert during BEGIN/ENDS/pause via `isPlayerKeyboardAvailable`).
  Menu: `AI_NUMBER_OPTION` (0..playerNumber) + `AI_DIFFICULTY_OPTION` in `GameMenu`, applied to
  `GameSettings.aiNumber/aiDifficulty` on START_NEW_GAME. Tunables: `disc.maxPerShot`, `ai.scanAngles`,
  `ai.scanBudgetPerFrame`, `ai.skillsEnabled` in `game.properties` (per-key fallback).
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
- **C-fixes (post-AI playtest):** aim arc was halved (±90) — restored to legacy ±110 via
  `PlayWorld.AIM_ROTATION_LIMIT_DEG`. Player aim **cursor** drawable (legacy `PlayerCursor`) restored as
  `GameScreen.drawCursors` (arrowhead along `aimOf(p).currentAngle()`, dir=(-sinθ,cosθ) matches disc
  travel; `CursorDirectionTest`). Menu value rows centred via `optionValue(...)` (no manual padding,
  symmetric chevrons); rows recompacted (`menuHeight=120`/`nextLine=46`, contiguous 0..13) so
  CONTROLS/QUIT fit the 900px window. `AiDifficulty.getDisplayName()` added for the menu label.
- **Base placement (post-AI):** `MapGenerator.BASE_CENTRES` kept at 1 tile from each border
  (`{12,23}`,`{12,1}`,`{1,12}`,`{23,12}`) so each base box + its `FLANK_OFFSET=2` flank walls TOUCH the
  border (matches `preview/PLAYER_CURSOR_EXAMPLE.png`). Even so, no aim angle across the ±110° arc
  first-reflects on an own flank (disc clears the spawn box first), so the AI flank filter is a
  harmless safeguard — proven by `FlankFilterHarmlessTest`. Menu value rows are centred under their
  labels via `shadowStringCentered` (tight `< n >` for numbers, symmetric chevrons for the difficulty
  name); rows gapped so CONTINUE and CONTROLS/QUIT sit apart from the option block, all inside the
  backdrop (bottom extended past QUIT).
- **Spawn side / aim now fixed per player id (bug):** `PlayWorld.locateBases` used to assign bases by
  MAP SCAN ORDER, so player count changed which side each player spawned on and which way they aimed
  (2-player cursors/lasers pointed at the border). Now it maps `playerId -> MapGenerator.baseCentre(p)`
  + `SHOOT_DIRECTIONS[p]`: P0 bottom, P1 top, P2 left, P3 right, ALWAYS, with neutral aim pointing at
  the map centre regardless of player count. `MapGenerator.baseCentre(int)` exposes the canonical
  tiles. Tests in `PlayWorldTest` (spawn-side-independent-of-count, aim-to-centre, aim-independent-of-count).
- **Per-player aim key handedness (bug):** human LEFT/RIGHT keys are mirrored for the side-facing
  bases so each player's key turns toward THEIR own left/right (legacy `Player.moveUnit`: P1/P3=-1
  mirrored, P2/P4=+1). `PlayWorld.aimKeysMirrored(playerId)` drives the swap in `PlayInput.aimFor`;
  the world API (`applyInput`) stays raw so the AI is unaffected. Menu: all option titles + action
  rows now drawn via `shadowStringCentered` (centred on the panel).
- **Per-round map regeneration (bug):** the map was built once and reused every round. `PlayWorld`
  now regenerates the map each `resetRound()` from `mapSeedFor(roundIndex) = mix(baseMapSeed, round)`
  via `buildMap` (rebuilds tiles/collider/laser/bases + re-registers capture points; rebinds
  `DiscSystem` collider via new `setCollider`). `baseMapSeed` = master seed (or a value drawn from the
  test Random when seed==0); `resetMatch` resets `roundIndex=0` so a new game restarts the sequence —
  fully reproducible per master seed. `PlayingState.rebuildAiForCurrentMap()` rebuilds `AiPlayers`
  after each `resetRound()` so AI targeting binds to the fresh collider. Tests in `PlayWorldTest`.
- **USER ACTION (Windows, leftover stubs):** delete the inert Windows-locked stub files I could not
  remove from the sandbox: the whole `src/main/java/pl/mzebrows/shoots/game/logic/` folder and
  `ui/PSConst.java`. Build is green with them present (they hold only a package line).
- **BUILD ENV**: `./mvnw` auto-detects the vendored offline toolchain in `tools/` (JDK 26 + Maven 3.9 +
  pre-seeded `.m2`); builds fully offline in the sandbox. Verify with `./mvnw test` from project root.
  **Mount gotcha:** editor/`rm` on existing files is Windows-locked; writes can append NUL bytes — write
  via bash heredoc/python, verify, and hand file deletions to the user on Windows.

## Legacy Logic Coverage Map
- All legacy `game.logic` responsibilities migrated to `world`/`score`/`entity`/`spatial` (c4-c11); legacy retained for c12 ref, deleted c13.
