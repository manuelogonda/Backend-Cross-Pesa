# CrossPesa – Architecture & Coding Rules (Junie Context)

## Core Principles
- Double-entry ledger is the source of truth. Wallet.balance is only a cache/projection.
- NO database triggers for balance_after. Everything is calculated in Java services.
- Always use pessimistic locking (`findByIdWithLock`) before changing any balance.
- Every money movement must create balanced debit + credit ledger entries.
- Idempotency keys are mandatory on all fund-moving endpoints.
- Webhooks must validate signature → return 200 OK immediately → process asynchronously.

## Tech Stack
- Java 21, Spring Boot 3.x, Spring Security 6
- PostgreSQL, Redis (Redisson)
- Flutterwave, M-Pesa Daraja, Paystack

## Key Modules & Rules

### Ledger Module
- `balance_after` is set in the service layer only.
- Use `findTopByWalletIdOrderByCreatedAtDescIdDesc` to get current balance.
- Lock wallets in deterministic UUID order to avoid deadlocks.

### Transaction Module
- Fee engine produces QuoteResult (gross, markup, routing, net, payout).
- Ledger legs (principal + markup + routing) are posted in the same transaction.
- Status flow: PENDING → PROCESSING → COMPLETED / FAILED / FLAGGED

### Wallet Module
- USER_RETAIL, SYSTEM_MARKUP, SYSTEM_ROUTING, SYSTEM_LIQUIDITY
- Available balance = balance - lockedBalance
- System wallets have user = null
- ## Wallet Feature

### Wallet Types
- USER_RETAIL – end-user spendable wallet
- SYSTEM_MARKUP – receives platform markup fees
- SYSTEM_ROUTING – receives corridor costs
- SYSTEM_LIQUIDITY – FX clearing pools

### Rules
- One USER_RETAIL wallet per user (current design).
- balance is a cache; ledger is the source of truth.
- availableBalance = balance - lockedBalance (never negative).
- Always lock with pessimistic write lock before mutation.
- addFunds is idempotent on gatewayReference.
- Top-up credit happens only after successful gateway verification (or webhook).
- System wallets have user = null and are never exposed to normal users.

### Payment / Webhooks
- Never trust redirect success.
- Only credit after verified webhook or explicit verify call + idempotency check.


## Fee Engine & System Wallet Settlement

### TransactionFeeEngineService
- Progressive USD-tier markup (Tier 1: 0.60%, Tier 2: 0.40%, Tier 3: 0.20%).
- Corridor-specific routing costs with fallback default.
- All calculations use BigDecimal with scale 4 and HALF_UP.
- Returns immutable QuoteResult used by the rest of the system.

### SystemWalletEngine
- Manages SYSTEM_LIQUIDITY, SYSTEM_MARKUP, SYSTEM_ROUTING wallets for every supported currency.
- executeCrossBorderSettlement posts multi-leg double-entry entries:
    - User debit (principal + markup + routing) – itemized
    - System markup & routing credits
    - FX clearing between source and target liquidity pools
- balance_after is calculated in Java as a running balance per leg.
- Always lock wallets with pessimistic locking in deterministic UUID order.
- Wallet.balance is updated only after successful ledger posting (projection only).
- initializeSystemWallets() runs on startup and creates the full currency grid if missing.


## Transaction Feature

### Core Flow
1. Idempotency check
2. Fraud / KYC hard validation
3. Pessimistic lock on source wallet
4. Live FX rates → QuoteResult from fee engine
5. Available balance check under lock
6. Create Transaction record
7. Immediately post all ledger legs via SystemWalletEngine (or equivalent)
8. Return response

### Status Lifecycle
PENDING → PROCESSING → COMPLETED / FAILED / FLAGGED / CANCELLED

### Settlement Worker
- Only confirms external payout status.
- Never re-posts ledger legs.
- Runs with fixedDelay and small batches.
- Each reconciliation runs in REQUIRES_NEW.

### Fraud Rules
- Hard block: suspended/locked users, unapproved KYC, tier limit exceeded.
- Soft flag: high-value (>100k KES) or velocity (>5 tx/hour).

### Invariants
- Idempotency key is unique and required.
- Ledger is the source of truth.
- Wallet.balance is a projection updated only after successful ledger posting.

## Flutterwave & Webhooks

### Payment Initiation
- Create or use a stable tx_ref that maps to our Transaction / payment reference.
- Call Flutterwave Standard checkout and return the link.
- Redirect success is untrusted.

### Verification
- Server-side verify endpoint must check status, amount and currency.
- Prefer webhook as the primary confirmation path.

### Webhook Safety (Mandatory)
1. Validate signature (verif-hash) first.
2. Return HTTP 200 OK immediately.
3. Process asynchronously / in a separate transactional method.
4. Processing must be fully idempotent (never double-credit).
5. Credit wallet only through WalletService.addFunds (which checks gateway reference).

### Configuration
- flutterwave.secret-key
- flutterwave.base-url
- flutterwave.redirect-url
- flutterwave.webhook-secret

## Admin Module

### Access Control
- All endpoints require ROLE_ADMIN.
- Mutating actions must record who performed them and why (reason / adminNotes).

### Capabilities
- Dashboard metrics (today’s volume, pending, flagged, revenue)
- Transaction search & filtering by status
- User listing
- View any user’s retail wallet and full ledger statement
- Freeze / suspend / activate a user’s retail wallet
- Update KYC status and level
- View system wallets (LIQUIDITY / MARKUP / ROUTING)
- Execute treasury rebalance between liquidity pools (audited via Transaction + ledger)

### Invariants
- Treasury can never touch USER_RETAIL wallets.
- Every status or KYC change is logged with admin identity + reason.
- Rebalance creates an audit Transaction and balanced ledger legs.


## Beneficiary Module

- Users can save payout contacts (bank / mobile money / card).
- Unique per user: (user_id + payout_provider + account_number).
- Ownership is strictly enforced on read/update/delete.
- Used by the send-money flow as the external payout destination.
- Prefer per-user uniqueness for email/phone rather than global uniqueness.

## Coding Standards
- Fully implemented production code only (no // TODO or placeholders).
- Use BigDecimal for all money.
- Prefer records for DTOs.
- Every mutating admin action requires a reason/notes field.