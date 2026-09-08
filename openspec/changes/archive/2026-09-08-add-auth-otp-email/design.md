> **SUPERSEDED (2026-07-16):** Key model và verified-marker dưới đây không còn là
> thiết kế hiện hành. Xem `../harden-otp-auth-v1/design.md` và
> `docs/OTP_SECURITY_FLOW_VI.md` cho OTP v2/proof token.

## Context

The backend already exposes public auth endpoints under `/api/v1/auth` and uses Redis for security-sensitive runtime state such as access-token blacklisting, refresh-token sessions, and RBAC cache. It does not yet have a transactional email provider or a reusable OTP flow for account-sensitive operations.

This change introduces email OTP as phase 1 of the broader notification/payment roadmap. OTP email must support `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL` without coupling auth logic directly to Resend-specific APIs.

## Goals / Non-Goals

**Goals:**
- Add a reusable OTP request/verify flow for registration, password recovery, and email change.
- Send OTP emails through Resend using a provider abstraction that can later support SendGrid or another provider.
- Store OTP runtime state in Redis with TTL, resend cooldown, and failed-attempt limits.
- Keep OTP generation, hashing, verification, rate limiting, and email delivery in the backend.
- Provide API contracts that frontend can call without handling provider secrets or OTP internals.

**Non-Goals:**
- Do not implement payment, order, or delivery notification emails in this phase.
- Do not add SendGrid in the first implementation.
- Do not send email directly from frontend code.
- Do not store raw OTP codes in PostgreSQL.
- Do not replace the existing password login/refresh-token model.

## Decisions

### 1. Resend-first provider abstraction
- **Decision:** Add an internal `EmailProvider` abstraction and implement `ResendEmailProvider` first.
- **Rationale:** Resend is simple for transactional email and fits the current project size, while an interface keeps auth and future order/payment notifications independent from one vendor.
- **Alternatives considered:**
  - Use Spring SMTP directly: simpler dependency surface, but less aligned with transactional email provider APIs.
  - Integrate Resend and SendGrid immediately: more flexible, but unnecessary complexity for a personal sandbox-first project.

### 2. Redis-backed OTP state
- **Decision:** Store OTP state in Redis, not PostgreSQL, using bounded TTL keys.
- **Rationale:** OTPs are short-lived runtime secrets. Redis already exists in the project and is a better fit for expiration, cooldown, and attempts than a durable table.
- **Key model:**
  - `auth:otp:{purpose}:{normalizedEmail}` stores the hashed OTP and metadata with a short TTL.
  - `auth:otp:cooldown:{purpose}:{normalizedEmail}` blocks immediate resend.
  - `auth:otp:attempts:{purpose}:{normalizedEmail}` tracks failed verification attempts and expires with the OTP window.
- **Alternatives considered:**
  - PostgreSQL OTP table: auditable, but adds cleanup burden and increases risk of retaining secrets longer than needed.
  - In-memory map: easy locally, but fails across restarts and multiple instances.

### 3. Purpose-scoped OTPs
- **Decision:** OTPs are scoped by purpose and normalized email. Supported purposes are `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL`.
- **Rationale:** Purpose scoping prevents a code requested for one workflow from being reused for another.
- **Behavior:** Verification creates a short-lived verified marker such as `auth:otp:verified:{purpose}:{normalizedEmail}` that the final account action consumes.

### 4. Backend-owned security controls
- **Decision:** Backend generates six-digit numeric OTPs using a secure random generator, stores only hashes, enforces cooldown and attempts, and returns generic success-style messages where user enumeration matters.
- **Rationale:** The frontend cannot be trusted with provider keys, OTP generation, or rate-limit logic.
- **Policy defaults:** OTP TTL 5 minutes, resend cooldown 60 seconds, maximum failed attempts 5. Exact values are configurable.

### 5. Auth flow integration
- **Decision:** OTP request/verify APIs are added first, then supported auth/account actions require a verified marker before finalizing sensitive changes.
- **Rationale:** This keeps the OTP module reusable and lets tests exercise OTP behavior independently from registration/password/email update side effects.
- **Flow examples:**
  - Register: request `REGISTER` OTP, verify OTP, then create account using verified state.
  - Forgot password: request `FORGOT_PASSWORD` OTP, verify OTP, then reset password using verified state.
  - Change email: authenticated user requests `CHANGE_EMAIL` OTP for the new email, verifies it, then finalizes email update.

## Risks / Trade-offs

- **[Risk] Email provider outage blocks OTP delivery.** Mitigation: map provider failure to a clear service error, log provider correlation details, and keep the provider behind an interface for future fallback.
- **[Risk] OTP brute force attempts.** Mitigation: hash OTPs, enforce failed-attempt limits, expire OTP keys quickly, and delete OTP state after successful verification.
- **[Risk] User enumeration through OTP request responses.** Mitigation: return consistent responses for supported public flows where revealing account existence is not required.
- **[Risk] Redis outage blocks OTP verification.** Mitigation: return service-unavailable behavior rather than silently accepting or bypassing OTP.
- **[Risk] Verified marker replay.** Mitigation: keep verified marker TTL short and consume/delete it when the final action succeeds.

## Migration Plan

1. Add Resend configuration and email provider abstraction.
2. Add OTP request/verify DTOs, purpose enum, Redis store, and service.
3. Add public OTP controller endpoints under `/api/v1/auth/otp`.
4. Integrate verified OTP consumption with registration, forgot password, and change email flows.
5. Add tests for OTP request, verify, cooldown, attempts, TTL expiry, provider failure, and supported purposes.
6. Update API docs and environment examples after implementation.

Rollback is configuration-safe: disabling the new OTP endpoints or feature flag leaves the existing password/JWT auth flow unchanged.

## Open Questions

- Should registration require OTP before user creation, or allow creating an unverified user and then marking email verified?
- Should `CHANGE_EMAIL` verify the new email only, or require a second confirmation against the current email later?
- Should forgot-password reset be completed by a short-lived reset token after OTP verification, or by passing the new password directly with verified OTP state?
