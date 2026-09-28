# Paylane: a 3-tier payment app for learning EKS

Paylane is a small but complete digital wallet: people sign up, add cards and bank accounts, top up
their wallet, send money to each other, request money, pay bills and withdraw. An admin console lets you
freeze users, change limits and reverse transfers. It's built to be deployed the way real services are:
stateless containers, config from environment variables, health probes, metrics, graceful shutdown and a
schema managed by migrations.

```
 Browser ──► [ frontend ]  React SPA served by nginx (port 8080)
                 │  /api/*  proxied to the backend Service
                 ▼
             [ backend ]   Spring Boot 3 / Java 17 REST API (port 8080)
                 │  JDBC (HikariCP), Flyway migrations on startup
                 ▼
             [ MySQL 8 ]   Amazon RDS in production, a StatefulSet or container for learning
```

## What's in the box

```
paylane/
├── backend/              Spring Boot API (Maven)
│   ├── Dockerfile        multi-stage: maven build -> temurin 17 JRE, non-root uid 10001
│   └── src/main/resources/db/migration/   Flyway SQL (schema + seeded billers)
├── frontend/             React 18 + Vite
│   ├── Dockerfile        multi-stage: node build -> nginx-unprivileged, non-root, port 8080
│   └── nginx/default.conf.template        SPA routing, /api proxy, /healthz
├── scripts/smoke-test.sh end-to-end test of every payment flow (curl + jq)
├── docker-compose.yml    run all three tiers locally
└── .env.example          every configuration variable
```

## Features

Users can register and log in (JWT), edit their profile, change password, and set a 4-6 digit transaction
PIN. Every money movement out of a wallet needs the PIN; five wrong attempts lock it for 15 minutes.

Money flows: top up from a card, withdraw to a bank account, send to another user by email or phone, request
money and pay or decline requests, and pay bills to eight seeded billers. There's full transaction history
with filters, receipts, a monthly in/out summary, and in-app notifications.

