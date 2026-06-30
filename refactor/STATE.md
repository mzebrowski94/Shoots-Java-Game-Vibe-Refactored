# STATE.md — Architecture Reference

Living map + established contracts for new-feature work. Keep it compact: record permanent
contracts, not history. Pair with CLAUDE.md (conventions), GameRules.md (mechanics),
OnlineMode.md (online), NewFeatures.md (backlog), tools/AgentWorkflow.md (build/mount).

## Package map (root `pl.mzebrows.shoots`; entry `ProjectShoots`)
The AWT-free deterministic simulation (`world`/`entity`/`spatial`/`score`/`ai`) sits alongside the app shell + engine.
- `config` — immutable config records + `GameConfigLoader` (loads `game.properties` logic +
  `graphic.properties` rendering, merged). Aggregates `GameConfig` (gameplay) + `GraphicsConfig`
  (`MenuTheme`+`ObjectStyle`). `GameplayLimits`/`GameplayOptions` = player-editable tunables + caps.
- `input` — `GameAction` enum + `InputBridge` (EDT writes raw keys, loop reads via `poll()`; also text capture).
- `state` — `GameState`+`GameStateMachine`; `PlayingState` (drives `PlayWorld` + round cycle), `PausedState`, `GameOverState`.
- `engine` — `GameLoop` (heartbeat thread + composition root) + `FixedTimestep` (fixed-step accumulator).
- `render` — `Renderer`/`AwtRenderer`/`ImageCache`. `render.object` — ordered `MapObjectRenderer`s +
  `RenderFrame`; one renderer per map object (OCP), read-only views over `PlayWorld`.
- `ui` — AWT shell (`GameFrame`/`GameCanvas`/`GameScreen`/`GameCounter`/`GamePointer`/`GameMenu`,
  `ColorScheme`, `MenuEnum`/`RoundEnum`/`GameDimensions`) + session state (`GameSettings`/`Round`).
- `net` — online (see OnlineMode.md): `TcpConnection`/`TcpServer`/`TcpClientTransport`, `MessageCodec`/`NetMessage`,
  `LockstepCoordinator`/`LockstepApplier`, `OnlineHost`/`OnlineClient`/`OnlineSession`/`OnlineLobby`,
  LAN discovery (`LanBeacon`/`LanDiscovery`/`LanAnnouncement`), `RoundFlow`, `WorldHash`.
- `world` — `PlayWorld` facade (headless, AWT-free, tested; main entry over entities/collider/scoring) +
  `PlayInput` adapter + `MatchFlow`; per-mechanic subsystems the facade owns + delegates to:
  `DisruptionSystem` (disrupt/grace timers + parked disc), `ChargeController` (power hold-to-charge),
  `BlockHitEffects` (wall-flash list; + the `BlockHitEffect` data record).
- `entity` — pooled `Entity`/`EntityType` + `ObjectPool<T>`; `AimController`, `AttackStrategy`/`DiscAttackStrategy`,
  `LaserPredictor`, `EntitySpawner`; disc lifecycle `DiscSpawner` (spawn/retire) + `DiscSystem` (per-tick, index loops).
- `spatial` — `SpatialCollider`/`UniformGridCollider` (tile queries), `TileType`, `MapGenerator`, `GridPathTracer`.
- `score` — `CapturePoint`, `CaptureScoring`, `PlayerScore`, `MatchScorer`.
- `ai` — `AiDifficulty`/`TargetMode`/`AiSkills`(+`AiSkillsFactory`)/`AiTargeting`/`PlayerAiController`/`AiPlayers`.
  An AI is just another input source: it drives `PlayWorld.applyInput`/`fire`; nothing special-cases it in the loop.

## Design patterns
Strategy (`AttackStrategy`), State (`GameStateMachine`), Object Pool, Facade (`PlayWorld`),
Adapter (`PlayInput`/`AwtRenderer`), Game Loop (`FixedTimestep`), Spatial Partition (`UniformGridCollider`),
Observer (`DiscSystem.DiscEventSink`), Factory/Spawner (`DiscSpawner`/`MapGenerator`), System/ECS.

