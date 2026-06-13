#!/bin/sh
# Thin Maven wrapper: delegate to the Maven installed on PATH.
# The repo's verify harness and permission allowlist invoke ./mvnw; this
# keeps that contract working without committing a binary wrapper jar.
exec mvn "$@"
