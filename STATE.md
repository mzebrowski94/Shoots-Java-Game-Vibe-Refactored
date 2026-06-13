# STATE.md — Living Architecture Reference

> Rewrite sections **in place** as the refactor progresses. Don't turn this into a
> history log — git commit messages already cover history. If a section grows past
> ~15-20 lines, either promote the entry to "Established Contracts" (permanent) or
> delete it (resolved). Target: whole file under ~80 lines.

## Package Map
_(empty — filled in starting with cluster 1)_

## Legacy Code Map
_(filled in by cluster 0; remove an entry once its class is migrated/deleted)_

## Established Contracts
_(interfaces / base classes / enums that later clusters must reuse as-is. Changing
one of these means updating every consumer in the same pass.)_

## Open Decisions / Backlog
_(short, forward-looking notes for future clusters — delete once resolved)_
