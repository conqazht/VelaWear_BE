## 1. OTP Proof and Atomic Redis

- [x] 1.1 Replace email/purpose verify contract with challenge/proof DTOs
- [x] 1.2 Add domain-separated HMAC secret and OTP v2 key model
- [x] 1.3 Add reserve, publish, verify/issue and consume Lua scripts
- [x] 1.4 Bind `CHANGE_EMAIL` to actor and add forgot-password decoy
- [x] 1.5 Consume proof in register/reset/change-email final actions

## 2. Abuse Controls and Trusted IP

- [x] 2.1 Add configurable multi-dimensional sliding-window limiter
- [x] 2.2 Apply global/IP filter and normalized service dimensions
- [x] 2.3 Add stable 429 error/Retry-After contract
- [x] 2.4 Resolve client IP only from servlet trusted-proxy state
- [x] 2.5 Reject unsafe NATIVE proxy startup config and group IPv6 `/64`

## 3. Revoke All Sessions

- [x] 3.1 Add Flyway V20 `users.security_version`
- [x] 3.2 Add version to access JWT, refresh JWT and Redis session
- [x] 3.3 Compare authenticated JWT version with PostgreSQL
- [x] 3.4 Add Redis JTI index by user and after-commit cleanup
- [x] 3.5 Revoke all on password reset/change, email change and role change

## 4. Monitoring

- [x] 4.1 Add server-side request correlation filter
- [x] 4.2 Add bounded security events and Micrometer counters/timers
- [x] 4.3 Protect `/actuator/metrics/**` with `ROLE_ADMIN`
- [x] 4.4 Enable structured Logstash output in production

## 5. Client, Tests and Documentation

- [x] 5.1 Update frontend challenge/proof flow and sensitive-change logout behavior
- [x] 5.2 Verify Redis/PostgreSQL concurrency, fail-closed and proxy/session cases
- [x] 5.3 Run full backend and frontend verification suites
- [x] 5.4 Publish Vietnamese operations documentation, ADR and API/context updates
