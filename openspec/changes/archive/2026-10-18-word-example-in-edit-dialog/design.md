## Context

Behaviour is in the delta specs. Design: the approved canvas of #598 (Nunito, the existing tokens).

## Decisions

- The lesson and the dialog read one rule for the example of a word, so what the dialog shows is what a lesson asks.
- Replacing an example stores the new one and then deletes the old ones, so the dialog can use the learner's ordinary writes and the result is the same on every device.
- An example the learner writes has no word-by-word breakdown; lessons already accept that.
- Nothing is written until the learner saves; closing the dialog drops the edits.
