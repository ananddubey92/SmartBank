# 🏦 SmartBank AI — Online Banking System

A full-featured, enterprise-grade online banking system built with **Spring Boot 3**, **Spring Security**, **Thymeleaf**, **H2/JPA**, and a **Python-based ML Fraud Detection** microservice.

---

## 🚀 Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x |
| Security | Spring Security 6 (Form Login + JWT), BCrypt, RBAC |
| ORM | Spring Data JPA / Hibernate 6 |
| Database | H2 (dev), configurable via environment for production |
| Frontend | Thymeleaf, HTML5, CSS3, JavaScript |
| Fraud | Python microservice (scikit-learn ML + rule engine) |
| Build | Maven Wrapper (`mvnw`) |
| Testing | JUnit 5, Spring Boot Test, MockMvc |

---

## 👤 Roles & Access Control

The system enforces strict **server-side RBAC** with three roles:

| Role | Capabilities |
|---|---|
| `ROLE_SUPER_ADMIN` | Full system control: open/manage accounts, create admins, freeze/block accounts, view all data, issue debit cards, manage KYC, audit logs |
| `ROLE_ADMIN` | Operational oversight: view customer data, manage transactions (block/review/mark-safe), fraud dashboard |
| `ROLE_CUSTOMER` | Banking operations: deposit, withdraw, transfer (with OTP), view own account only, manage own debit card and beneficiaries |

> **IDOR Protection**: Every customer-facing endpoint enforces ownership verification. Customers cannot access another customer's account, transactions, beneficiaries, or debit card.

---

## Prerequisites

- **Java 21+** (tested with Java 21 LTS)
- **Maven 3.9+** (bundled via `mvnw`)
- **Python 3.10+** (optional for the fraud detection microservice)

---

## 🔧 Environment Variables

### Development (default)
The application runs with H2 in-memory/file database with demo defaults. No configuration needed for local development.

### Production Environment Variables

