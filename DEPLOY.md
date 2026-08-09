# Deploying CinemaSeat

Two-click deploy: **backend on Render**, **frontend on Vercel**.

The repo is already configured — you only need to sign in and click.

---

## Prerequisites

- GitHub account (the repo lives at `github.com/TISamin/CineCli`)
- Render account (free tier works): https://render.com/register
- Vercel account (free tier): https://vercel.com/signup

Total cost on free tier: **$0/month**.
Render's free Postgres expires after 90 days; the free Web Service sleeps after 15 min of inactivity.
For a production demo, upgrade to Render's Starter tier: **$14/month**.

---

## Part 1 — Backend on Render

### 1.1 Create the Blueprint

1. Sign in at https://dashboard.render.com
2. Click **New** → **Blueprint**
3. Select the repo `TISamin/CineCli`
4. Render will detect `render.yaml` and show 3 services to be created:
   - `cinecli-db` (Postgres 16)
   - `cinecli-gateway` (mock payment gateway)
   - `cinecli-api` (Spring Boot API)
5. Click **Apply** — Render builds and deploys all three.

### 1.2 Wire the gateway URL into the API

After the gateway service finishes deploying (3-5 minutes):

1. Open the `cinecli-gateway` service → copy its URL (e.g. `https://cinecli-gateway.onrender.com`)
2. Open the `cinecli-api` service → **Environment** tab
3. Set `GATEWAY_URL=https://cinecli-gateway.onrender.com` (replace with your actual URL)
4. Click **Save Changes** — Render redeploys the api service.

### 1.3 Verify the backend

```bash
curl https://cinecli-api.onrender.com/health
# → {"status":"UP"}

curl https://cinecli-api.onrender.com/api/movies
# → list of 2 movies
```

You should also see the keystone run logs in the `cinecli-api` service → **Logs** tab.

### 1.4 Run the keystone against the live URL

```bash
k6 run -e BASE_URL=https://cinecli-api.onrender.com tests/load/scenario-a.js
```

Expected:
```
successes (HTTP 200): 1
conflicts (HTTP 409): 99
other (HTTP 4xx/5xx): 0
oversell: 0 (must be 0)
```

---

## Part 2 — Frontend on Vercel

### 2.1 Import the project

1. Sign in at https://vercel.com/dashboard
2. Click **Add New** → **Project**
3. Import the repo `TISamin/CineCli`
4. **Framework preset:** Other
5. **Root directory:** click "Edit" → type `frontend` → confirm
6. Click **Deploy**

Vercel will detect `frontend/vercel.json` and:
- serve the static HTML/CSS/JS
- proxy `/api/*` requests to `https://cinecli-api.onrender.com/api/*`

### 2.2 Update the proxy URL (if your api URL is different)

If you renamed the Render service, edit `frontend/vercel.json`:

```json
"destination": "https://<your-actual-api-url>.onrender.com/api/$1"
```

Then commit and push — Vercel auto-redeploys.

### 2.3 Verify

Open `https://<your-project>.vercel.app` in a browser:
- Movies page lists 2 movies
- Click a showtime → seat picker loads
- Hold a seat → checkout shows booking ref
- Pay → after ~5s, confirmation shows ✓ Confirmed

---

## Part 3 — One-box "all on Render" (simpler)

If you don't want to use Vercel, skip Part 2 and just open the backend:

```
https://cinecli-api.onrender.com/
```

The Spring Boot service serves the same frontend at `/` (no extra config needed).

This is what we recommend for the demo — it's one URL, one dashboard, no CORS to think about.

---

## Part 4 — Custom domains

### Render

1. Open `cinecli-api` → **Settings** → **Custom Domain**
2. Type your domain (e.g. `api.cinemaseat.example.com`)
3. Add the CNAME record at your DNS provider.

### Vercel

1. Open the project → **Settings** → **Domains**
2. Type your domain (e.g. `cinemaseat.example.com`)
3. Add the A record at your DNS provider.

---

## Part 5 — Teardown

To stop paying (or stop the free-tier sleep):

- Render dashboard → select each service → **Settings** → **Delete Service**
- Vercel dashboard → select the project → **Settings** → **Delete Project**

The Postgres instance is the only one that costs money on the Starter tier. Delete it last.