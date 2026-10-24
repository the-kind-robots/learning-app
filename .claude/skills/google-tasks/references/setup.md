# Google Tasks — one-time setup

Done once, by the account owner. No Google Tasks CLI ships its own OAuth client id, so the
Cloud Console step below is unavoidable whatever tool is used.

Roughly ten minutes. At the end, `doctor` prints all-ok and `pull` returns your notes.

## 1. Install oauth2l

`oauth2l` is Google's official OAuth CLI. Its GitHub releases carry no binaries — the GCS
bucket is the source:

```bash
mkdir -p ~/.local/bin
curl -sL https://storage.googleapis.com/oauth2l/latest/linux_amd64.tgz | tar xz
install -m 0755 linux_amd64/oauth2l ~/.local/bin/oauth2l
rm -rf linux_amd64
```

`~/.local/bin` must be on `PATH`. Check:

```bash
command -v oauth2l
```

`jq` and `curl` are also required and are usually already installed.

## 2. Create the OAuth client

In the [Google Cloud Console](https://console.cloud.google.com/), signed in as the account
whose tasks you want to read:

1. **Create or pick a project.** Any project works; a personal one named for this is fine.
2. **Enable the Tasks API** — *APIs & Services -> Library -> Google Tasks API -> Enable*.
3. **Configure the consent screen** — *APIs & Services -> OAuth consent screen*. User type
   **External** (an *Internal* screen exists only for Workspace organizations). Fill in the
   app name and your own email; nothing else is needed.
4. **Add the scope** `https://www.googleapis.com/auth/tasks`, and add your own Google account
   as a test user.
5. **Create the client** — *Credentials -> Create credentials -> OAuth client ID*, type
   **Desktop app**. The type matters: a Desktop app client gets `http://localhost` as its
   first redirect URI, and that is what activates the loopback flow oauth2l uses. A "Web
   application" client will not work without redirect URIs configured by hand.
6. **Download the client JSON** (*Download JSON* on the client you just created).

## 3. Publish the app, or re-consent every week

This is the step that is easy to skip and annoying to debug.

While the consent screen is in **Testing**, Google issues a refresh token that **expires after
7 days**. The exemption for long-lived tokens covers only the name/email/profile scopes; the
`tasks` scope is not among them. A week after setup, `doctor` will report no usable token and
`auth` will have to be run again.

Set the app to **In production** — *OAuth consent screen -> Publish app*. For a single
personal user the two consequences are irrelevant:

- The app is unverified and asks for a sensitive scope, so consent shows a one-time
  "Google hasn't verified this app" interstitial. Click **Advanced -> Go to app (unsafe)**.
  Verification is only needed to remove that screen for other people.
- An unverified app is capped at 100 users. You are one.

Two more limits worth knowing, neither reachable in normal use:

- A refresh token is revoked after **six months** without use.
- Google keeps at most **100 refresh tokens per account per client id**; issuing the 101st
  silently invalidates the oldest.

## 4. Put the files where the script looks

```bash
mkdir -p ~/.config/learning-app
mv ~/Downloads/client_secret_*.json ~/.config/learning-app/google-tasks-client.json
chmod 700 ~/.config/learning-app
chmod 600 ~/.config/learning-app/google-tasks-client.json
```

Optional settings file, if the default list is not the one you capture into:

```bash
cp .skills/google-tasks/references/google-tasks.env.example \
   ~/.config/learning-app/google-tasks.env
chmod 600 ~/.config/learning-app/google-tasks.env
```

Edit it and set `GOOGLE_TASKS_LIST` to the list title you use. Both variables can also be set
in the environment, which wins over the file. `@default` means the account's default list, so
the file can be left out entirely if that is where the notes are.

Nothing here belongs in the repository. The repository is public.

## 5. Consent

```bash
bash .skills/google-tasks/scripts/google_tasks.sh auth
```

It prints a URL instead of opening a browser — auto-open is unreliable under WSL2. Paste it
into a browser, approve (through the unverified-app interstitial, if the app is published),
and the browser is redirected back to a `localhost` port the command is listening on.

The access token oauth2l prints is redacted before it reaches your terminal. The refresh token
is cached by oauth2l in `~/.oauth2l`, which it creates world-readable — tighten it:

```bash
chmod 600 ~/.oauth2l
```

`oauth2l` exits 0 even when consent never completed, so `auth` verifies the result itself and
tells you which happened.

## 6. Check

```bash
bash .skills/google-tasks/scripts/google_tasks.sh doctor
bash .skills/google-tasks/scripts/google_tasks.sh lists
bash .skills/google-tasks/scripts/google_tasks.sh pull
```

`doctor` should print all-ok. `lists` gives the ids and titles of your task lists, which is
where the value for `GOOGLE_TASKS_LIST` comes from.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| `doctor`: no usable token, about a week after setup | The consent screen is still in *Testing*. See step 3. |
| `doctor`: no usable token, right after setup | Consent did not complete. Re-run `auth` and follow the URL to the end. |
| `HTTP 403: Google Tasks API has not been used in project ... before or it is disabled` | Step 2.2 was skipped, or you enabled it in a different project. |
| `HTTP 403: Request had insufficient authentication scopes` | An old cached token from another scope. `oauth2l reset`, then `auth`. |
| `HTTP 400: redirect_uri_mismatch` during consent | The client is not of type *Desktop app*. Create a new one, step 2.5. |
| `no task list matches "..."` | The title differs from the phone's display. Take the exact title or the id from `lists`. |
| `pull` returns notes already dealt with | They were never closed at the source. Mark them with `done <task-id>`. |
