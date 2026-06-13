# STATE.md — Living Architecture Reference

> Rewrite sections **in place** as the refactor progresses. Don't turn this into a
> history log. Promote permanent items to "Established Contracts"; delete resolved
> ones. Target: whole file under ~80 lines.

## Package Map
_(new code lands here; filled in starting with cluster 1)_
- Root package: `pl.mzebrows.shoots`. Legacy lives in `...shoots.game.logic` (+ `.Drawables`)
  and `...shoots.game.main`. Entry point: `main/ProjectShoots`.

## Legacy Code Map
_(remove an entry once its class is migrated/deleted)_
- **main/ProjectShoots** — trivial entry point; constructs GameLoop.
- **GameLoop** (290) — top-level loop INSIDE constructor; Thread.sleep pacing, TARGET_FPS=120;
  manual round/menu state flags; O(N²) player×disc collision in updateGameLogic.
- **GameSettings** (433) — GOD CLASS: config + game state + players + rounds + font init.
- **GameFrame/GameCanvas/GameScreen/GameCounter/GamePointer** — AWT Canvas/JFrame render panels;
  draw lists, side/top panels, per-frame allocations; logic+Graphics2D mixed.
- **GameMenu** (295) — menu nav + end-score screen; raw KeyEvent codes; per-frame `new Color`.
- **KeyboardInput** — KeyListener; `keys[]`/`currentKeys[]`, poll()/keyDown()/keyDownOnce().
- **Player** (340) — base entity: position, disc list, raw key bindings, score; index/coord confusion.
- **ColisionCalculator/ColisionPoint** — grid collision + reflection; `ballColisionSize=4`,
  hard-coded grid bound 24; ColisionPoint = mutable pos/dir/speed holder.
- **MapMatrix** — int[][] grid builder (walls/blocks/point fields); allocates unused ColisionPoint.
- **PointList/PointField/Round** — capture-point scoring + round timing; O(N²) scan in PointList.
- **ColorScheme/PSConst/MenuEnum/RoundEnum** — palette holder; const enum (UNIT=36,TABLESIZE=25);
  menu/round state enums.
- **Drawables/**: `Drawable{draw(Graphics2D)}`, `DrawableEffect{draw, isEffect}` interfaces;
  Block, Disc (projectile, `new ColisionPoint` in ctor), LightEffect, PlayerBase, PlayerCursor,
  PlayerLaser (trajectory pre-compute), PointField. All allocate Color/Stroke/Transform per draw.

## Established Contracts
_(none yet — clusters 2-7 establish these)_

## Open Decisions / Backlog
_(short, forward-looking; delete once resolved)_
- **pom.xml is stale**: JUnit 3.8.1 only, no Java-version/compiler config, no Lombok/SLF4j/
  AssertJ/Mockito. Cluster 1 must rewrite it for Java 25 + deps.
- **Hard-coded font/image paths** (GameSettings ~110, GameFrame ~34): Windows-absolute paths →
  load from classpath with graceful fallback (cluster 1 config + cluster 3 renderer).
- **Magic numbers everywhere** (grid 24/25, ballColisionSize=4, UNIT=36, arc angles, layout
  offsets, radii) → externalize into config records (cluster 1).
- **Loop in GameLoop ctor + Thread.sleep pacing** → replace with fixed-timestep accumulator +
  interpolation (cluster 3). Negative sleepTime risk noted.
- **O(N²) collision** (GameLoop update, PointList scan) → SpatialCollider grid (cluster 5).
- **Per-frame allocations in all Drawables** → pooling + cached Stroke/Transform; decouple
  logic from Graphics2D (clusters 3-4-6).
- **Raw KeyEvent codes** (Player, GameMenu) → GameAction enum + thread-safe bridge (cluster 2).
- **"Colision" misspelling** throughout legacy API — rename to "Collision" when migrating.
- **Strategy-vs-ECS** for cluster 4: plan recommends Strategy-based composition; confirm there.
