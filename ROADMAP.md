# CrossPesa Roadmap

> This roadmap outlines the evolution of CrossPesa from a functional MVP into a production-inspired cross-border financial platform.

---

# Vision

CrossPesa aims to become a modern cross-border payment platform that enables individuals, merchants, businesses, and institutions to move money securely across multiple countries and currencies.

The long-term goal is to simulate the architecture and business workflows used by platforms such as M-Pesa, Chipper Cash, Flutterwave, and Wise while serving as a learning project for modern backend engineering.

---

# Development Philosophy

CrossPesa is being developed incrementally.

Each version introduces new engineering concepts while keeping the application deployable and functional.

Progression:

```
Simple CRUD
        ↓
Business Rules
        ↓
Financial Transactions
        ↓
Distributed Architecture
        ↓
Production-Grade FinTech
```

---

# Version 1.0 — Minimum Viable Product (MVP)

## Authentication

* User Registration ✅ (with password strength policy + default-currency wallet provisioning)
* User Login ✅ (Redis-backed per-IP rate limiting)
* JWT Authentication ✅ (access + refresh tokens, token-type claims)
* BCrypt Password Hashing ✅
* Role-based Authorization ✅
* Google OAuth2 login ✅ (configurable redirect, fragment-based token delivery)

## Wallets

* Multi-currency wallets ✅
* Wallet balances ✅
* Wallet status ✅

## Transfers

* Wallet funding ✅ (verified Flutterwave checkout — no longer mock)
* Cross-border transfers ✅
* Currency conversion ✅
* Transaction history ✅

## Exchange Rates

* External FX API integration ✅ (Open Exchange Rates, with HTTP timeouts)
* Cached exchange rates ✅ (Postgres-backed)

## Notifications

* Database notifications ✅
* In-app notification support ✅

## Database

* PostgreSQL ✅
* Flyway migrations ✅ (V1–V5)

Status:

**Complete**

---

# Version 1.1 — Financial Foundation ✅

Proper financial accounting.

## Double Entry Ledger ✅

* Ledger entries with running `balance_after` per leg
* Debit/Credit accounting enforced at DB level (`chk_debit_credit_exclusive`)
* Immutable transaction records (legs are non-updatable)
* Monotonic `entry_seq` ordering for correct balance derivation (Flyway V4)
* System wallet grid: SYSTEM_MARKUP / SYSTEM_ROUTING / SYSTEM_LIQUIDITY per currency

## Wallet Improvements ✅

* Wallet balance derived from ledger (wallet row is only a projection)
* Balance validation under pessimistic locks
* Unified available-balance semantics (ledger balance − locked funds, clamped)

## Transaction Improvements ✅

* Gateway/payout references with partial unique indexes
* FX audit trail (USD normalization rate, applied rate, routing pair, markup tiers)

Status:

**Complete**

---

# Version 1.2 — Reliable Money Transfers ✅

Transaction safety.

## Transfer State Machine ✅

```
PENDING / PROCESSING / FLAGGED
↓
COMPLETED or FAILED (+ automatic ledger reversal & refund on failure/timeout)
```

* Settlement worker confirms payouts via provider status; never fabricates completion
* Failed/unconfirmed payouts are fully reversed (user refund, fee clawback, float restoration) idempotently

## Idempotency ✅

* DB-unique-index-enforced idempotency keys; concurrent duplicates get clean HTTP 409s
* Safe retries across send-money and P2P flows

## Webhook Trust Model ✅

* HMAC-SHA256 signature validation (payout callbacks), shared-secret validation (Flutterwave, Smile ID)
* Fail-safe defaults: unconfigured secrets reject all traffic

## Exchange Rate Locking

* Quote expiration / FX validity window — **partially in place** (fx_rates expiry window exists); quote-lock-per-transfer still planned

Status:

**Complete** (FX quote locking partially remaining)

---

# Version 1.3 — Merchant Platform

Support business payments.

## Merchant Accounts

* Merchant onboarding
* Merchant dashboards
* Virtual till numbers

## Merchant Payments

* QR payments
* Payment links
* Merchant settlements

Status:

**Planned**

---

# Version 1.4 — Government & Institutional Payments

Enable payments to public institutions.

Examples:

* Taxes
* School fees
* County services
* Utility bills

Features:

* Government billers
* Payment references
* Receipt generation

Status:

**Planned**

---

# Version 1.5 — Financial Insights

Provide visibility into financial activity.

Features:

* Spending analytics
* Wallet summaries
* Monthly reports
* Currency usage
* Transfer statistics

Status:

**Planned** (admin dashboard metrics already provide aggregate revenue/status views)

---

# Version 2.0 — Event-Driven Architecture

Transition toward distributed system design.

## Apache Kafka

Publish domain events:

