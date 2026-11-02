# Metro Booking System – Endpoints and Changes

This file explains how the system works, lists every endpoint, and records what was added in this round of changes.

---

## 1. How the system is built

| Service | Port | What it does |
|---|---|---|
| eureka-server | 8761 | Service registry. Every service registers here. |
| api-gateway | 8080 | Single entry point. Checks the JWT, checks admin role, routes to services. |
| auth-service | 8082 | Register, login, Google login, email verify, password reset, users and roles. |
| station-service | 8081 | Stations, lines, segments, fare slabs, shortest route + fare (Dijkstra). |
| booking-service | 8083 | Bookings, cancel, tickets (QR code), gate scan. |
| payment-service | 8084 | Razorpay orders, verify, webhook, refunds. |

The frontend only talks to the gateway. Services talk to each other in two ways:

- **Feign (HTTP):** booking-service calls `GET /route` on station-service to get the path and fare.
- **Kafka (events):**

| Topic | Sent by | Read by | Meaning |
|---|---|---|---|
| `booking-created` | booking | payment | New booking, create a payment + Razorpay order |
| `payment-success` | payment | booking | Money received, confirm the booking |
| `payment-failed` *(new)* | payment | booking | User gave up / checkout failed, mark booking FAILED |
| `refund-required` | booking | payment | Refund this booking (reason: `BOOKING_EXPIRED_AFTER_PAYMENT`, `USER_CANCELLED`) |
| `refund-completed` | payment | booking | Razorpay accepted the refund, mark booking REFUNDED |

### Booking status flow

```
PENDING ──payment success──> CONFIRMED ──gate scan──> USED
   │                            │
   │                            └─user cancels──> REFUND_REQUESTED ──refund done──> REFUNDED
   ├─user cancels──> CANCELLED
   ├─15 min passed──> EXPIRED
   └─payment failed─> FAILED

CANCELLED / EXPIRED / FAILED + late payment ──> PAID_AFTER_EXPIRY ──refund done──> REFUNDED
CONFIRMED + admin refund from payment-service ─────────────────────> REFUNDED
```

### Payment status flow

```
CREATED ──verify / webhook──> SUCCESS ──refund──> REFUND_PENDING ──> REFUNDED
   └──user gave up──> FAILED ──late verify / webhook (money moved)──> SUCCESS (then refunded)
```

---

## 2. All endpoints (through the gateway, port 8080)

**Access:** Public = no token. User = any logged-in user. Admin = token with role `ADMIN`.

### Auth service

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/auth/register` | Public | Sends a verification email |
| GET | `/auth/verify?token=` | Public | Link from the email |
| POST | `/auth/login` | Public | Returns `{ "token": "..." }` |
| POST | `/auth/resend-verification` | Public | `{ "email" }` |
| POST | `/auth/forgot-password` | Public | **New.** `{ "email" }`. Always the same reply, so it does not reveal which emails exist |
| POST | `/auth/reset-password` | Public | **New.** `{ "token", "newPassword" }`. Link is valid for 30 minutes |
| GET | `/auth/me` | User | **New.** Your profile (id, email, name, role, provider, emailVerified) |
| POST | `/auth/change-password` | User | **New.** `{ "currentPassword", "newPassword" }`. Not allowed for Google-only accounts |
| GET | `/auth/admin/users` | Admin | **New.** All users |
| PATCH | `/auth/admin/users/{id}/role` | Admin | **New.** `{ "role": "USER" \| "ADMIN" }`. You cannot change your own role |
| GET | `/oauth2/authorization/google` | Public | Start Google login |
| GET | `/login/oauth2/code/google` | Public | Google callback. **Changed:** now redirects to the frontend `…/oauth-success?token=…` (or `?error=google_email_not_verified`) instead of returning JSON |

### Station service

Reads need a logged-in user (gateway rule). Writes (POST, PUT, PATCH, DELETE) need Admin.

| Method | Path | Notes |
|---|---|---|
| GET | `/stations` | All stations |
| GET | `/stations/{id}` | **New** |
| POST | `/stations` | `{ "name", "code" }` |
| PUT | `/stations/{id}` | **New.** Same body as POST |
| DELETE | `/stations/{id}` | **Changed:** 404 if missing, 409 if a segment still uses it |
| GET | `/lines` | All lines |
| GET | `/lines/{id}` | **New** |
| GET | `/lines/{id}/stations` | **New.** Stations on that line |
| POST | `/lines` | `{ "name", "color" }` |
| PUT | `/lines/{id}` | **New** |
| DELETE | `/lines/{id}` | **Changed:** 404 if missing, 409 if segments use it |
| GET | `/segments` | All segments |
| GET | `/segments/{id}` | **New** |
| POST | `/segments` | `{ "stationAId", "stationBId", "lineId", "distanceKm" }`. **Changed:** now validated, stations must be different |
| PUT | `/segments/{id}` | **New** |
| DELETE | `/segments/{id}` | **Changed:** 404 if missing |
| GET | `/fare-slabs` | All slabs |
| GET | `/fare-slabs/{id}` | **New** |
| POST | `/fare-slabs` | `{ "minDistanceKm", "maxDistanceKm", "fare" }`. **Changed:** min must be less than max, and slabs cannot overlap (touching edges like 0–5 and 5–10 are fine) |
| PUT | `/fare-slabs/{id}` | **New.** Same checks |
| DELETE | `/fare-slabs/{id}` | **Changed:** 404 if missing |
| GET | `/route?from=&to=` | Shortest path, distance and fare |

### Booking service

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/bookings` | User | `{ "fromStationId", "toStationId" }` → PENDING booking |
| GET | `/bookings` | User | My bookings |
| GET | `/bookings/{id}` | User | Frontend polls this after payment |
| PATCH | `/bookings/{id}/cancel` | User | **Changed:** PENDING → CANCELLED (as before). CONFIRMED → REFUND_REQUESTED and a full refund is started |
| GET | `/bookings/ticket/{ticketCode}` | Admin | **New.** Gate device looks up a ticket from the QR code |
| PATCH | `/bookings/ticket/{ticketCode}/use` | Admin | **New.** Gate scan. CONFIRMED → USED. A second scan gives 409 |
| GET | `/bookings/admin?status=` | Admin | **New.** All bookings, optional status filter (e.g. `CONFIRMED`) |

