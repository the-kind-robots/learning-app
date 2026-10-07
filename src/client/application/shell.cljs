(ns application.shell
  "The shell's status line, apart from the rest of the shell so that a page
   view can name it without requiring `application`, which requires every
   page view.")


(def status-region-id
  "The id of the status line. A control whose action ends in an
   announcement puts it into that action's payload, and the action passes
   it to `:effect/announce`."
  "app-status")


(def status-region
  "The one status line every screen announces through. It is rendered
   empty and always, so Replicant never diffs its text and a message
   written into it is announced."
  [:p.visually-hidden
   {:id   status-region-id
    :role "status"}])
