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
- **Hover over an option's vote count/percentage to see the names of voters who selected that option**
- Server-side validation and global error handling
- Transactional vote recording
- PostgreSQL-ready configuration
- Neon-compatible SSL PostgreSQL connection
- Small Hikari connection pool suitable for a free/small database plan
- Actuator health endpoint for deployment checks: `/actuator/health`
- Maven tests + JaCoCo coverage
- Dockerfile-based deployment

## Requirements

- Java 17+
- Maven 3.9+
- Docker Desktop (recommended for Docker testing)
- A PostgreSQL database such as Neon for production-style deployment

# Fork and Run Your Own Copy

## 1. Fork the repository

Open the GitHub repository and click **Fork** to create your own copy under your GitHub account.

Then clone your fork:

```bash
git clone https://github.com/YOUR-GITHUB-USERNAME/online-voting.git
cd online-voting
```

Replace `YOUR-GITHUB-USERNAME` with your GitHub username.

## 2. Run locally with H2

H2 is the easiest option for local development and requires no database setup.

```bash
mvn clean verify
mvn spring-boot:run
```

Open:

```text
http://localhost:8092/
```

The H2 database is in memory, so data is lost when the application stops.

## 3. Run locally with Neon PostgreSQL

For testing the same database style used in deployment, create a Neon PostgreSQL database and configure it locally.

Create:

```text
src/main/resources/application-local.properties
```

Do **not** commit this file. It is already included in `.gitignore`.

Add:

```properties
spring.datasource.url=jdbc:postgresql://YOUR-NEON-HOST/YOUR-DATABASE?sslmode=require&channelBinding=require
spring.datasource.username=YOUR-NEON-USERNAME
spring.datasource.password=YOUR-NEON-PASSWORD
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=0
spring.datasource.hikari.connection-timeout=10000
spring.datasource.hikari.validation-timeout=5000
```

Run the application with the local profile.

### Windows PowerShell

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run
```

### Linux/macOS

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Open:

```text
http://localhost:8092/
```

## 4. Run with Docker locally

Build the Docker image:

```bash
docker build -t online-voting .
```

Run it using an external PostgreSQL database such as Neon:

```bash
docker run --rm -p 8092:8092 \
  -e DATABASE_URL="jdbc:postgresql://YOUR-NEON-HOST/YOUR-DATABASE?sslmode=require&channelBinding=require" \
  -e DATABASE_USERNAME="YOUR-NEON-USERNAME" \
  -e DATABASE_PASSWORD="YOUR-NEON-PASSWORD" \
  -e PORT=8092 \
  -e DDL_AUTO=update \
  online-voting
```

Open:

```text
http://localhost:8092/
```

# Deploy to AWS EC2 with Docker + Neon

This project can be deployed directly to an EC2 instance using the Dockerfile. Docker Compose is not required.

The deployment architecture is:

```text
GitHub
   │
   ▼
AWS EC2
Docker container
   │
   ▼
Neon PostgreSQL
   │
   ▼
Public users
```

## 1. Prepare an EC2 instance

Use an EC2 instance with Docker installed and a security group that allows SSH access. For the initial test deployment, allow TCP port `8092` from your required source so the application can be reached at:

```text
http://YOUR-EC2-PUBLIC-IP:8092/
```

For a real public deployment, HTTPS through a reverse proxy/load balancer is recommended instead of exposing port 8092 directly.

## 2. SSH into EC2

Example:

```bash
ssh -i YOUR-KEY.pem ec2-user@YOUR-EC2-PUBLIC-IP
```

If your EC2 image uses another username, such as `ubuntu`, use that username instead.

## 3. Create the production environment file

Create a directory for the application configuration:

```bash
sudo mkdir -p /opt/voteflow
sudo nano /opt/voteflow/.env
```

Add:

```env
DATABASE_URL=jdbc:postgresql://YOUR-NEON-HOST/YOUR-DATABASE?sslmode=require&channelBinding=require
DATABASE_USERNAME=YOUR-NEON-USERNAME
DATABASE_PASSWORD=YOUR-NEON-PASSWORD
PORT=8092
DDL_AUTO=update
DB_MAX_POOL_SIZE=5
DB_MIN_IDLE=0
DB_CONNECTION_TIMEOUT_MS=10000
DB_VALIDATION_TIMEOUT_MS=5000
```

Replace the `YOUR-...` values with the connection details from your Neon project.

Secure the file:

```bash
sudo chmod 600 /opt/voteflow/.env
```

**Never commit this file, the Neon password, AWS credentials, or an EC2 private key to GitHub.**

## 4. Clone the fork on EC2

```bash
cd /opt
git clone https://github.com/YOUR-GITHUB-USERNAME/online-voting.git
cd /opt/online-voting
```

## 5. Build the Docker image

```bash
docker build -t online-voting .
```

The Dockerfile builds the Spring Boot application with Java 17 and packages it into a lightweight runtime image.

## 6. Run the application

```bash
docker run -d \
  --name voteflow-app \
  --restart unless-stopped \
  -p 8092:8092 \
  --env-file /opt/voteflow/.env \
  online-voting
