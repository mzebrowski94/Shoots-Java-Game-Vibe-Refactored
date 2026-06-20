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

## 3. Writes: trust-but-verify (not "never use Edit")

The mount *can* truncate a write or inject NUL bytes, but in practice this is **rare, not
systematic** — Write+Edit on `.md`/`.java` came back clean when last tested (2026-06-20). So:
- **Use the native Edit tool for surgical changes** — it's the cheapest path and it works.
  Use a `bash` heredoc for full-file (re)writes.
- **Let the build be your verifier for code.** A truncated/NUL-corrupted `.java` fails
  compilation loudly ("reached end of file while parsing" / "illegal character: NUL"), and
  `./mvnw test` catches it. You do NOT need a manual check after every edit.
- **Manually verify only when it matters** — a non-compiled file (`.md`, `.properties`) you
  won't build, or any file where an edit looked suspicious. One command:
  `wc -l FILE; grep -aP '\x00' FILE | wc -l` — NUL count must be 0.
  (`grep -c $'\x00'` is a false friend — the empty pattern matches every line.)
- **If a write DID corrupt:** restore from git (`git show HEAD:PATH > PATH`) and re-apply via
  bash heredoc — don't hand-repair a truncated tail.

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
| reached end of file while parsing / illegal character NUL | mount corrupted a write (rare) | restore from git, rewrite via heredoc, verify NUL=0 |
| Fix doesn't change test results | stale class files | `./mvnw clean test` |
| can't build / `java -version` shows 11 | looking at system JDK | use `./mvnw` (vendored JDK 26) |
| copying … to target/… failed: NoSuchFileException | broken/locked target on mount | already avoided via build.dir; else delete target on Windows |
| index.lock / index file corrupt | Windows-held git lock | user fixes on Windows (§4) |
| rm: Operation not permitted | mount lock (even on new files) | neutralise in place; user deletes on Windows |
