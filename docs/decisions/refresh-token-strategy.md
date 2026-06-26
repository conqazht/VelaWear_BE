# ADR-001: Refresh Token Strategy — Cookie (SPA) + Body (Mobile)

## Status
Accepted

## Context

The system serves two types of clients:
- **SPA** (React/Vue/Angular) — runs in browser, vulnerable to XSS
- **Mobile app** (Android/iOS) — native app, no cookie support needed

We need a refresh token mechanism that is secure for browser-based SPA
while remaining practical for mobile clients.

## Options Considered

### Option A: Response Body for both
- Refresh token returned as JSON, stored in localStorage (SPA) or secure storage (mobile)
- Simple: one API, one flow
- Risk: SPA stores token in localStorage → XSS can steal it

### Option B: Cookie for SPA + Body for Mobile (CHOSEN)
- Refresh token set as `HttpOnly; Secure; SameSite=Lax` cookie
- SPA: browser auto-sends cookie, JS cannot read it → XSS-safe
- Mobile: sends refresh token in request body
- Backend checks both sources: cookie first, then body

### Option C: Response Body + in-memory storage
- SPA stores refresh token in JS memory (not localStorage)
- Safer than localStorage, but token lost on page refresh
- UX trade-off: user must re-login after every page refresh

## Decision
**Option B** — Dual-source refresh token.

## Implementation Design

### Login Response
```
POST /api/v1/auth/login

Response body:
{
  "statusCode": 200,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ..."     ← Mobile uses this
  }
}

Response cookie (set by backend):
Set-Cookie: refresh_token=eyJ...;
            HttpOnly;
            Secure;
            SameSite=Lax;
            Path=/api/v1/auth;
            Max-Age=259200
```

- `refreshToken` in body: for mobile clients
- `refresh_token` cookie: for SPA (browser auto-manages)
- Cookie `Path=/api/v1/auth`: cookie only sent to auth endpoints, not every request

### Refresh Endpoint
```
POST /api/v1/auth/refresh

Backend checks (in order):
1. Cookie `refresh_token` → if present, use this (SPA)
2. Request body `refreshToken` → if present, use this (Mobile)
3. Neither → return 401

Response: same as login (new accessToken + new refreshToken + new cookie)
```

### Logout Endpoint
```
POST /api/v1/auth/logout

Backend:
1. Invalidate refresh token in database
2. Clear cookie: Set-Cookie: refresh_token=; Max-Age=0; Path=/api/v1/auth
```

### Token Configuration
| Token | Expiration | Storage |
|-------|-----------|---------|
| Access token | 15 minutes | SPA: memory / Mobile: secure storage |
| Refresh token | 3 days | SPA: httpOnly cookie / Mobile: secure storage |

Refresh token expiration set to 3 days (not 7) to reduce risk window
if token is somehow compromised.

### Cookie Settings Explained
| Attribute | Value | Why |
|-----------|-------|-----|
| `HttpOnly` | true | JS cannot read → XSS-safe |
| `Secure` | true | HTTPS only → no MITM |
| `SameSite` | Lax | Prevents CSRF on POST, allows normal navigation |
| `Path` | /api/v1/auth | Cookie only sent to auth endpoints |
| `Max-Age` | 259200 | 3 days in seconds |

### CSRF Consideration
- `SameSite=Lax` blocks cross-origin POST requests → sufficient CSRF protection
- No additional CSRF token needed for this setup
- If `SameSite=None` is ever required (cross-domain), must add explicit CSRF protection

## Consequences

### Positive
- SPA: refresh token invisible to JS → XSS cannot steal it
- Mobile: standard JSON flow, no cookie handling needed
- Single `/auth/refresh` endpoint serves both clients
- `SameSite=Lax` provides CSRF protection without extra complexity

### Negative
- Backend logic slightly more complex (check cookie → then body)
- Testing needs to cover both flows (cookie-based and body-based)
- Local development needs HTTPS for `Secure` cookie (or disable in dev profile)

### Risks
- If SPA and API are on different domains, `SameSite=Lax` may block cookies
  → Mitigation: deploy on same domain or subdomain (api.example.com + app.example.com)
- Refresh token rotation not yet implemented
  → Mitigation: short expiration (3 days) + plan to add rotation later

## Files Affected
- `config/SecurityConfig.java` — CORS + cookie config
- `feature/auth/AuthController.java` — set/clear cookie
- `feature/auth/AuthServiceImpl.java` — dual-source token extraction
- `application.yml` — token expiration config