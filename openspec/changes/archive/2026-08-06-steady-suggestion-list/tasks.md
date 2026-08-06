# Tasks

- [x] 1. Drop the `:home/suggestions` reset from `:action/update-word`; keep the word save and translation clear
- [x] 2. Trace every remaining `:home/suggestions` write and confirm each clearing flow survives (answer replace, empty prefix, blur, Escape, pick, submit)
- [x] 3. Node tests in `test/client/pages/home_test.cljs`: keystroke keeps the list, answer replaces it, emptied input clears it, submit clears it, pick from a kept list uses the entry's own data
- [x] 4. `npx shadow-cljs compile app node-test` 0 warnings; `node target/node-tests.js` 149 tests / 343 assertions (baseline 144/331), 0 failures
- [ ] 5. Visual steadiness while typing — pends the shared dev stand