## Established contracts
- **Config = immutable records, AWT-free, NO code defaults.** Every key in the two `.properties` files is
  required (missing/unparseable -> `ConfigException` -> log + exit). Map geometry is the only fixed code
  constant (`GameConfigLoader.GRID_UNIT`/`TABLE_SIZE`). `GameConfig` aggregates Grid/Disc/Collision/Round/
  ColorPalette/Ai/Menu/Window/Power/Disruption + a resolved master `seed` (drives map+AI; blank `game.seed`
  = fresh time seed). Copy helpers `withSeed`/`withPlayerNumber`/`withRound`/`withDisc`/`withDisruption`.
- **`PlayWorld` is the single headless entry point.** `(GameConfig)` builds from `config.seed()`;
  `(GameConfig,long)` seeds map+AI + exposes `seed()`; `(GameConfig,Random)` is the test ctor (records seed 0).
  API: `applyInput(player,AimInput,shoot)`, `applyShoot`, `step`, `fire`/`firePower`, `predictLaser`,
  `resetRound`/`resetMatch`, queries (`discs`,`tiles`,`scoring`,`baseOf`,`aimOf`,`collider`,`tracer`,
  `chargeProgress`, disruption queries). Disc owner tracked by identity (`IdentityHashMap`) because
  retirement `reset()`s the entity (wiping ownerId) before the event sink runs.
- **Spawn/aim fixed per player id, independent of player count.** `MapGenerator.baseCentre(p)` -> P0 bottom,
  P1 top, P2 left, P3 right; neutral aim points at map centre. Human LEFT/RIGHT keys are mirrored for the
  side bases via `PlayWorld.aimKeysMirrored` (raw `applyInput` stays unmirrored, so the AI is unaffected).
  Aim arc = +/-`PlayWorld.AIM_ROTATION_LIMIT_DEG` (110).
- **Map regenerates each `resetRound()`** from `mapSeedFor(round)=mix(baseMapSeed,round)`; `resetMatch`
  restarts the sequence -> fully reproducible per master seed. `PlayingState.rebuildAiForCurrentMap()`
  rebinds AI targeting to the fresh collider after each reset.
- **Reflection geometry = `spatial.GridPathTracer`, the SINGLE source.** It DDA-casts a ray vs tile faces
  and reflects at the exact face, so a slow disc, a fast disc and the laser trace the IDENTICAL polyline
  (only the rate differs); each corner is ONE reflection event. Convex corner = single-axis glance
  (tie-break `|dx|>=|dy|`), concave = reversal. Fully deterministic (no RNG/time) -> replay/online safe.
  Consumers: `DiscSystem` (advance one frame along path; acceleration only changes per-frame distance, not
  geometry), `LaserPredictor`, `AiTargeting`. `SpatialCollider` is now just `tileAt` (OOB->WALL); the old
  band-based `resolve`/`CollisionResult` are retired.
- **Entity = pooled mutable data.** One mutable pooled `Entity` (primitive pos/vel, `prevX/Y`, `EntityType`,
  power/park/owner fields); pool `release()` runs `reset()`. Mutate in place; never replace a pooled instance.
- **Input/state/loop.** All input as `GameAction` (EDT writes, loop polls). `GameStateMachine.update(InputBridge)
  -> GameState`; `PlayingState` owns BEGIN->CONTINUES->ENDS, delegating phase ownership to `net.RoundFlow`
  (OFFLINE/HOST/CLIENT). Round clock counted in SIM STEPS (120/s), not wall-clock -> deterministic.
- **Scoring.** `world.MatchFlow` owns one `PlayerScore`/player + `MatchScorer`; `step()` syncs from
  `CaptureScoring`. `PlayingState` ENDS calls `finishRound`/`isMatchOver`/`resolveMatchWinners`; restart `resetMatch`.
- **Rendering.** `Renderer.render(RoundEnum, alpha, PlayWorld)`. `GameScreen` drives an ordered
  `List<MapObjectRenderer>` (Wall/BlockHit/CapturePoint/Base/Disruption/Cursor/Laser/Disc) once per frame via a
  shared `RenderFrame`. Add a map object = new renderer + register it. Visual tunables from `GraphicsConfig`;
  convention tile[i][j] -> pixel (i*unit, j*unit).

