#!/bin/bash
set -e

claude --permission-mode acceptEdits -p "@RefactorPrompt.md @GameRules.md @RefactorPlan.md @STATE.md
1. Read STATE.md first. It holds the current package map, the legacy code map, and contracts (interfaces/base classes/enums) already established by earlier clusters. Reuse those contracts as-is - do not redesign them unless STATE.md lists them under 'Open Decisions'.
2. Find the highest-priority CLUSTER in RefactorPlan.md that still has unchecked [ ] sub-items. Work ONLY within that cluster, even if you finish early.
3. Implement every unchecked sub-item of that cluster, following RefactorPrompt.md (Java 25 features, object pooling, Strategy/ECS composition, decoupled rendering, etc.). Complete whole sub-items only - never leave one half-done.
4. Write JUnit 5 / AssertJ / Mockito tests for any newly decoupled logic.
5. Verify: ./mvnw compile -q && ./mvnw test -q
   - If this fails and you cannot fix it within this session: run 'git checkout -- .' to discard the changes, leave the cluster's checkboxes as they were, and add a short note describing the blocker under 'Open Decisions' in STATE.md. Do not commit broken code and do not check off any sub-item you reverted.
6. For each sub-item you completed and verified, change its [ ] to [x] in RefactorPlan.md. Mark the cluster header [x] only when every sub-item under it is [x].
7. Update STATE.md in place (Package Map / Established Contracts / Legacy Code Map / Open Decisions). Remove entries that are no longer relevant. Keep the whole file under ~80 lines.
8. Commit with a message containing cluster title
If every cluster in RefactorPlan.md is [x], output <promise>COMPLETE</promise>."
