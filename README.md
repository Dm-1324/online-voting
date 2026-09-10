# VoteFlow — Live Online Voting

VoteFlow is a Spring Boot live-polling application. A creator can create a poll, receive a unique shareable link, and share it with anyone. Voters can open the link, enter a display name, vote once from their browser/device identity, and see results refresh automatically.

> **Important:** this is a public polling application, not a legally secure election system. The current voter-control mechanism is an anonymous browser/device ID. A determined user can bypass it by clearing browser storage or changing devices. For high-integrity elections, add verified email/OTP or an authenticated identity provider before production election use.

## Architecture

```text
Browser
   │
   │ HTTP/HTTPS
   ▼
EC2 — Spring Boot API + static UI
   │
   ▼
Neon PostgreSQL (production)
```

The app also supports H2 automatically for simple local development.

## Features

- Professional responsive VoteFlow UI
- Create polls with 2–10 options
- Unique 8-character share code per poll
- Clean public URLs: `/p/{shareCode}`
- Copy/shareable poll links
- Anonymous voter/device ID generated in the browser
- One vote per voter ID per poll, backed by a database unique constraint
- Creator-only poll closing using a private admin token
- Automatic result refresh every 5 seconds
- Server-side validation and global error handling
- Transactional vote recording
- PostgreSQL-ready configuration
- Neon-compatible SSL PostgreSQL connection
- Small Hikari connection pool suitable for a free/small database plan
- Actuator health endpoint for deployment checks: `/actuator/health`
- Maven tests + JaCoCo coverage
- Docker and Docker Compose support

## Requirements

- Java 17+
- Maven 3.9+
- Docker Desktop (recommended for PostgreSQL testing)

## Option 1 — Run with Maven + H2

```bash
git clone https://github.com/Dm-1324/online-voting.git
cd online-voting
mvn clean verify
mvn spring-boot:run
```

Open:

```text
http://localhost:8092/
```

H2 is the local default. Its data is not suitable for production.

## Option 2 — Run the full stack with PostgreSQL

This is the recommended way to test the production-style database setup locally.

```bash
docker compose up --build
```

Open:

```text
http://localhost:8092/
```

Stop it with:

```bash
docker compose down
```

To also remove the local PostgreSQL volume/data:

```bash
docker compose down -v
```

## Neon PostgreSQL — recommended low-cost production database

Neon provides managed PostgreSQL and currently has a Free plan. Neon documents a Free plan with 10 projects, 0.5 GB storage per project, 50 CU-hours/month per project, 5 GB egress/month, and scale-to-zero behavior. Limits can change, so check the current Neon pricing page before relying on a free tier for sustained public traffic. citeturn0search0

Neon works with Java/JDBC. Neon requires encrypted connections and provides the database connection string from the project's **Connect** dialog. citeturn0search8turn0search12

### Create the Neon database

1. Create a Neon project.
2. Use a stable PostgreSQL release such as PostgreSQL 17 for this application.
3. Open **Connect** in the Neon dashboard.
4. Copy the PostgreSQL connection details.
5. Convert the connection string to the JDBC form used by Spring Boot:

```text
jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require&channelBinding=require
```

6. Set these variables on the EC2 host or through your deployment secret mechanism:

```text
DATABASE_URL=jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require&channelBinding=require
DATABASE_USERNAME=YOUR_NEON_USERNAME
DATABASE_PASSWORD=YOUR_NEON_PASSWORD
DDL_AUTO=update
PORT=8092
DB_MAX_POOL_SIZE=5
DB_MIN_IDLE=0
```

Spring Boot supports environment variables and externalized configuration, so these values do not need to be committed to Git. citeturn1search2

For this project, **EC2 + Neon** is a good fit:

```text
Public users
     │
     ▼
EC2 / VoteFlow
     │
     │ TLS PostgreSQL
     ▼
Neon PostgreSQL
```

Neon should be treated as the persistent source of truth for polls and votes; do not put database credentials in the Docker image or Git repository.

## How public voting works

### 1. Create a poll

Create a question and at least two options.

The server generates:

- a poll ID
- an 8-character share code
- a private creator token

The creator receives a link similar to:

```text
http://YOUR_EC2_PUBLIC_IP:8092/p/7XK92A
```

or, after adding a domain and HTTPS:

