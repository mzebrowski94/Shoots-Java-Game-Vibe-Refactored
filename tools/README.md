# Offline build toolchain (for sandboxed verification)

`./mvnw` auto-detects a vendored toolchain in this `tools/` folder and, when it
finds one, builds **fully offline** — no network, no Maven Central. This lets the
agent run `./mvnw compile` and `./mvnw test` itself.

Everything here is git-ignored (see `.gitignore`); these are large binaries you
populate once locally. The agent's sandbox is **Ubuntu 22.04, x86_64, glibc 2.35**,
so all three pieces must be **Linux x64 (glibc)** builds — not Windows, not musl/Alpine.

## Layout `./mvnw` expects

```
tools/
├── jdk-25.../            # a full JDK 25 (must contain bin/javac)
├── apache-maven-3.9.../  # Apache Maven (must contain bin/mvn)
└── .m2/repository/       # pre-populated local Maven repo (all deps + plugins)
```

Any directory matching `jdk*`, `maven*`, or `apache-maven*` is picked up
automatically; the exact version suffix doesn't matter.

## One-time setup (run on your machine, where the build already works)

### 1. Pre-download every dependency and plugin into `tools/.m2`
From the project root, with your working Maven + JDK 25 on PATH:

```bash
mvn -Dmaven.repo.local=tools/.m2/repository dependency:go-offline
mvn -Dmaven.repo.local=tools/.m2/repository dependency:resolve-plugins
# Surefire pulls a couple of providers only at test time — prime them once:
mvn -Dmaven.repo.local=tools/.m2/repository test
```

> If you're on Windows, run those in WSL/Git-Bash so the repo layout matches, or
> just run them in PowerShell — the `.m2` contents are OS-independent.

### 2. Drop in a Linux x64 JDK 25
Download a **Linux x64** JDK 25 tarball (Eclipse Temurin or Amazon Corretto) and
extract it here, e.g.:

```bash
mkdir -p tools && tar -xzf OpenJDK25U-jdk_x64_linux_hotspot_*.tar.gz -C tools/
# result: tools/jdk-25.../bin/javac
```

### 3. Drop in Apache Maven
```bash
tar -xzf apache-maven-3.9.*-bin.tar.gz -C tools/
# result: tools/apache-maven-3.9.../bin/mvn
```

## Verify it works offline
```bash
cd <project root>
./mvnw -v          # should report the vendored JDK 25 + Maven
./mvnw compile     # runs with -o (offline) automatically
./mvnw test
```

If `tools/` is empty or incomplete, `./mvnw` transparently falls back to the
`mvn` and JDK on your PATH, so committing this wrapper never breaks a normal
local build.
```
