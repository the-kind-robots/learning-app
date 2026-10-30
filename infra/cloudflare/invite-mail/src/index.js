// Mails the operator an invite request. The backend POSTs {subject, text}
// with the shared secret; the Worker sends it from SENDER to OPERATOR_ADDRESS
// through the send_email binding.
const encoder = new TextEncoder();

function authorized(request, secret) {
  const given = encoder.encode(request.headers.get("Authorization") ?? "");
  const expected = encoder.encode(`Bearer ${secret}`);
  return given.byteLength === expected.byteLength
    && crypto.subtle.timingSafeEqual(given, expected);
}

export default {
  async fetch(request, env) {
    // Without them every caller would be checked against "Bearer undefined".
    if (!env.INVITE_MAIL_SECRET || !env.OPERATOR_ADDRESS) {
      console.error("invite-mail: INVITE_MAIL_SECRET or OPERATOR_ADDRESS is not set");
      return new Response(null, { status: 500 });
    }
    if (request.method !== "POST") {
      return new Response(null, { status: 405 });
    }
    if (!authorized(request, env.INVITE_MAIL_SECRET)) {
      return new Response(null, { status: 401 });
    }
    const message = await request.json().catch(() => null);
    if (typeof message?.subject !== "string" || typeof message?.text !== "string") {
      return new Response(null, { status: 400 });
    }
    try {
      await env.MAIL.send({
        from: { email: env.SENDER, name: "Sprecha" },
        to: env.OPERATOR_ADDRESS,
        subject: message.subject,
        text: message.text,
      });
    } catch (error) {
      // The backend only needs to know the mail did not go; the code says why.
      console.error("invite-mail: send failed", error.code, error.message);
      return new Response(null, { status: 502 });
    }
    return new Response(null, { status: 204 });
  },
};
