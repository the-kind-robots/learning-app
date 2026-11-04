# Invite mail Worker

Mails the operator an invite request (#650). The backend POSTs
`{"subject": …, "text": …, "html": …}` (`html` optional) with
`Authorization: Bearer <secret>`; the Worker
sends it from `invite@sprecha.de` with the `send_email` binding and answers
204.

## Setup

Email Routing must be on for `sprecha.de`, with the operator's address a
verified destination. Then, from this directory:

```bash
openssl rand -hex 32                          # the shared secret
npx wrangler secret put INVITE_MAIL_SECRET    # paste it
npx wrangler secret put OPERATOR_ADDRESS      # the verified destination
npx wrangler deploy                           # prints the Worker's URL
```

The backend reads the URL from `LEARNING_APP__INVITE_MAIL_URL` (in
`/etc/learning-app/environment`) and the secret from the systemd credential
`invite_mail_secret` (`admin-setup.sh` asks for it).

Check it from a shell:

```bash
curl -i -X POST "$URL" -H "Authorization: Bearer $SECRET" \
  -H 'Content-Type: application/json' -d '{"subject":"test","text":"test"}'
```

A 204 and a mail in the operator's inbox mean it works. A 502 means `send`
threw: the Worker's log (`npx wrangler tail`) names why. If it names
`E_SENDER_DOMAIN_NOT_AVAILABLE`, the domain is not yet onboarded for sending:
onboard `sprecha.de` under Email Service → Email Sending in the dashboard,
then check again.
