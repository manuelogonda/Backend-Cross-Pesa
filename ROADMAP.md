# CrossPesa Roadmap

> This roadmap outlines the planned evolution of CrossPesa from a functional MVP into a production-inspired cross-border financial platform.

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

* User Registration
* User Login
* JWT Authentication
* BCrypt Password Hashing
* Role-based Authorization

## Wallets

* Multi-currency wallets
* Wallet balances
* Wallet status

## Transfers

* Wallet funding (mock)
* Cross-border transfers
* Currency conversion
* Transaction history

## Exchange Rates

* External FX API integration
* Cached exchange rates

## Notifications

* Database notifications
* In-app notification support

## Database

* PostgreSQL
* Flyway migrations

Status:

**Current Development**

---

# Version 1.1 — Financial Foundation

Introduce proper financial accounting.

## Double Entry Ledger

* Ledger entries
* Debit/Credit accounting
* Immutable transaction records

## Wallet Improvements

* Wallet balance derived from ledger
* Balance validation
* Account reconciliation

## Transaction Improvements

* Transaction references
* Improved audit trail

Status:

**Planned**

---

# Version 1.2 — Reliable Money Transfers

Improve transaction safety.

## Transfer State Machine

```
PENDING
↓
PROCESSING
↓
COMPLETED

or

FAILED
```

Future expansion:

```
PENDING
↓
SCREENING
↓
FX_LOCKED
↓
DEBITED
↓
PARTNER_PENDING
↓
COMPLETED
```

## Idempotency

* Prevent duplicate transfers
* Safe retries

## Exchange Rate Locking

* Quote expiration
* FX validity window

Status:

**Planned**

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

**Planned**

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

* Notifications
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

* Automatic rollback
* Refund handling
* Failure recovery

Status:

**Future**

---

# Version 2.3 — Fraud & Compliance

Introduce financial risk controls.

## Compliance

* KYC verification
* AML screening
* Transaction limits
* Country restrictions

## Fraud Detection

* Velocity checks
* Suspicious transaction monitoring
* Device fingerprinting
* Login anomaly detection

Status:

**Future**

---

# Version 2.4 — Partner Integrations

Connect to external payment providers.

Potential integrations:

* M-Pesa
* Airtel Money
* Bank APIs
* Card processors
* FX providers

Features:

* Partner callbacks
* Reconciliation
* Settlement reports

Status:

**Future**

---

# Version 3.0 — Production Architecture

Transform CrossPesa into a production-inspired financial platform.

Planned improvements:

* Modular Monolith refinement
* API Gateway
* Redis caching
* Background workers
* Docker Compose
* Monitoring & Metrics
* Centralized logging
* Health checks
* Rate limiting
* API versioning
* CI/CD pipeline
* Automated testing

Status:

**Vision**

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

Throughout this roadmap, CrossPesa is expected to demonstrate knowledge of:

* Java & Spring Boot
* Spring Security
* JWT Authentication
* PostgreSQL
* Flyway
* JPA/Hibernate
* REST APIs
* External API integration
* Financial transaction processing
* Double-entry accounting
* Event-Driven Architecture
* Apache Kafka
* Transactional Outbox Pattern
* Saga Pattern
* Redis
* Docker
* Git & GitHub Flow
* CI/CD
* Secure software engineering
* Scalable backend architecture

---

# Guiding Principle

CrossPesa is intentionally built in stages.

Rather than attempting to build a complex financial platform all at once, each release introduces one or two new engineering concepts while preserving a stable, working application.

This iterative approach mirrors how real-world financial systems evolve over time, balancing feature delivery with architectural improvements.
