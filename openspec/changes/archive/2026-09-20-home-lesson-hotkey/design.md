# Design: home-lesson-hotkey

The requirement is in `specs/home-add-form-focus/spec.md`. This note records
only the choices the implementation makes and why.

## Why the handler is on the page root, not on the fields

The home screen has two textareas, a heading that can be edited in place and
three buttons. A hotkey that must work wherever the focus sits is one handler
on `[:div.home ...]` and keydown bubbling, or five handlers that have to stay
in step. It is also what scopes the hotkey: the element exists only while the
home page is rendered, so nothing has to add and remove a `window` listener or
ask which page is current. A `window`/`document` listener would have to answer
both questions itself and would outlive the screen.

The bound is what bubbling gives: the keystroke is heard when focus is inside
the home page's own element. On a fine pointer the screen already focuses the
word field on mount, and every control that can take focus on the screen is
inside that element.

## Why the predicate comes from the presenter

The action needs to know whether the screen is offering a lesson at all. That
is the same `empty-vocab?` the footer's `hidden` is rendered from, and the view
does not compute predicates in this repo. The view passes what the presenter
already gives it, so the hotkey and the footer cannot drift apart: whatever
hides the button also disarms the keystroke.

## `Alt` among the screen's other Enter keys

Three Enter meanings already live on this screen: bare `Enter` on the word
field, `Ctrl`/`Cmd`+`Enter` on the translation field, `Enter` on the collection
heading. `Ctrl`/`Cmd`+`Enter` tests its own modifiers, so `Alt`+`Enter` never
submits the form.

The word field's handler tests no modifier — it acts on `Enter` — so on
`Alt`+`Enter` it still runs first, in the target phase, and moves focus to the
translation field or picks the highlighted suggestion before the event reaches
the page root. Neither adds a word, and the navigation that follows replaces
the screen; coming back to home resets the form through `:action/show-home`.
Left as it is rather than threading `alt?` through the field handler for a
state that is discarded a frame later.

`:effect/prevent-default` on the page handler stops the browser's own action
for the keystroke. It does not stop the field handlers: they are on the same
event, earlier in its path.
