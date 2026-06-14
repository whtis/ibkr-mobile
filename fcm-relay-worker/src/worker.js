/**
 * FCM relay — a Cloudflare Worker that forwards a push to Firebase Cloud
 * Messaging (HTTP v1) on behalf of the backend.
 *
 * The backend runs on a NAS in mainland China and can't reach Google. This
 * Worker (on Cloudflare's edge, reachable from the NAS via the user's domain)
 * holds the Firebase service-account credentials, mints an OAuth token, and
 * calls FCM. The backend just POSTs { token, title, body, data } here with a
 * shared secret.
 *
 * Config:
 *   vars:    FCM_PROJECT_ID            (e.g. "bulltap")
 *   secrets: FCM_CLIENT_EMAIL          (service-account client_email)
 *            FCM_PRIVATE_KEY           (service-account private_key, PEM)
 *            RELAY_SECRET              (shared secret the backend sends)
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Public APK proxy: GET /apk/<tag>/<file.apk> -> the GitHub release asset,
    // cached at Cloudflare's edge. GitHub's release CDN is throttled from China;
    // CF<->GitHub is fast and CF<->China is fine, so the phone downloads the full
    // APK from here instead of timing out against GitHub directly.
    if (request.method === "GET" && url.pathname.startsWith("/apk/")) {
      const rest = url.pathname.slice("/apk/".length); // "<tag>/<file.apk>"
      if (!rest || rest.includes("..") || !rest.endsWith(".apk")) {
        return new Response("not found", { status: 404 });
      }
      const repo = env.APK_REPO || "whtis/ibkr-mobile";
      const gh = `https://github.com/${repo}/releases/download/${rest}`;
      const upstream = await fetch(gh, { cf: { cacheEverything: true, cacheTtl: 3600 } });
      const headers = new Headers();
      headers.set("Content-Type", "application/vnd.android.package-archive");
      headers.set("Cache-Control", "public, max-age=3600");
      const len = upstream.headers.get("Content-Length");
      if (len) headers.set("Content-Length", len);
      return new Response(upstream.body, { status: upstream.status, headers });
    }

    if (request.method !== "POST") return json({ ok: false, error: "method-not-allowed" }, 405);
    if (env.RELAY_SECRET && request.headers.get("Authorization") !== `Bearer ${env.RELAY_SECRET}`) {
      return json({ ok: false, error: "unauthorized" }, 401);
    }
    let payload;
    try {
      payload = await request.json();
    } catch {
      return json({ ok: false, error: "bad-json" }, 400);
    }
    const { token, title, body, data } = payload || {};
    if (!token) return json({ ok: false, error: "missing-token" }, 400);

    let accessToken;
    try {
      accessToken = await getAccessToken(env);
    } catch (e) {
      return json({ ok: false, error: "auth: " + e.message }, 502);
    }

    const message = {
      message: {
        token,
        notification: { title: title || "", body: body || "" },
        data: stringifyValues(data || {}),
        android: { priority: "high" },
      },
    };
    const resp = await fetch(
      `https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify(message),
      },
    );
    const text = await resp.text();
    if (resp.ok) return json({ ok: true });

    // Tell the backend when a token is dead so it can prune it.
    let unregistered = false;
    try {
      const err = JSON.parse(text);
      const status = err?.error?.status;
      const code = err?.error?.details?.find?.((d) => d.errorCode)?.errorCode;
      unregistered = status === "NOT_FOUND" || code === "UNREGISTERED";
    } catch {
      /* ignore parse errors */
    }
    return json({ ok: false, unregistered, status: resp.status, error: text.slice(0, 300) }, 502);
  },
};

let cachedToken = null; // { token, exp }

async function getAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.token;
  const jwt = await makeJwt(env, now);
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const j = await resp.json();
  if (!resp.ok) throw new Error(j.error_description || j.error || "token-exchange-failed");
  cachedToken = { token: j.access_token, exp: now + (j.expires_in || 3600) };
  return cachedToken.token;
}

async function makeJwt(env, iat) {
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: env.FCM_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat,
    exp: iat + 3600,
  };
  const enc = (o) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const signingInput = `${enc(header)}.${enc(claim)}`;
  const key = await importPrivateKey(env.FCM_PRIVATE_KEY);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${b64url(new Uint8Array(sig))}`;
}

async function importPrivateKey(pem) {
  const clean = pem
    .replace(/\\n/g, "\n")
    .replace(/-----[^-]+-----/g, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(clean), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function b64url(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function stringifyValues(obj) {
  const out = {};
  for (const k of Object.keys(obj)) out[k] = String(obj[k]);
  return out;
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