### Payment service

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/payments/booking/{bookingId}` | User | Razorpay order details for Checkout |
| POST | `/payments/verify` | User | `{ "razorpayOrderId", "razorpayPaymentId", "razorpaySignature" }` |
| POST | `/payments/failed` | User | **New.** `{ "razorpayOrderId", "reason" (optional) }`. Call this when the user closes Checkout after a failure. Booking becomes FAILED |
| POST | `/payments/webhook` | Public (signed) | **New.** Razorpay webhook. Checked with the `X-Razorpay-Signature` header |
| GET | `/payments/admin?status=` | Admin | **New.** All payments, optional status filter |
| POST | `/payments/admin/booking/{bookingId}/refund` | Admin | **New.** Manual full refund of a successful payment |

### Other

| Path | Notes |
|---|---|
| `/swagger-ui.html` | Swagger for all services, on the gateway (dev only) |
| `/actuator/health` | **New on all services** (auth, booking, gateway added; station and payment had it) |
| `http://localhost:8761` | Eureka dashboard |

---

## 3. Razorpay webhook

**Why:** if the user pays and then closes the browser before `/payments/verify` runs, the money is taken but the booking would expire. The webhook lets Razorpay tell us directly.

**What it does:**

1. Checks the signature on the raw body with the webhook secret. If it's wrong, it returns 400 and nothing else happens.
2. Only handles `payment.captured`. Other events are ignored. `payment.failed` is ignored on purpose, because the user can retry inside the same Checkout.
3. Finds the payment by `order_id`. Unknown orders are ignored, because the Razorpay account may be shared with other apps.
4. Checks the amount matches what we stored. A mismatch is logged for manual review.
5. Marks the payment SUCCESS and sends `payment-success`, exactly like `/payments/verify`. If both arrive, the second one does nothing.

**Setup in the Razorpay Dashboard** (Settings → Webhooks → Add):

- URL: `https://metro-api.algoforge.work/payments/webhook`
- Secret: any long random string. Put the same value in `RAZORPAY_WEBHOOK_SECRET`.
- Event: `payment.captured` (and make sure auto-capture is on, which is the default for orders).

> **Domain:** `api.algoforge.work` is already used by another project, so this backend uses **`metro-api.algoforge.work`**. The domain is not hard-coded in Java. To use a different subdomain, change the webhook URL in the Razorpay Dashboard and the `APP_GATEWAY_BASE_URL` value.

---

## 4. Configuration needed

Each service reads its own `.env` (properties format) or environment variables.

| Variable | Service | Required | Example / notes |
|---|---|---|---|
| `RAZORPAY_WEBHOOK_SECRET` | payment | **Yes (new)** | Same secret as in the Razorpay Dashboard. Payment won't start without it |
| `ADMIN_EMAIL` | auth | Optional (new) | First admin account, created at startup if it doesn't exist |
| `ADMIN_PASSWORD` | auth | Optional (new) | Password for that admin |
| `APP_FRONTEND_URL` | auth, gateway | Optional (new) | Default `http://localhost:3000`. Used for the reset-password link, the Google redirect and CORS |
| `APP_GATEWAY_BASE_URL` | auth | Optional (new) | Default `http://localhost:8080`. In production: `https://metro-api.algoforge.work` (used in the verify-email link) |
| `DB_PASSWORD`, `JWT_SECRET`, `MAIL_*`, `GOOGLE_CLIENT_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | as before | Yes | Unchanged |

**Google Console:** the authorised redirect URI for production is `https://metro-api.algoforge.work/login/oauth2/code/google`.

