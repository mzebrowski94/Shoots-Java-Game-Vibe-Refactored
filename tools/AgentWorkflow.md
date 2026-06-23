# AgentWorkflow.md — Environment Gotchas & Reliable Workflow

> Reference doc. The 2–3 inviolable rules also live in `CLAUDE.md` (loaded every turn);
> consult THIS file when a specific symptom appears — jump to the symptom table at the
> bottom. Root cause of almost everything here: the project is a Windows folder exposed to
> the Linux sandbox over a 9p/virtiofs-style **mount** (`/sessions/<id>/mnt/...`). The mount
> reads fine and creates new files fine; it is unreliable for **deleting/overwriting** files.

## The inviolable rules (everything else is detail)

1. **Build/verify with `./mvnw test`.** Never conclude "can't build here" from `java -version`.
2. **You cannot delete or overwrite mount files from Linux.** Hand deletions to the user (Windows).
3. **The user owns git.** Do NOT run `git add`/`commit`/`push` — just summarise what changed.

## 1. Build: use `./mvnw`, ignore the system JDK

`./mvnw` auto-detects the vendored offline toolchain in `tools/` and builds **fully offline**:
```
tools/jdk-26.0.1/            # JDK 26 (bin/javac); project compiles release 25
tools/apache-maven-3.9.16/   # Maven
tools/.m2/repository/        # pre-seeded local repo (all deps + plugins)
```
- System `java`/`javac` on PATH is JDK 11 and there is **no system `mvn`** — ignore both.
  Maven Central is firewalled; the seeded `.m2` is why offline works. Never `curl`/`wget` deps.
- Correct verification, always: `cd <project root> && ./mvnw test` (or `clean test`).
- Past false alarm to avoid: seeing JDK 11 and reporting that release-25 can't be built. Wrong —
  that ignores `tools/`.
- **Toolchain drift is real — check `tools/.m2` BEFORE assuming your code broke the build.**
  The pinned plugin/dependency versions in `pom.xml` can drift *ahead* of what's seeded in
  `tools/.m2/repository/` (seen 2026-06-23: pom pinned compiler `3.15.0`, surefire `3.5.6`,
  Lombok `1.18.46`; the offline repo only had `3.14.0`/`3.5.4`/`1.18.42`). Symptom: a
  `PluginResolutionException`/`DependencyResolutionException` saying an artifact "has not been
  downloaded" in offline mode — this fires *before* compilation, so it is NOT your code.
  Diagnose: `ls tools/.m2/repository/org/apache/maven/plugins/<plugin>/` and compare to the
  `<version>` in `pom.xml`. Fix options: (a) reseed `tools/.m2` to the pinned versions on a
  networked host (preferred — keeps the committed pom authoritative); or (b) to verify code
  locally, temporarily edit the pom to the cached versions, run `./mvnw test`, then **revert the
  pom to its committed state** (`git diff -- pom.xml` must be empty). Never commit a downgrade
  done only to satisfy this sandbox.

## 2. The deletion constraint (the reliable failure mode)

Files on the mount **cannot be deleted or overwritten from Linux** ("Operation not permitted") —
this even applies to files created *this* session (verified 2026-06-20). It hits `.git/index`,
`.git/index.lock`, `target/**/*.class`, and any stray file you'd want to remove.
- **Can't delete a file?** It's a mount lock, not a permissions bug. Neutralise it in place
  (truncate to empty / make it inert+compilable) and tell the user to `del` it on Windows.
- **`target/` is redirected off-mount in the sandbox** (do not undo): `pom.xml` sets
  `<build><directory>${build.dir}</directory>` (default `${project.basedir}/target`); `mvnw`
  passes `-Dbuild.dir=$TMPDIR/mvn-build/<slug>` in the sandbox so class files never hit the
  mount. Net effect: **`./mvnw test` works directly on the real tree** — no /tmp copy needed.
  (Legacy fallback, rarely needed: `cp -r pom.xml mvnw .mvn src /tmp/build/ && ln -s "$PWD/tools"
  /tmp/build/tools && cd /tmp/build && ./mvnw test`.)

