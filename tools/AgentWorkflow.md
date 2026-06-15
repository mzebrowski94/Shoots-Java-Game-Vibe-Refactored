# AGENT_WORKFLOW.md — Environment Gotchas & Reliable Workflow

> Read this first if you are an AI agent (or anyone) building this repo from the
> isolated Linux sandbox. It documents failure modes already hit and the workflow
> that avoids re-deriving them every session. Most issues stem from ONE root cause:
> the project lives on a Windows folder exposed to Linux through a 9p/virtiofs-style
> **mount** (path under `/sessions/<id>/mnt/...`). That mount is reliable for reading
> and for creating *new* files, but unreliable for two operations.

## Build toolchain: USE `./mvnw`, do NOT trust the system JDK

**Before concluding "the build can't run here", check `tools/`.** This repo ships a
vendored, offline build toolchain and `./mvnw` auto-detects it:

```
tools/jdk-26.0.1/            # full JDK (bin/javac) — the project needs release 25+
tools/apache-maven-3.9.16/   # Apache Maven
tools/.m2/repository/        # pre-seeded local repo (all deps + plugins, offline)
```

- The **system** `java`/`javac` on PATH is JDK 11 and there is **no system `mvn`** —
  ignore both. `./mvnw` picks up the vendored JDK 26 + Maven and builds **fully
  offline** (Maven Central is firewalled by the sandbox allowlist; the seeded `.m2`
  is why offline works). Network fetches WILL fail — never `curl`/`wget` deps.
- So the correct verification is always: `cd <project root> && ./mvnw test`
  (run from the dir containing `mvnw`). It compiles `release 25` fine via the vendor JDK.
- Past mistake to avoid: running `java -version`, seeing 11, and reporting that the
  project (release 25) cannot be built. WRONG — that ignores `tools/`. Always test via `./mvnw`.

## The two mount failure modes

1. **In-place writes can silently truncate or inject NUL bytes.** Rewriting a file
   may leave the tail unwritten (compiler: "reached end of file while parsing") or a
   block of NUL bytes (compiler: "illegal character: NUL"). The write tool may still
   report success.
2. **Existing files held by a Windows process cannot be deleted/overwritten from
   Linux** ("Operation not permitted"). This hits `.git/index`, `.git/index.lock`,
   and `target/**/*.class`. The worst symptom: Maven cannot overwrite locked class
   files, so it **silently runs stale bytecode** and your fixes appear to do nothing.

## Standing fixes already in place (do not undo)

- **`target/` is redirected off the mount in the sandbox.** `pom.xml` defines
  `<build><directory>${build.dir}</directory>` with `build.dir` defaulting to
  `${project.basedir}/target` (so Windows/IDE builds are unchanged). `mvnw` detects a
  sandbox mount and passes `-Dbuild.dir=$TMPDIR/mvn-build/<slug>` automatically, so
  class files never land on the mount. Override with `-Dbuild.dir=...` or `$BUILD_DIR`.
- Net effect: **`./mvnw test` works directly on the real tree** in the sandbox. You
  do NOT need to copy the project to /tmp to build anymore.

## Reliable workflow (follow this, don't improvise)

1. Build/verify with `./mvnw test` (or `clean test`). It already builds off-mount.
   If you ever still see lock / NoSuchFile / stale behaviour, build a clean copy:
   rm -rf /tmp/build && cp -r pom.xml mvnw .mvn src /tmp/build/ && ln -s "$PWD/tools" /tmp/build/tools && cd /tmp/build && ./mvnw test
2. After writing/editing any file, verify it landed. One command:
   wc -l FILE; grep -c '{' FILE; grep -c '}' FILE; grep -aP '\x00' FILE | wc -l
   Braces must balance and NUL count must be 0. (Note: grep -c $'\x00' is a FALSE
   FRIEND — the empty pattern matches every line. Use grep -aP '\x00' | wc -l.)
3. Prefer heredoc writes for large/rewritten files (cat > FILE <<'EOF' ... EOF).
   This write path proved reliable; the editor tool and python open().write() were
   the ones that truncated. Always re-verify per step 2.
4. Treat "my change had no effect on test output" as a build-cache smell. Suspect
   stale classes immediately; run ./mvnw clean test, don't keep editing.
5. Git locks: if git says index.lock: File exists or index file corrupt and you
   cannot rm it from Linux, it is a Windows-side lock. Fix on Windows:
   del .git\index.lock & del .git\index & git reset  (rebuilds the index from HEAD;
   no working files touched). Closing the IDE first usually prevents it.
6. Cannot delete a stray file (e.g. a debug stub) from Linux? It is Windows-locked.
   Neutralise it in place (make it inert/compilable) and ask the user to del it.
7. Reduce locks at the source: close the IDE or disable its background auto-build /
   indexer for this project while an agent works — most "cannot delete target/.class"
   cases are IntelliJ compiling in the background.

## Quick reference: symptom -> cause -> action

| Symptom | Cause | Action |
|---|---|---|
| reached end of file while parsing / illegal character NUL | mount truncated/corrupted a write | rewrite via heredoc; verify with step 2 |
| Fix doesn't change test results | stale class files (locked target) | ./mvnw clean test; ensure build is off-mount |
| copying ... to target/... failed: NoSuchFileException | broken/locked target on mount | already avoided via build.dir; else delete target on Windows |
| index.lock: File exists / index file corrupt | Windows-held git lock | fix on Windows (see step 5) |
| rm: Operation not permitted on an existing file | Windows process holds it | neutralise in place; delete on Windows |
