# AgentWorkflow.md — Build & Sandbox Reference

Owner doc for **build, the Linux sandbox, and the mount gotcha**. `CLAUDE.md` carries the
three rules; come here for the *why* and the symptom table at the bottom.

**Root cause of every quirk below:** the repo is a Windows folder exposed to the Linux
sandbox over a mount (`/sessions/<id>/mnt/...`). The mount reads files, *creates* files, and
**rewrites existing files in place** (truncate-then-write) all fine. What it refuses is
**unlink** — deleting or renaming a file ("Operation not permitted").

## Inviolable rules
1. Build/verify with **`./mvnw test`** from the project root. Never infer "can't build" from
   `java -version` — the system JDK is 11; the build uses the vendored JDK 26 in `tools/`.
2. **Never delete or rename a mount file from Linux.** Overwriting in place is fine; unlink is
   not. Hand deletions to the user (Windows).
3. **The user owns git.** Don't `git add/commit/push` — just summarise what changed.

## Build
- `./mvnw` auto-detects the offline toolchain (`tools/jdk-26.0.1`, `tools/apache-maven-3.9.16`,
  seeded `tools/.m2`) and forces offline (`-o -Dmaven.repo.local=tools/.m2/...`). **Don't** add
  `-o`, pass `-Dbuild.dir`, or `curl`/`wget` deps — the network is firewalled.
- `target/` is redirected off-mount (`-Dbuild.dir=$TMPDIR/mvn-build/<slug>`), so `./mvnw test`
  runs on the real tree — no /tmp copy needed, and class files never touch the mount.
- **Inner loop:** `./mvnw -q test-compile` (~13 s) only compiles — use it to catch errors while
  iterating. Run full `./mvnw -q test` (~28 s) once before declaring done. Batch edits between
  runs; don't rebuild after every line.
- **"My change had no effect"** → stale classes → `./mvnw clean test`. Most stale/lock cases are
  IntelliJ auto-building in the background; closing it (or disabling background build) avoids them.
- A failed compile can be **masked by stale artifacts** in a later step (e.g. a render/run step
  "succeeds" on old `.class` files). Trust the exit code of the *compile/test* step itself.

## Editing files reliably
The mount can silently **truncate** a write: the file ends mid-line with the tail dropped and
**no NUL bytes**, so a NUL check won't catch it. Native `Edit` round-trips are the usual culprit;
full in-place writes are not. Ranked by reliability (all verified in this repo):
1. **Python replace-script** for multi-point edits — read file, `str.replace` each change with
   `assert count == 1`, write back. Surgical *and* reliable, and cheaper than retyping the file.
2. **`cat > FILE <<'EOF' … EOF`** heredoc for a full rewrite.
3. **Native `Edit`** — only for a genuine one-liner.

**Verify in the same bash call**, never wait for the build:
`wc -l < FILE; tail -3 FILE; grep -aPc '\x00' FILE` — expect a plausible line count, your real
last line, and `0`. The **tail is the primary detector** (truncation is NUL-clean); use
`grep -aPc` (plain `-c` matches every line). If a write truncated, **rewrite the whole file**
(method 1/2) — restore lost content from git first if needed (`git show HEAD:PATH > /tmp/orig`).
Don't re-`Edit` the broken tail; it tends to truncate again.

## Deleting / stray files
Can't `rm` a file (even one you just created)? That's the mount blocking unlink, not a permissions
bug. Leave it **inert in place** (truncate to empty, or make it compile to a no-op) and ask the
user to delete it on Windows. Keep throwaway scratch — harnesses, scripts, PNGs — in the **outputs
scratch dir, never in the repo**, so there is nothing to delete.

## Verifying UI / layout changes headlessly
AWT renders to a `BufferedImage` with no display. To check a menu/HUD change without launching the
game: compile a throwaway harness (in the target class's package, kept in the scratch dir — not the
repo) against the off-mount classes dir + seeded jars, draw the screen to a PNG in outputs, and view
it. Render onto a full-window canvas with the counter/pointer regions and a centre crosshair marked
to judge centring and overlap. Far more reliable than reasoning about coordinates.

## Symptom → cause → action
| Symptom | Cause | Action |
|---|---|---|
| `reached end of file while parsing`; tail missing, NUL 0 | mount **truncated** the write | rewrite whole file (Python/heredoc); verify `tail` + `wc -l` at write time |
| `illegal character: NUL` | mount injected NUL (rarer) | rewrite; `grep -aPc '\x00'` must be 0 |
| change has no effect on tests | stale classes | `./mvnw clean test` |
| compile fails but a later run "works" | stale artifacts mask it | trust the compile/test exit code, not a downstream step |
| `can't build` / `java -version` is 11 | reading the system JDK | use `./mvnw` (vendored JDK 26) |
| `rm: Operation not permitted` | mount blocks unlink | neutralise in place; user deletes on Windows |
| `PluginResolution/DependencyResolutionException … not downloaded` (offline) | pom pins a plugin/dep version ahead of seeded `tools/.m2` | not your code; compare pom `<version>` to `ls tools/.m2/...`; reseed `.m2` on a networked host, or temp-match the pom to verify, then revert |
| `index.lock` / `index file corrupt` | Windows-held git lock | user fixes on Windows: `del .git\index.lock & del .git\index & git reset` |