The payment rules are ones a real wallet would enforce. Balances are updated under row-level locks
(`SELECT ... FOR UPDATE`, taken in id order so two opposite transfers can't deadlock), every movement writes
double-entry ledger rows with the balance after, each user has a daily spending limit plus a per-transaction
maximum, and money-moving endpoints accept an `Idempotency-Key` header so a retried request never charges
twice. Card numbers are Luhn-checked and only brand plus last four digits are stored; the CVV is never saved.

Admins get platform stats, user search, freeze or unfreeze, per-user daily limits, a global transaction list,
and transfer reversal.

### The mock payment gateway

No real money moves. The gateway approves everything except:

| Card or bank account ending | Result |
|---|---|
| `0002` | declined |
| `9995` | insufficient funds |

Handy test cards: `4242 4242 4242 4242` (approved), `4000 0000 0000 0002` (declined). Any future expiry and
any 3-digit CVV work. Declined top-ups are still recorded, as FAILED transactions.

### Admin login

An admin account is created on first start from `ADMIN_EMAIL` / `ADMIN_PASSWORD`
(defaults `admin@paylane.local` / `Admin@12345`). Change these in any shared environment. It's safe with
multiple replicas: whichever pod starts first creates it, the others see it exists.

## Run it locally first

With Docker installed:

```bash
docker compose up --build
```

Open http://localhost:3000, register two users in two browsers (or one normal plus one private window), set
a PIN in Settings, add the test card, top up, and send money between them. Swagger UI is at
http://localhost:8080/swagger-ui.html.

Then run the end-to-end test, which registers fresh users and exercises every flow including an admin
reversal:

```bash
./scripts/smoke-test.sh http://localhost:3000
```

Without Docker, for development: start MySQL, run the backend with `cd backend && mvn spring-boot:run`, and
the frontend with `cd frontend && npm install && npm run dev` (http://localhost:5173, it proxies `/api` to
port 8080).

## Configuration

Everything is configured with environment variables, so the same image runs in every environment.

**Backend**

| Variable | Default | Notes |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `paylane` | used to build the JDBC URL |
| `DB_URL` | built from the above | set this instead to give a full JDBC URL |
| `DB_USE_SSL` | `false` | set `true` for RDS |
| `DB_USERNAME`, `DB_PASSWORD` | `paylane`, `paylane` | **Secret** |
| `DB_POOL_SIZE` | `10` | connections per pod; replicas × this must stay under MySQL `max_connections` |
| `JWT_SECRET` | dev value | **Secret**, 32+ characters, identical on every replica |
| `JWT_EXPIRATION_MINUTES` | `120` | |
| `CORS_ALLOWED_ORIGINS` | localhost dev ports | comma-separated; only matters if the browser calls the API on a different host |
| `WALLET_CURRENCY` | `USD` | |
| `WALLET_DAILY_LIMIT` | `5000.00` | default for new users |
| `WALLET_MAX_TXN_AMOUNT` | `10000.00` | |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME` | see above | password is a **Secret** |
| `SERVER_PORT` | `8080` | |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` | heap follows the container memory limit |

**Frontend**

| Variable | Default | Notes |
|---|---|---|
| `BACKEND_URL` | `http://backend:8080` | runtime, where nginx proxies `/api/`; point it at your backend Service DNS, e.g. `http://paylane-backend.paylane.svc.cluster.local:8080` |
| `VITE_API_BASE_URL` | empty | build arg, only if the SPA must call the API on another origin |

## API

All endpoints are under `/api` and take/return JSON. Everything except `/api/auth/**` needs
`Authorization: Bearer <token>`. Errors look like
`{"timestamp":"...","status":422,"code":"INSUFFICIENT_FUNDS","message":"...","path":"/api/transfers","fieldErrors":{...}}`.
Full interactive docs: `/swagger-ui.html`.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register` `{fullName,email,phone,password}` · `POST /auth/login` `{email,password}` |
| Profile | `GET /users/me` · `PUT /users/me` `{fullName,phone}` · `POST /users/me/password` `{currentPassword,newPassword}` · `POST /users/me/pin` `{password,pin}` · `GET /users/lookup?query=` |
| Wallet | `GET /wallet` · `POST /wallet/topup` `{paymentMethodId,amount}` · `POST /wallet/withdraw` `{paymentMethodId,amount,pin}` |
| Transfers | `POST /transfers` `{recipient,amount,note,pin}` (recipient is an email or phone) |
| History | `GET /transactions?type=&status=&from=&to=&reference=&page=&size=` · `GET /transactions/summary` · `GET /transactions/{reference}` |
| Payment methods | `GET /payment-methods` · `POST /payment-methods/cards` `{cardNumber,holderName,expiryMonth,expiryYear,cvv}` · `POST /payment-methods/banks` `{bankName,holderName,accountNumber,routingCode}` · `PATCH /payment-methods/{id}/default` · `DELETE /payment-methods/{id}` |
| Requests | `POST /requests` `{payer,amount,note}` · `GET /requests/incoming` · `GET /requests/outgoing` (both `?status=`) · `GET /requests/pending-count` · `POST /requests/{id}/pay` `{pin}` · `POST /requests/{id}/decline` · `POST /requests/{id}/cancel` |
| Bills | `GET /billers` · `POST /bills/pay` `{billerId,accountReference,amount,pin}` |
| Notifications | `GET /notifications` · `GET /notifications/unread-count` · `POST /notifications/{id}/read` · `POST /notifications/read-all` |
| Admin (role ADMIN) | `GET /admin/stats` · `GET /admin/users?search=` · `PATCH /admin/users/{id}/status` `{status}` · `PATCH /admin/users/{id}/limit` `{dailyLimit}` · `GET /admin/transactions` · `POST /admin/transactions/{reference}/reverse` `{reason}` |

Send an `Idempotency-Key` header (any unique string, e.g. a UUID) on top-up, withdraw, transfer and bill
payment; repeating the same key returns the original result instead of moving money again.

Every response carries an `X-Request-Id` header (generated, or echoed from the request) that also appears in
the backend log lines, so you can follow one request through the ingress, nginx and Spring logs.

## Deploying to EKS: what the app expects from your manifests

You're writing the manifests yourself, so here is everything the containers need.

**Images.** Build and push both to ECR:

```bash
aws ecr create-repository --repository-name paylane-backend
aws ecr create-repository --repository-name paylane-frontend
docker build -t <acct>.dkr.ecr.<region>.amazonaws.com/paylane-backend:1.0.0 backend
docker build -t <acct>.dkr.ecr.<region>.amazonaws.com/paylane-frontend:1.0.0 frontend
docker push ...   # after aws ecr get-login-password | docker login
```

Build with `--platform linux/amd64` (or arm64 for Graviton nodes) if you're on an Apple Silicon laptop.

**Database.** In production use Amazon RDS for MySQL 8 (Multi-AZ), in private subnets with a security group
that allows 3306 only from the node or pod security group, and set `DB_USE_SSL=true`. Create an empty
database named `paylane` and a user; Flyway creates all tables on first backend start. For a learning
cluster, a MySQL StatefulSet with a gp3 PersistentVolumeClaim also works. Keep `DB_PASSWORD`, `JWT_SECRET`
and `ADMIN_PASSWORD` in a Kubernetes Secret, or better, AWS Secrets Manager synced with External Secrets
Operator or the Secrets Store CSI driver. Put the rest in a ConfigMap.

**Backend Deployment.** Container port `8080`, runs as uid `10001` (so `runAsNonRoot: true` works;
`readOnlyRootFilesystem: true` needs an `emptyDir` on `/tmp`). Probes:

| Probe | Path | Suggested |
|---|---|---|
| startup | `/actuator/health/liveness` | `periodSeconds: 5`, `failureThreshold: 30` (JVM + Flyway can take ~30-60s) |
| liveness | `/actuator/health/liveness` | `periodSeconds: 10` |
| readiness | `/actuator/health/readiness` | `periodSeconds: 5`; goes DOWN during shutdown so traffic drains |

Requests around `cpu: 250m, memory: 512Mi` with a `1Gi` memory limit are a sensible start. The app shuts
down gracefully (25s), so keep `terminationGracePeriodSeconds` at 30 or more, and a `preStop` sleep of
5-10s helps the load balancer deregister the pod before it stops. It's fully stateless (JWT auth, all state
in MySQL), so you can run 2+ replicas behind an HPA on CPU and add a PodDisruptionBudget. Several replicas
starting at once is fine: Flyway takes a database lock so only one runs migrations.

**Frontend Deployment.** Container port `8080`, non-root nginx. Probes on `/healthz`. Set `BACKEND_URL` to
the backend Service. nginx resolves that name when it starts, so create the backend Service before (or
with) the frontend Deployment. Tiny resources: `cpu: 50m, memory: 64Mi`.

**Ingress.** With the AWS Load Balancer Controller, an ALB Ingress with two options:

1. Route `/` to the frontend Service only; nginx forwards `/api` to the backend. Simplest.
2. Route `/api` to the backend Service and `/` to the frontend Service in the same Ingress. Same origin, so
   no CORS needed, and API traffic skips the nginx hop.

Terminate TLS on the ALB with an ACM certificate. Point the ALB health check at `/healthz` (frontend) and
`/actuator/health/readiness` (backend).

**Observability.** Prometheus metrics at `/actuator/prometheus` (HTTP latency, JVM, HikariCP pool, and so
on); scrape it with a ServiceMonitor or pod annotations. Logs go to stdout in a single-line format with the
request id, ready for Fluent Bit to ship to CloudWatch.

**Suggested learning path.** Get it running with `docker compose`, then on EKS with MySQL in-cluster; then
move MySQL to RDS and secrets to Secrets Manager; then add the ALB Ingress with TLS, HPA, PDB, network
policies (only the backend may reach MySQL), Prometheus/Grafana, and finally a CI pipeline that builds,
pushes to ECR and rolls out. Run `scripts/smoke-test.sh https://your-domain` after each change.

## Troubleshooting

`Communications link failure` at backend startup means it can't reach MySQL: check `DB_HOST`, the security
group, and that the database exists. `Access denied for user` is the credentials. If the backend starts but
the UI says "Can't reach the server", check `BACKEND_URL` on the frontend pod and the Ingress routing for
`/api`. If users get logged out when you scale the backend, the replicas have different `JWT_SECRET`
values. "Too many wrong PIN attempts" (HTTP 423) clears itself after 15 minutes.