* UserRegistered
* WalletFunded
* TransferCreated
* TransferCompleted
* NotificationRequested

Consumers:

* Notifications *(currently served by Spring application events + scheduled poller)*
* Analytics
* Audit
* Fraud Detection

Status:

**Future**

---

# Version 2.1 — Transactional Outbox

Guarantee reliable event publishing.

Features:

* Outbox table
* Background publisher
* Exactly-once event delivery (application level)

Status:

**Future**

---

# Version 2.2 — Saga-Based Transfers

Coordinate distributed financial operations.

Examples:

* FX conversion
* Wallet debit
* External partner payment
* Notifications

Compensation:

* Automatic rollback ✅ (single-transaction atomicity today; payout reversal logic already implements business-level compensation)
* Refund handling ✅ (mirrored reversal legs in the settlement engine)
* Failure recovery ✅ (timeout-driven reversal in the settlement worker)

Remaining: distribute these steps across services, where the saga pattern becomes necessary.

Status:

**Partially realized** (compensation semantics exist; distributed orchestration future)

---

# Version 2.3 — Fraud & Compliance

Financial risk controls.

## Compliance ✅ (core)

* KYC verification ✅ (Smile ID pipeline: submission, webhook auto-decision, admin review)
* Transaction limits ✅ (tiered KYC levels enforced against daily aggregates, KES-normalized)
* Country restrictions ✅ (DB CHECK constraint on country of residence)
* AML screening — **planned**

## Fraud Detection ✅ (core)

* Velocity checks ✅ (>5 transactions/hour flags)
* Suspicious transaction monitoring ✅ (high-value flagging → FLAGGED status)
* Device fingerprinting — **planned**
* Login anomaly detection — **planned** (rate limiting exists as first layer)

Status:

**Core complete** (AML screening, device fingerprinting, login anomaly detection remaining)

---

# Version 2.4 — Partner Integrations

Connect to external payment providers.

Current integrations:

* Flutterwave ✅ (funding + webhooks)
* M-Pesa / Africa's Talking ✅ (SMS notifications; payouts pending)
* Open Exchange Rates ✅ (FX)
* Smile ID ✅ (KYC)

Planned integrations:

* Airtel Money
* Bank APIs (real payout transfer-status verification is the immediate next step)
* Card processors

Features:

* Partner callbacks ✅ (signed webhook infrastructure)
* Reconciliation — **planned**
* Settlement reports — **planned**

Status:

**In Progress**

---

# Version 3.0 — Production Architecture

Transform CrossPesa into a production-inspired financial platform.

Completed:

* Modular monolith structure (package-by-feature)
* Redis caching / rate limiting
* Background workers (settlement worker, notification poller)
* Docker Compose for local infra
* Monitoring & metrics (Micrometer, structured JSON logging, trace-id propagation)
* Health checks (Actuator, public health / admin-only operations)
* Rate limiting
* CI/CD pipeline (Maven quality gate + Render deploy hooks)
* Automated testing (121 unit & integration tests)

Planned improvements:

* API Gateway
* Centralized log aggregation
* API versioning strategy
* Kubernetes deployment
* High availability architecture

Status:

**Mostly complete** (gateway, centralized logging, k8s remaining)

---

# Long-Term Goals

* Support multiple African countries
* Multi-currency remittances
* Merchant ecosystem
* Government payment integrations
* Business-to-business transfers
* Mobile application
* Cloud deployment
* Kubernetes deployment
* Production monitoring
* High availability architecture

---

# Engineering Concepts Covered

Demonstrated so far:

* Java 21 & Spring Boot 4
* Spring Security (JWT + OAuth2, token-type claims, refresh flow)
* PostgreSQL with integrity-first modeling (CHECK constraints, partial unique indexes, triggers)
* Flyway versioned migrations with backfills
* JPA/Hibernate (pessimistic locking, JSONB, generated values)
* REST API design
* Double-entry accounting with ledger-derived balances
* Concurrency control: deterministic lock ordering, idempotency keys, TOCTOU elimination
* Webhook security (HMAC signatures, constant-time comparison, fail-safe defaults)
* External API integration (Flutterwave, OER, Smile ID, Africa's Talking, Cloudinary, SendGrid)
* Scheduled workers and async processing (proxy-safe @Async)
* Observability (MDC tracing, Micrometer, structured logs)
* Docker, Git/GitHub Flow, CI/CD
* Secure software engineering

Still ahead:

* Event-Driven Architecture (Kafka)
* Transactional Outbox Pattern
* Saga orchestration across services
* Scalable distributed backend architecture

---

# Guiding Principle

CrossPesa is intentionally built in stages.

Rather than attempting to build a complex financial platform all at once, each release introduces one or two new engineering concepts while preserving a stable, working application.

This iterative approach mirrors how real-world financial systems evolve over time, balancing feature delivery with architectural improvements.