| Variable | Description | Required |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Set to `prod` | Yes |
| `DB_URL` | JDBC URL for production database | Yes |
| `DB_DRIVER` | JDBC driver class name | Yes |
| `DB_USERNAME` | Database username | Yes |
| `DB_PASSWORD` | Database password | Yes |
| `JWT_SECRET` | 256-bit+ JWT signing secret key | Yes |
| `JWT_EXPIRATION_MS` | JWT token TTL in ms (default: 86400000) | Optional |
| `CVV_SECRET_KEY` | AES key for encrypted CVV storage | Yes |
| `ML_SERVICE_URL` | Python fraud detection URL (default: http://localhost:5001) | Optional |
| `DEMO_CODES_ENABLED` | Enable fallback OTP/PIN codes (set false in prod) | Optional |

---

## 🏃 Running the Application

### Start (Development)
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux/macOS
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`

### Start the Python Fraud Detection Service (Optional)
```bash
cd fraud-detection
pip install -r requirements.txt
python app.py
```

---

## 🗝️ Default User Credentials (Development Only)

| Username | Password | Role |
|---|---|---|
| `superadmin` | `SuperAdmin@123` | ROLE_SUPER_ADMIN |
| `admin` | `Admin@123` | ROLE_ADMIN |
| `rahul` | `Rahul@123` | ROLE_CUSTOMER |
| `priya` | `Priya@123` | ROLE_CUSTOMER |

> **Warning**: Change all default passwords before any non-local deployment.

---

## 🌐 Navigation

| Path | Role Required | Description |
|---|---|---|
| `/` | Public | Landing page |
| `/login` | Public | Login page |
| `/register` | Public | Customer self-registration |
| `/dashboard` | Customer | Customer banking dashboard |
| `/admin` | Admin | Admin operations panel |
| `/super-admin` | Super Admin | Full banking management console |
| `/h2-console` | Dev only | H2 database console (disabled in prod) |

---

## 📡 Key API Endpoints

### Authentication — `/api/auth`
- `POST /api/auth/login` — JWT login
- `POST /api/auth/register` — Customer registration
- `POST /api/auth/change-password` — Change password

### Transactions — `/api/transactions`
- `POST /api/transactions/deposit` — Deposit with OTP (Customer, own account)
- `POST /api/transactions/withdraw` — Withdraw with OTP + PIN (Customer, own account)
- `POST /api/transactions/transfer` — Transfer with OTP + PIN (Customer, own account)
- `GET /api/transactions/history/{accountNumber}` — Transaction history (Customer, own)

> All transaction endpoints accept an `Idempotency-Key` header.

**Transaction Limits**: Per-transaction max ₹1,00,000 | Daily transfer limit ₹5,00,000

### Debit Cards — `/api/debit-cards`
- `GET /api/debit-cards/my-card` — Get masked card (Customer)
- `POST /api/debit-cards/{cardId}/toggle-status` — Block/unblock own card
- `POST /api/debit-cards/{cardId}/reveal` — Reveal full card details (requires PIN/password)
- `POST /api/debit-cards/{cardId}/replace` — Request replacement card

### Super Admin APIs — `/api/super-admin`
- `GET /api/super-admin/dashboard` — System metrics
- `POST /api/super-admin/admins` — Create admin
- `POST /api/super-admin/accounts` — Create account for customer
- `POST /api/super-admin/accounts/open` — Full account opening workflow
- `PATCH /api/super-admin/accounts/{num}/freeze` — Freeze account
- `PATCH /api/super-admin/accounts/{num}/unfreeze` — Unfreeze account
- `DELETE /api/super-admin/accounts/{num}` — Close or delete account
- `GET /api/super-admin/audit-logs` — Full audit trail
- `POST /api/super-admin/debit-cards/backfill` — Issue cards to accounts missing one

---

## 🧪 Running Tests

```bash
# Windows
.\mvnw.cmd test

# Linux/macOS
./mvnw test

# Specific test class
.\mvnw.cmd test -Dtest=SuperAdminSecurityIntegrationTest
```

### Test Coverage Summary (107+ Tests — BUILD SUCCESS)

| Test Class | Count | Area |
|---|---|---|
| `SuperAdminSecurityIntegrationTest` | 29 | RBAC, account lifecycle, admin provisioning, audit logging |
| `AdminSecurityIntegrationTest` | 10 | Admin portal, transaction management, registration |
| `AccountCreationSecurityIntegrationTest` | 5 | Account creation RBAC |
| `DebitCardIntegrationTest` | 5 | Debit card lifecycle and IDOR |
| `FraudControllerIntegrationTest` | 5 | Fraud endpoint authorization |
| `NotificationControllerIntegrationTest` | 5 | Notification IDOR protection |
| `DebitCardServiceTest` | 7 | Debit card service unit tests |
| `OtpServiceTest` | 4 | OTP generation, validation, rate limiting |
| `StatementServiceTest` | 6 | Statement filtering, PDF/Excel generation |
| `UpiServiceTest` | 8 | UPI payment operations |
| `FraudDetectionServiceTest` | 5 | ML fraud scoring |
| `AiBankingAssistantServiceTest` | 4 | AI assistant |
| `ExpenseAnalyticsServiceTest` | 2 | Spending analytics |
| `FinancialHealthServiceTest` | 2 | Financial health scoring |
| `OnlinebankingsystemApplicationTests` | 1 | Context load |

---

## 🔐 Security Architecture

| Control | Implementation |
|---|---|
| Password Storage | BCrypt (cost factor 10) |
| Transaction PIN | BCrypt-hashed, never logged |
| CVV Storage | AES-GCM encrypted with env key |
| PAN/Aadhaar | Masked in all API responses |
| Card Number | Masked (`**** **** **** XXXX`) |
| OTP | Cryptographically random, 5-min expiry, 3-attempt limit |
| Idempotency | Per-request keys prevent duplicate transactions |
| Daily Limits | ₹5,00,000 transfer limit per account per day |
| Session Cookie | `HttpOnly`, `Secure`, `SameSite=Strict` in production |
| H2 Console | Disabled in production |
| IDOR | All customer endpoints verify ownership via `principal.getName()` |

### Double-Entry Ledger
Every financial transaction creates balanced `DEBIT` + `CREDIT` ledger entries ensuring the general ledger always sums to zero.

### Concurrency Safety
Transfers use `@Lock(LockModeType.PESSIMISTIC_WRITE)` with lexicographic deadlock ordering to prevent race conditions.

---

## 📂 Project Structure

```
src/
├── main/
│   ├── java/com/project/onlinebankingsystem/
│   │   ├── config/          # Security, JWT, database migration config
│   │   ├── controller/      # REST + MVC controllers
│   │   ├── dto/             # Request/Response DTOs (never raw entities)
│   │   ├── enums/           # Role, AccountStatus, CardStatus, etc.
│   │   ├── exception/       # Global exception handler
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Spring Data JPA repositories
│   │   └── service/         # Business logic services
│   └── resources/
│       ├── application.properties        # Common config (env-overridable)
│       ├── application-dev.properties    # Dev config (H2 console enabled)
│       ├── application-prod.properties   # Prod config (H2 disabled)
│       └── templates/                   # Thymeleaf templates
└── test/
    └── java/com/project/onlinebankingsystem/
        ├── controller/      # Integration tests
        └── service/         # Unit tests
```

---

## 📋 Audit Logging

All privileged actions are recorded with:
- `performedBy` — actor username
- `action` — event type (e.g., `ACCOUNT_FROZEN`, `ADMIN_STATUS_CHANGED`)
- `targetEntityId` — what was affected
- `previousValue` → `newValue` — state change
- `ipAddress` — client IP
- `timestamp`

Access via `GET /api/super-admin/audit-logs` or the Super Admin UI.

---

## 🚢 Production Deployment Checklist

- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Set all required env variables (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CVV_SECRET_KEY)
- [ ] Set `DEMO_CODES_ENABLED=false`
- [ ] Change all default user passwords
- [ ] Configure production RDBMS (PostgreSQL/MySQL) and update `DB_DRIVER`
- [ ] Configure TLS/SSL at the reverse proxy (Nginx, AWS ALB, etc.)
- [ ] Set up the Python fraud detection service
- [ ] Enable structured logging (Logback → ELK/CloudWatch)
