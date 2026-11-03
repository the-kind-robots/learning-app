## 1. Implementation

- [x] 1.1 Browser tests: mouse drag, folder drag, «Всё подряд» stays first, new collection last, colour kept; touch: long press that moves, drag from the editing state
- [x] 1.2 Domain and use case: a new collection takes the next position; a drop writes the positions of the new order
- [x] 1.3 Order and colour: tiles by position, colour by the order they were made in
- [x] 1.4 Drag on the themes screen: the tile follows the pointer, a caret, the slide after the drop; no scroll while dragging

## 2. Verification

- [x] 2.1 Unit and browser suites pass; touch drags proven through CDP touch events in headless Chrome
- [x] 2.2 A drag by finger on the owner's phone, on the dev stand served from this branch. A recorder there showed the scroll Chrome started from the moves before the lift; the fix cancels every move of a touch on a target in its editing state