## 3. Writes: heredoc by default, verify the TAIL immediately

The mount can silently **truncate** a write — the file is cut off at a clean point (mid-line or
mid-comment) with the rest dropped. **No NUL bytes are injected when this happens**, so a NUL check
does NOT detect it. (NUL injection is a separate, rarer corruption; truncation is the one that bit
hard on 2026-06-23 — five source files truncated in a single edit pass, every one NUL-clean.)

What proved reliable vs not, this session:
- **`bash` heredoc full-file writes: reliable.** Every heredoc-written file came back intact.
- **Native `Edit` round-trips: unreliable for non-trivial files.** The truncations all landed on
  files changed via `Edit` (and re-synced through the mount). Treat `Edit` as fine ONLY for a
  genuine one-liner; for anything larger, rewrite the whole file with a heredoc.

The rules:
- **Prefer a `bash` heredoc (`cat > FILE <<'EOF' ... EOF`) for any multi-line change or full
  rewrite.** Use native `Edit` only for true single-line surgical fixes.
- **Verify the TAIL in the SAME bash call, every time** — do not wait for the build:
  `cat > FILE <<'EOF' ... EOF; echo "lines: $(wc -l < FILE)"; tail -3 FILE; echo "NUL: $(grep -aPc '\x00' FILE)"`.
  The file must (a) end with the real last line you wrote, (b) have a plausible line count, and
  (c) report `NUL: 0`. Truncation shows up as a wrong/short tail even when NUL is 0 — the **tail
  check is the primary detector; NUL is secondary**.
- **Let the build be a backstop, not the first alarm.** A truncated `.java` does fail
  compilation, but the error arrives late and can interleave with unrelated failures (e.g. a
  toolchain-drift `PluginResolutionException`), which wastes a cycle untangling them. Catch it at
  write time instead.
- **If a write DID truncate:** rewrite the whole file via heredoc (restore the lost tail from git
  first if you no longer have the content: `git show HEAD:PATH > /tmp/orig`). Don't hand-patch the
  truncated end with another `Edit` — that tends to truncate again.
- `grep -c $'\x00'` is a false friend (the empty pattern matches every line); use `grep -aPc '\x00'`.

## 4. Git: the user commits, you don't

The user adds and commits all changes themselves. The model should **not** spend tokens or tool
calls on `git add`/`commit`/`push`. Just briefly describe what changed so the user can review and
commit. (Git index corruption — `index.lock: File exists` / `index file corrupt` — is a
Windows-side lock; the user fixes it on Windows: `del .git\index.lock & del .git\index & git reset`,
which rebuilds the index from HEAD and touches no working files. Closing the IDE first prevents it.)

## 5. Stale-build smell
"My change had no effect on test output" → suspect stale classes immediately → `./mvnw clean test`,
don't keep editing. (Reduce locks at the source: close the IDE / disable its background auto-build
for this project while an agent works — most lock cases are IntelliJ compiling in the background.)

## Quick reference: symptom → cause → action

| Symptom | Cause | Action |
|---|---|---|
| reached end of file while parsing (tail missing, NUL=0) | mount **truncated** the write (silent; not rare) | rewrite whole file via heredoc; verify `tail -3` + `wc -l` at write time |
| illegal character: NUL | mount injected NUL bytes (rarer) | rewrite via heredoc; `grep -aPc '\x00'` must be 0 |
| PluginResolution/DependencyResolutionException, "has not been downloaded" (offline) | toolchain drift: pom pins versions ahead of `tools/.m2` | NOT your code; compare pom `<version>` to `ls tools/.m2/...`; reseed `.m2`, or temp-downgrade pom to verify then revert |
| Fix doesn't change test results | stale class files | `./mvnw clean test` |
| can't build / `java -version` shows 11 | looking at system JDK | use `./mvnw` (vendored JDK 26) |
| copying … to target/… failed: NoSuchFileException | broken/locked target on mount | already avoided via build.dir; else delete target on Windows |
| index.lock / index file corrupt | Windows-held git lock | user fixes on Windows (§4) |
| rm: Operation not permitted | mount lock (even on new files) | neutralise in place; user deletes on Windows |
