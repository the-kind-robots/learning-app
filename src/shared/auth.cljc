(ns auth
  "The client↔server auth contract.")


(def token-cookie
  "The bearer-token cookie. The client derives it from device-db on every
   boot (ADR-0006); the server only ever reads it."
  "auth-token")