## Gameplay feature contracts
- **Disc acceleration / power shot.** Per-disc `speedGainPerBounce`/`maxMoveSpeed` (from `DiscConfig`);
  `DiscSystem` accelerates on each WALL hit, capped. `PowerShotConfig` (`power.*`): power speed/bounces scale
  off the disc (`power.speedFactor`/`maxBouncesFactor`). `PlayWorld.applyShoot` (delegated to `ChargeController`): press->charge,
  release-before-full->normal disc, full hold->auto power. AI bypasses the charge UX and calls `firePower`.
- **Base disruption** (`DisruptionConfig`, `disruption.*`). `TileType.PLAYER_BASE` is non-solid; `GridPathTracer`
  emits `onPlayerBase`. A disc that disrupts a non-immune opponent PARKS on the base (`Entity.parked`; disc
  lifecycle owned by `PlayWorld`), silencing the victim (no fire/laser) for a duration, then a grace window.
  `DisruptionSystem` owns the per-player disrupt/grace timers + parked-disc refs; `PlayWorld` delegates to it.
  Queries `isDisrupted`/`isImmune`/`disruptionProgress`/`graceProgress`. Overlay-only `DisruptionRenderer`.
- **AI.** `AiSkillsFactory.create(difficulty,seed,playerId[,toggles])` -> immutable `AiSkills` (normalised knobs
  + concrete disc counts/tick intervals + `TargetMode`), reproducible per (seed,player). `PlayerAiController.think`
  scans `ai.scanAngles` angles via `AiTargeting.reach`(`IncludingBases`), filters own-flank-first-hit, scores
  capture points / disruptable opponent bases by knobs, caches the best angle + Gaussian aim error (= real
  misses), fires honouring volley pacing + disc caps. `AiPlayers.build` fills the HIGHEST slots, clamps disc
  caps, staggers first decisions. Per-skill `AiSkillToggles` + master switches live in `AiConfig` (`ai.*`).

## Online (summary; full design in OnlineMode.md)
Host-authoritative deterministic lockstep: peers exchange tiny inputs (aim+shoot) and all re-simulate from one
master seed (no world snapshots). `OnlineSession` drives one command frame per tick with a small input-delay
buffer; the host aggregates via `LockstepCoordinator`, broadcasts the `InputFrame`, and applies it with
`LockstepApplier`; clients follow. `RoundFlow` HOST decides + broadcasts CONTROLs, CLIENT follows. `WorldHash`
per frame detects desync. AWT-free; the offline/hotseat path is unchanged. Determinism is the prerequisite —
keep the sim free of wall-clock/RNG drift.

## Window / options
Elastic scaled window: panels are laid out at a fixed LOGICAL resolution inside a `GridBagLayout`-centred
`gamePanel`; `GameFrame.rescale()` applies one uniform scale (spare space letterboxes); scale 1.0 is
byte-identical to the old fixed window and the sim is untouched. GAMEPLAY OPTIONS (`config.GameplayOptions`, held
by `GameSettings`) = live tunables seeded from config and clamped to `GameplayLimits` (`<key>.min/.max/.step`);
`applyTo(GameConfig)` overlays them when a world is built; locked while a match is in progress; the host
broadcasts them in `NetMessage.Start` so every peer builds an identical world.

## Invariants for new-feature work
- New gameplay logic goes through `PlayWorld`, stays AWT-free + unit-testable.
- Zero `new` / no streams / no logging in the hot loop (per-frame entity/disc/collision). Pools + primitives only.
- Don't break the fixed-timestep accumulator/clamp/interpolation or sim determinism (canary: targeting/scoring/seed tests).
- Adding a config field = add the key to the right `.properties` file (no code defaults).
- Verify with `./mvnw test` (offline vendored toolchain). The mount blocks unlink and can corrupt native
  writes — see tools/AgentWorkflow.md. Full unit suite is green.
