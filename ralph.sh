#!/bin/bash
# Outer driver for ralph-once.sh.
# Usage: ./ralph.sh [max_iterations]
set -e

MAX_ITER=${1:-20}
PREV_DONE=-1
STALL=0

for i in $(seq 1 "$MAX_ITER"); do
  echo "===== Iteration $i/$MAX_ITER ====="

  if ! OUTPUT=$(./ralph-once.sh 2>&1); then
    echo "$OUTPUT"
    echo "ralph-once.sh exited with an error - stopping for manual review."
    exit 1
  fi
  echo "$OUTPUT"

  if echo "$OUTPUT" | grep -q '<promise>COMPLETE</promise>'; then
    echo "All clusters complete after $i iteration(s)."
    exit 0
  fi

  DONE=$(grep -o '\[x\]' RefactorPlan.md | wc -l)
  if [ "$DONE" -eq "$PREV_DONE" ]; then
    STALL=$((STALL + 1))
  else
    STALL=0
  fi
  PREV_DONE=$DONE

  if [ "$STALL" -ge 2 ]; then
    echo "No new [x] checkboxes for $STALL iteration(s) in a row - stopping for manual review."
    exit 1
  fi
done

echo "Reached max iterations ($MAX_ITER) without completing all clusters."
