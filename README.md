
# CrossPesa MVP

> A cross-border remittance platform built with **Spring Boot, React, PostgreSQL, JWT Authentication, and Flyway**.

## Overview

CrossPesa is a fintech application that enables users to send money across borders through a secure multi-currency wallet system. This MVP focuses on the core remittance workflow, including authentication, wallet management, currency conversion, and transfer processing.

The project is inspired by platforms such as M-Pesa, Chipper Cash, Flutterwave, and Wise, while serving as the foundation for a larger financial platform that will evolve through future releases.

---

## Features

### Authentication

* User registration
* User login
* JWT-based authentication
* Password hashing with BCrypt
* Protected API endpoints

### User Management

* User profiles
* KYC status and level tracking
* User roles (User, Merchant, Admin)

### Multi-Currency Wallets

* Create wallets
* View wallet balances
* Wallet status management
* One wallet per currency

### Wallet Funding (Mock)

* Mock top-up endpoint
* Balance updates
* Transaction validation

### Exchange Rates

* Fetch live exchange rates from an external provider
* Store latest exchange rates
* Currency conversion calculations

### Cross-Border Transfers

* Balance validation
* Currency conversion
* Transfer creation
* Transaction status tracking
* Idempotent transfer requests

### Notifications

* In-app notification records
* SMS/Email notification support (database model)

---

## Technology Stack

### Backend

* Java 21
* Spring Boot
* Spring Security
* Spring Data JPA
* JWT Authentication
* Flyway
* Maven

### Database

* PostgreSQL

### Frontend

* React
* TypeScript
* Vite

---

## Database

Current schema includes:

* Users
* Wallets
* Transactions
* Exchange Rates
* Notifications

Database migrations are managed using Flyway.

---

## MVP Workflow

```text
Register
      |
Login
      |
Receive JWT
      |
Create Wallet
      |
Fund Wallet (Mock)
      |
Fetch Exchange Rate
      |
Initiate Transfer
      |
Save Transaction
      |
Generate Notification
```

---

## Current Project Status

Completed:

* User authentication
* JWT authorization
* PostgreSQL integration
* Flyway migrations
* Database schema
* Login and registration UI

In Progress:

* Wallet management
* Mock wallet funding
* Exchange rate integration
* Cross-border transfers
* Transaction history
* Notifications

---

## Future Roadmap

Future releases will progressively introduce production-grade fintech architecture, including:

* Double-entry ledger
* Transaction state machine
* Kafka event streaming
* Transactional Outbox Pattern
* Saga orchestration
* Fraud detection
* AML/KYC workflows
* Redis caching
* Merchant payments
* Government bill payments
* Partner reconciliation
* Analytics and reporting

---

## Learning Goals

This project is designed to demonstrate practical software engineering concepts, including:

* REST API design
* Secure authentication
* Database modeling
* Financial transaction workflows
* Currency conversion
* Clean Architecture
* Git branching strategy
* Incremental software evolution