**Frontend pages expected:** `/reset-password?token=…` and `/oauth-success?token=…` (or `?error=…`).

---

## 5. Database notes (one-time, existing databases)

`ddl-auto=update` adds the new columns by itself (`users.password_reset_token`, `users.password_reset_expiry`, `bookings.used_at`, `payments.failure_reason`).

It does **not** change existing columns. If `bookings.status` was created as a MySQL `ENUM`, the new statuses (`USED`, `REFUND_REQUESTED`) will fail to save. Run this once on the booking database:

```sql
ALTER TABLE bookings MODIFY status VARCHAR(30);
```

The entity now says `varchar(30)`, so new databases get the right type and future statuses won't need this again.

---

## 6. What changed, per service

### api-gateway
- New public paths: `/auth/forgot-password`, `/auth/reset-password`, `/payments/webhook`, `/actuator/health`.
- New admin-only paths, for any method: `/auth/admin`, `/bookings/admin`, `/payments/admin`, `/bookings/ticket`.
- CORS origin now comes from `APP_FRONTEND_URL`. Actuator added.

### auth-service
- `AdminSeeder` creates the first admin from `ADMIN_EMAIL` / `ADMIN_PASSWORD`. Before this, there was no way to get an admin without editing the database.
- New endpoints: `/me`, forgot/reset/change password, admin user list, change role.
- Role checks read the role from the database, not just from the token.
- Google login now redirects to the frontend with the token.
- New exceptions: `UnauthorizedException`, `AdminAccessRequiredException`, `UserNotFoundException`, `InvalidRoleChangeException`, `InvalidResetTokenException`, `PasswordChangeNotAllowedException`.

### station-service
- Get-by-id and update (PUT) for stations, lines, segments, fare slabs. Added `GET /lines/{id}/stations`.
- Deletes return 404 or 409 with a clear message instead of a raw database error.
- Segment validation is fixed (it had wrong messages and `@Valid` was missing). Fare slab overlap check added.
- New exceptions: `StationInUseException`, `LineInUseException`, `SegmentNotFoundException`, `InvalidSegmentException`, `FareSlabNotFoundException`, `InvalidFareSlabException`.

### booking-service
- New statuses: `USED`, `REFUND_REQUESTED`. New column `usedAt`.
- Cancel works for CONFIRMED bookings too, with a refund. If the booking gets confirmed at the same moment the user cancels, it takes the refund path.
- Gate scanning: ticket lookup and "use ticket", safe against double scans.
- Admin booking list.
- Listens to `payment-failed`, then PENDING → FAILED.
- `refund-completed` now also closes REFUND_REQUESTED and CONFIRMED (admin refund) bookings.
- Refund retry job also retries user-cancel refunds.
- Fix: a bad booking request returned 503. It now returns 400.
- Moved `booking.refund-retry.rate-ms` here. It was wrongly in payment-service.

### payment-service
- Razorpay webhook (see section 3).
- `POST /payments/failed` sends `payment-failed`.
- If money arrives for a FAILED payment, it is still recorded and the booking side refunds it.
- The refund reason is passed through to Razorpay notes.
- Admin payment list and manual refund.
- The success logic is shared by `/verify` and the webhook (`recordSuccessfulPayment`).

---

## 7. Tests

New unit tests (JUnit 5 + Mockito, no database or Kafka needed):

| Service | Test class | Tests |
|---|---|---|
| auth | `AuthServiceTest` | 8 |
| station | `StationControllerTest`, `FareSlabControllerTest` | 7 |
| booking | `BookingServiceTest` | 8 |
| payment | `PaymentServiceTest` | 18 |
| gateway | `JwtGatewayFilterTest` | 9 |

Run one service's tests:

```bash
cd payment-service
mvn test -Dtest=PaymentServiceTest
```

The existing `*ApplicationTests` start the full app, so they need MySQL, Kafka and Eureka running.

---

## 8. Not done (on purpose, to keep it simple)

- **Logout / refresh tokens.** JWTs are stateless and last 24 hours. Logout means the frontend deletes the token. A role change or password reset takes effect on the next login, not on old tokens.
- **Staff role for gates.** Gate devices log in as ADMIN. A separate `STAFF` role can be added later if needed.
- **Entry + exit scan.** A ticket is scanned once (CONFIRMED → USED).
- **Partial refunds / cancellation fees.** Refunds are always the full amount.