```

Check that the container is running:

```bash
docker ps
```

View application logs:

```bash
docker logs -f voteflow-app
```

Check the health endpoint from EC2:

```bash
curl http://localhost:8092/actuator/health
```

A healthy application should return a response containing:

```json
{"status":"UP"}
```

Then open from your browser:

```text
http://YOUR-EC2-PUBLIC-IP:8092/
```

## 7. Updating the application on EC2

After pushing changes to your fork:

```bash
cd /opt/online-voting
git pull

docker build -t online-voting .
docker rm -f voteflow-app || true
docker run -d \
  --name voteflow-app \
  --restart unless-stopped \
  -p 8092:8092 \
  --env-file /opt/voteflow/.env \
  online-voting
```

Your Neon database remains separate from the application container, so rebuilding/replacing the container does not remove the database data.

## 8. Optional: stable public URL

An EC2 public IP can change if the instance is stopped and started. For a stable URL, use an Elastic IP or a domain name. For production use, put HTTPS in front of the application using a reverse proxy or AWS load balancer.

# Neon PostgreSQL

Neon provides managed PostgreSQL. The application uses a JDBC PostgreSQL connection and encrypted connections.

### Create the Neon database

1. Create a Neon project.
2. Open **Connect** in the Neon dashboard.
3. Copy the PostgreSQL connection details.
4. Use the JDBC form required by Spring Boot:

```text
jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require&channelBinding=require
```

5. Keep the database credentials outside Git.

For this project, EC2 + Neon is a good fit:

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

# How Public Voting Works

### 1. Create a poll

Create a question and at least two options.

The server generates:

- a poll ID
- an 8-character share code
- a private creator token

The creator receives a link similar to:

```text
http://YOUR-EC2-PUBLIC-IP:8092/p/7XK92A
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

### 4. Live results and voter details

The browser refreshes the poll every five seconds. Multiple people can therefore see results change while the poll is active.

Each option's vote count and percentage is interactive. **Hover over the count/percentage to open a small voter list showing the display names of everyone who selected that option.** The voter names are loaded from the vote records and are not used as the uniqueness key.

### 5. Close the poll

The creator's private admin token is stored locally on the creator's browser after poll creation. Only a request containing the matching `X-Poll-Admin-Token` can close the poll.

Do not expose or commit this token.

# API

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

# Docker

The repository uses a **Dockerfile** for container builds. Docker Compose is not required.

Build:

```bash
docker build -t online-voting .
```

Run with environment variables:

```bash
docker run --rm -p 8092:8092 \
  --env-file /opt/voteflow/.env \
  online-voting
```

# Troubleshooting

## Poll page returns HTTP 500

The application intentionally sets `spring.jpa.open-in-view=false`. `Poll.options` is a lazy JPA collection, so API reads use fetch-join queries to load the options before the entity leaves the data-access layer. This avoids relying on a long-lived Hibernate session during HTTP response rendering.

## EC2 container is running but the browser cannot connect

Check:

1. The container is running:

```bash
docker ps
```

2. The application is healthy:

```bash
curl http://localhost:8092/actuator/health
```

3. EC2 security group allows TCP `8092` for the initial deployment.
4. You are using the correct EC2 public IP.
5. Check application logs:

```bash
docker logs voteflow-app
```

## Database connection fails

Verify the values in `/opt/voteflow/.env`, especially:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

The Neon URL should use the JDBC format and SSL parameters shown above.

# Pre-CI/CD Production Checklist

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
- [x] Dockerfile deployment
- [x] Environment-based database configuration
- [x] Responsive professional UI
- [x] Live result refresh without destroying the voter input
- [x] Voter names displayed interactively for each option's results
- [ ] HTTPS/domain/reverse proxy
- [ ] Production rate limiting/WAF
- [ ] Verified voter identity (OTP/auth) if stronger voting integrity is required
- [ ] CI/CD pipeline

# Jenkins Pipeline Target

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
