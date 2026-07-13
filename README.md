# Multi-Tenant SaaS Billing & Subscription Engine

A containerized billing platform: React dashboard + Spring Boot API + Stripe,
built for reliable local and CI testing via Docker, and deployed to Kubernetes.

## Stack
- **Frontend**: React 18, Axios, Server-Sent Events for real-time payment status
- **Backend**: Spring Boot 3 (Java 17), Spring Data JPA, Stripe Java SDK
- **Payments**: Stripe (Customers, Subscriptions, Invoices, Webhooks)
- **Data**: PostgreSQL (H2 in-memory for tests)
- **Infra**: Docker, docker-compose, Kubernetes, GitHub Actions CI/CD, AWS-ready

## Architecture

```
frontend (React, nginx)  --->  backend (Spring Boot)  --->  Stripe API
        ^                            |    ^
        | SSE (real-time)            |    |
        |                            v    |
        +------------------  PostgreSQL   +--- Stripe Webhooks
```

## Key edge cases handled
- **Failed payments**: `invoice.payment_failed` webhook flips the subscription to
  `PAST_DUE` in real time; the dashboard reflects it via SSE without polling.
- **Prorated upgrades/downgrades**: plan changes call Stripe with
  `proration_behavior=create_prorations`; the UI distinguishes prorated charges
  (upgrade) from prorated credits (downgrade).
- **Concurrent operations**: subscription rows use a `@Version` column
  (optimistic locking) plus a pessimistic write lock during plan-change
  transactions, so two racing upgrade requests can't corrupt state — the loser
  gets a `409 Conflict` and the frontend surfaces it clearly.
- **Webhook idempotency**: invoice upserts are keyed by Stripe invoice ID, so
  Stripe's automatic retries never create duplicate invoice rows.

## Local development

```bash
cp .env.example .env   # fill in your Stripe test keys
docker compose up --build
```
- Frontend: http://localhost:3000
- Backend: http://localhost:8080/api
- Postgres: localhost:5432

## Running tests

```bash
# Backend (JUnit 5, H2 in-memory, covers proration/concurrency/failed-payment cases)
cd backend && mvn test

# Frontend (Jest + React Testing Library)
cd frontend && npm test
```

## Deploying to Kubernetes

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-config-secrets.yaml   # edit secrets first!
kubectl apply -f k8s/02-postgres.yaml
kubectl apply -f k8s/03-backend.yaml
kubectl apply -f k8s/04-frontend.yaml
```

CI/CD (`.github/workflows/ci.yml`) runs backend + frontend tests, builds and
pushes Docker images, runs a docker-compose integration smoke test, and rolls
out to Kubernetes on merge to `main`.

## Stripe webhook setup
Point a Stripe webhook endpoint at `POST /api/webhooks/stripe` and subscribe to:
- `invoice.payment_succeeded`
- `invoice.payment_failed`
- `customer.subscription.updated`
- `customer.subscription.deleted`

Set `STRIPE_WEBHOOK_SECRET` to the signing secret Stripe gives you for that endpoint.
