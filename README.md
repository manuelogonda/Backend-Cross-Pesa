# CrossPesa

> A cross-border remittance platform built with **Spring Boot 4, React, PostgreSQL, JWT Authentication, and Flyway**.

## Overview

CrossPesa is a fintech application that enables users to send money across borders through a secure multi-currency wallet system. It implements a double-entry ledger, a system-wallet settlement engine, live FX quoting, KYC verification, and gateway-driven wallet funding — the core money-movement backbone of a remittance platform.

The project is inspired by platforms such as M-Pesa, Chipper Cash, Flutterwave, and Wise, while serving as the foundation for a larger financial platform that will evolve through future releases.

---

## Features

### Authentication

* User registration with password strength policy (length + character classes) and default-currency wallet provisioning
* User login with Redis-backed per-IP login rate limiting
* JWT access tokens + refresh tokens (`POST /api/v1/auth/refresh` revalidates account status/role from the DB)
* Token-type claims prevent refresh tokens being replayed as access credentials
* Google OAuth2 login with configurable post-login redirect; tokens delivered via URL fragment
* BCrypt password hashing, protected API endpoints, proxy-aware client IP resolution

### Multi-Currency Wallets

* Create wallets (one retail wallet per currency)
* View balances — derived from the ledger via a monotonic `entry_seq` ordering (never from timestamps or random UUIDs)
* Wallet status management (ACTIVE / FROZEN / SUSPENDED)
* Unified available-balance semantics (ledger balance − locked funds, clamped at zero)

### Wallet Funding (Flutterwave)

* Payment link generation via Flutterwave Standard checkout
* Signature-validated webhooks and server-side transaction verification
* Wallet credits use **only** gateway-reported amount/currency/payer identity — never client-supplied values
* Idempotent credits keyed by gateway reference

### Double-Entry Ledger & Settlement Engine

* Full double-entry bookkeeping: every movement posts balanced debit/credit legs with running `balance_after`
* System wallet grid: `SYSTEM_MARKUP`, `SYSTEM_ROUTING`, `SYSTEM_LIQUIDITY` per currency
* Cross-border settlement posts all FX clearing legs atomically with deterministic UUID-order locking
* Payout failure reversal mirrors every settlement leg (user refund, fee clawback, float restoration), idempotent via a REFUND guard
* Treasury rebalancing between liquidity pools (admin)

### Transfers

* Cross-border remittance with fee breakdown (markup + routing), USD normalization audit trail
* P2P internal transfers with deadlock-free deterministic lock ordering
* Idempotency keys enforced by DB unique index — concurrent duplicates get clean HTTP 409s, not constraint violations
* Fraud engine: account status/KYC gating, tiered daily aggregate limits, velocity checks, FLAGGED status for suspicious transactions

### Exchange Rates

* Live rates from Open Exchange Rates with DB caching
* Tight HTTP timeouts (3s connect / 5s read)
* FX quoted before pessimistic locks are taken so external calls never run inside lock windows

### KYC (Smile ID)

* Document + selfie submission pipeline with Cloudinary storage
* Signature-validated Smile ID webhook (shared-secret token, constant-time compare)
* Auto-approve/reject on biometric result, manual review queue for edge cases
* Admin review workflow with audit fields

### Notifications

