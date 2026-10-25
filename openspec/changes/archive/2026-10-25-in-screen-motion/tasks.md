## 1. Behaviour first

- [x] 1.1 Browser tests for every new scenario of `screen-transitions`, phone and desktop; catalog regenerated

## 2. Implementation

- [x] 2.1 The answer panel rises; the line with the learner's answer shakes on a wrong one
- [x] 2.2 The word dialog rises; its backdrop fades in
- [x] 2.3 Home keeps the ids it added in `:home/added`; opening the word list takes them as `:words/added` and highlights them; rows keyed by id
- [x] 2.4 Creating a collection runs inside a gap-opening transition
- [x] 2.5 All CSS motion under the phone width with motion allowed

## 3. Verify

- [x] 3.1 New and existing browser suites pass; the lesson stays still while its panel rises (`.lesson` clips instead of scrolling)
- [x] 3.2 Mid-animation frames at phone width read as in the canvas
