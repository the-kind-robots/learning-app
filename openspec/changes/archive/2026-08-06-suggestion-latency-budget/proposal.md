# Suggestion latency budget, measured

## Why

The 150 ms debounce was floored by list stability; #178 removed that floor (the list keeps its previous answer until the next arrives). #195 asked for the missing number: past the debounce, worker + SQLite + render cost 9–44 ms end to end (one-letter prefix worst, measured in headless Chrome against the stand).

## What changes

Debounce 150 → 100 ms: suggestions land ~110–145 ms after the typing pause, and fast typing (120–180 ms per key) still coalesces.

## Impact

One constant in `pages.home.effects` with the measurement in the comment.
