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

### Payment / Webhooks
- Never trust redirect success.
- Only credit after verified webhook or explicit verify call + idempotency check.

## Coding Standards
- Fully implemented production code only (no // TODO or placeholders).
- Use BigDecimal for all money.
- Prefer records for DTOs.
- Every mutating admin action requires a reason/notes field.