* SMS (Africa's Talking), email (SendGrid stub), in-app
* Delivery state (`dispatched_at`) fully decoupled from read status — marking read never cancels an SMS
* Async dispatch on a separate Spring bean (proxy-safe `@Async`/`@Transactional`), retry cap, scheduled poller with metrics

### Admin

* Dashboard metrics, user/wallet/transaction management
* PII masking: government ID numbers masked (last 3 chars) in list views
* Treasury rebalancing tools
* Actuator health public, admin-only operational endpoints

### Observability

* Structured JSON logging with MDC trace ids
* Inbound trace propagation (`X-Trace-Id` / W3C `traceparent`)
* Micrometer gauges/timers for notification polling

---

## Technology Stack

### Backend

* Java 21
* Spring Boot 4 (Spring Security, Data JPA, Web MVC, Actuator)
* JWT (jjwt) + OAuth2 client
* Flyway migrations
* Redis (Redisson) for rate limiting
* Maven

### Database

* PostgreSQL with CHECK constraints, partial unique indexes, and triggers enforcing financial invariants at the DB level

### Integrations

* Flutterwave (funding), Open Exchange Rates (FX), Smile ID (KYC), Cloudinary (documents), Africa's Talking (SMS)

### Frontend

* React, TypeScript, Vite

---

## Security Model

* All inbound webhooks require signature validation:
  * Flutterwave — `verif-hash` shared secret
  * Gateway payout callbacks — HMAC-SHA256 over the raw body (`X-Webhook-Signature`)
  * Smile ID — `X-Callback-Token` shared secret
  * Unconfigured secrets reject all traffic (secure default)
* Secrets are environment-sourced only — no hardcoded credentials; admin bootstrap credentials come from `ADMIN_EMAIL`/`ADMIN_PASSWORD`
* Money-movement endpoints validate ownership, currency, and gateway-reported values server-side

## Database

Schema includes: Users, Beneficiaries, Wallets, Transactions, Ledger Entries (with monotonic `entry_seq`), FX Rates, Notifications, KYC Submissions.

Migrations:

| Version | Purpose |
|---|---|
| V1 | Baseline schema |
| V2 | Ledger integrity constraints & indexes |
| V3 | Allow FLAGGED transaction status |
| V4 | Monotonic `entry_seq` for ledger balance derivation (with backfill) |
| V5 | Notification delivery state (`dispatched_at`) |

---

## MVP Workflow

```text
Register (+ default wallet)
      |
Login / OAuth2  ->  JWT access + refresh tokens
      |
Fund Wallet (Flutterwave checkout -> verified credit)
      |
Fetch Exchange Rate
      |
Initiate Transfer (idempotency key, fraud checks, fee quote)
      |
Double-Entry Settlement (atomic ledger legs)
      |
Settlement Worker confirms payout (or reverses + refunds on timeout/failure)
      |
Notification dispatched (SMS/in-app)
```

---

## Getting Started

1. Copy `.env.example` to `.env` and fill in real values.
2. Start PostgreSQL and Redis (a `compose.yaml` is provided).
3. Run Flyway migrations and the app:

```bash
./mvnw spring-boot:run
```

4. Tests (121 unit/integration tests against a schema-isolated local Postgres):

```bash
./mvnw test
```

CI runs the Maven quality gate on push/PR to main/staging and triggers Render deploys via hooks.

---

## Current Project Status

Completed:

* Authentication (JWT + refresh flow + OAuth2), rate-limited login
* Double-entry ledger, system wallet grid, cross-border settlement & reversal
* Wallet management and verified Flutterwave funding
* Live FX integration with caching and timeouts
* Cross-border and P2P transfers with idempotency and fraud controls
* KYC submission + webhook pipeline
* Notification delivery pipeline (async, retry-capped, poller-driven)
* Admin dashboard with PII masking and treasury tooling
* Hardened webhooks (HMAC/shared-secret), observability, CI quality gate

In Progress:

* Real payout-provider transfer status integration (settlement currently refunds unconfirmed payouts after a configurable timeout)
* SendGrid email dispatch implementation

---

## Future Roadmap

Future releases will progressively introduce production-grade fintech architecture, including:

* Transactional Outbox Pattern
* Kafka event streaming
* Saga orchestration
* AML screening workflows
* Merchant payments
* Government bill payments
* Partner reconciliation
* Analytics and reporting

---

## Learning Goals

This project is designed to demonstrate practical software engineering concepts, including:

* REST API design
* Secure authentication and webhook trust models
* Financial database modeling with integrity enforced at the DB level
* Double-entry accounting and concurrency control (pessimistic locking, deterministic lock order, idempotency)
* Clean architecture and incremental evolution
