# VoteFlow — Live Online Voting

VoteFlow is a Spring Boot live-polling application. A creator can create a poll, receive a unique shareable link, and share it with anyone. Voters can open the link, enter a display name, vote once from their browser/device identity, and see results refresh automatically.

> **Important:** this is a public polling application, not a legally secure election system. The current voter-control mechanism is an anonymous browser/device ID. A determined user can bypass it by clearing browser storage or changing devices. For high-integrity elections, add verified email/OTP or an authenticated identity provider before production election use.

## Architecture

```text
Browser
   │
   │ HTTPS / REST
   ▼
Spring Boot API + static UI
   │
   ▼
PostgreSQL (production)
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

## How public voting works

### 1. Create a poll

Create a question and at least two options.

The server generates:

- a poll ID
- an 8-character share code
- a private creator token

The creator receives a link similar to:

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

The response contains:

```json
{
  "poll": { "id": 1, "shareCode": "7XK92A" },
  "adminToken": "private-token",
  "shareUrl": "/p/7XK92A"
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

## Production database configuration

Set these environment variables in the deployment environment; do not hard-code credentials in Git:

```text
DATABASE_URL=jdbc:postgresql://HOST:5432/votingdb
DATABASE_USERNAME=voting
DATABASE_PASSWORD=CHANGE_ME
PORT=8092
DDL_AUTO=update
```

See `.env.example` for the variable names.

For a serious production database, use managed PostgreSQL, automated backups, restricted network access, TLS, and a migration tool such as Flyway rather than relying indefinitely on Hibernate `ddl-auto=update`.

## Docker

Build:

```bash
docker build -t online-voting .
```

Run against an externally configured database:

```bash
docker run --rm -p 8092:8092 \
  -e DATABASE_URL="jdbc:postgresql://HOST:5432/votingdb" \
  -e DATABASE_USERNAME="voting" \
  -e DATABASE_PASSWORD="CHANGE_ME" \
  online-voting
```

## Pre-CI/CD production checklist

Before Jenkins deployment:

- [x] Persistent PostgreSQL support
- [x] Public shareable poll URLs
- [x] Creator-only close operation
- [x] Server-side vote validation
- [x] Database-backed duplicate-vote constraint
- [x] Transactional voting
- [x] Health endpoint
- [x] Docker Compose local production-like stack
- [x] Environment-based database configuration
- [x] Responsive professional UI
- [x] Live result refresh without destroying the voter input
- [ ] HTTPS/domain/reverse proxy
- [ ] Managed PostgreSQL + backups
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
Deploy to AWS
   ↓
Health check
```

Secrets such as GitHub credentials, registry credentials, AWS credentials, SSH keys, and database passwords must be stored in Jenkins Credentials, never in the Jenkinsfile or repository.