```text
https://your-domain.com/p/7XK92A
```

### 2. Share the link

Anyone who receives the link can open it. The public page loads only that poll.

### 3. Vote

The browser creates an anonymous voter ID and stores it in local storage. The vote request sends that ID in the `X-Voter-Id` header.

The database enforces:

```text
one poll + one voter ID = one vote
```

The display name is stored with the vote for the poll record, but it is not the identity used for uniqueness.

### 4. Live results

The browser refreshes the poll every five seconds. Multiple people can therefore see results change while the poll is active.

For a future high-scale version, replace polling with WebSockets/SSE and move rate limiting/session state to shared infrastructure.

### 5. Close the poll

The creator's private admin token is stored locally on the creator's browser after poll creation. Only a request containing the matching `X-Poll-Admin-Token` can close the poll.

Do not expose or commit this token.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/polls` | List polls |
| GET | `/api/polls/{id}` | Get a poll by ID |
| GET | `/api/polls/public/{shareCode}` | Get a poll using its public share code |
| POST | `/api/polls` | Create a poll and receive creator token + share URL |
| POST | `/api/polls/{id}/vote` | Cast a vote |
| POST | `/api/polls/{id}/close` | Close a poll; requires creator token |
| GET | `/actuator/health` | Deployment health check |

### Create poll

```json
{
  "question": "Which option do you prefer?",
  "options": ["Option A", "Option B", "Option C"]
}
```

### Vote

Header:

```text
X-Voter-Id: <browser-generated-id>
```

Body:

```json
{
  "voterName": "Dhruv",
  "optionId": 1
}
```

### Close poll

Header:

```text
X-Poll-Admin-Token: <creator-token>
```

## Docker

Build:

```bash
docker build -t online-voting .
```

Run against Neon or another externally configured PostgreSQL database:

```bash
docker run --rm -p 8092:8092 \
  -e DATABASE_URL="jdbc:postgresql://HOST/DBNAME?sslmode=require&channelBinding=require" \
  -e DATABASE_USERNAME="voting" \
  -e DATABASE_PASSWORD="CHANGE_ME" \
  online-voting
```

## Why the poll page previously returned HTTP 500

The application intentionally sets `spring.jpa.open-in-view=false`. Spring Boot documents that Open EntityManager in View is what normally keeps a Hibernate session available to lazy-load associations during web response rendering. citeturn2search0

`Poll.options` is a lazy JPA collection. The API was returning a `Poll` and Jackson was trying to serialize `options` after the repository session had already closed. That can produce a `LazyInitializationException` and the browser then displayed **Internal Server Error**.

The repository now uses fetch-join queries for poll reads so `Poll.options` is loaded before the entity leaves the data-access layer. This keeps `open-in-view=false` and avoids relying on a long-lived Hibernate session during HTTP response rendering.

## Pre-CI/CD production checklist

Before Jenkins deployment:

- [x] Persistent PostgreSQL support
- [x] Neon-compatible database configuration
- [x] Public shareable poll URLs
- [x] Creator-only close operation
- [x] Server-side vote validation
- [x] Database-backed duplicate-vote constraint
- [x] Transactional voting
- [x] Lazy-loading bug fixed for API responses
- [x] Health endpoint
- [x] Docker Compose local production-like stack
- [x] Environment-based database configuration
- [x] Responsive professional UI
- [x] Live result refresh without destroying the voter input
- [ ] HTTPS/domain/reverse proxy
- [ ] Production rate limiting/WAF
- [ ] Verified voter identity (OTP/auth) if stronger voting integrity is required
- [ ] CI/CD pipeline

## Jenkins pipeline target

The intended CI/CD flow is a Jenkins **Pipeline job** using a `Jenkinsfile` stored in this repository:

```text
GitHub push
   ↓
Jenkins Pipeline
   ↓
Checkout
   ↓
Maven clean verify
   ↓
JaCoCo
   ↓
SonarQube analysis
   ↓
SonarQube quality gate
   ↓
Docker build
   ↓
Push image to container registry
   ↓
Deploy to AWS EC2
   ↓
Health check
```

Secrets such as GitHub credentials, registry credentials, AWS credentials, SSH keys, and database passwords must be stored in Jenkins Credentials, never in the Jenkinsfile or repository.
