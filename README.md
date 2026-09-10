# Online Voting System

A small Spring Boot voting application with an interactive browser UI, H2 in-memory persistence, duplicate-vote protection, poll closing, and live result refresh.

## Architecture

```text
Browser → Spring Boot (port 8092) → H2 in-memory database
```

## Requirements

- Java 17+
- Maven 3.9+
- Docker (optional)

## Run locally

1. Clone the repository:

```bash
git clone https://github.com/Dm-1324/online-voting.git
cd online-voting
```

2. Build and run the tests:

```bash
mvn clean verify
```

The project includes service-layer tests for validation, duplicate voting, closed polls, invalid options, vote counting, and closing polls. JaCoCo generates a coverage report during the Maven test phase.

3. Start the application:

```bash
mvn spring-boot:run
```

4. Open:

`http://localhost:8092/`

## Using the application

1. Enter a poll question and two options, then create the poll.
2. Enter a voter name and select an option.
3. A voter name can vote only once per poll; this is enforced by the service and database constraint.
4. Results refresh automatically every 3 seconds.
5. Closing a poll disables further voting.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/polls` | List polls |
| GET | `/api/polls/{id}` | Get one poll |
| POST | `/api/polls` | Create a poll |
| POST | `/api/polls/{id}/vote` | Cast a vote |
| POST | `/api/polls/{id}/close` | Close a poll |

Create-poll example:

```json
{"question":"Favorite color?","options":["Red","Blue"]}
```

Vote example:

```json
{"voterName":"Dhruv","optionId":1}
```

## Docker

Build and run:

```bash
docker build -t online-voting .
docker run --rm -p 8092:8092 online-voting
```

Then open `http://localhost:8092/`.

## Important notes

- H2 is configured as an in-memory database, so data is lost when the application stops.
- The application currently identifies voters by name; this is suitable for a demo/test project, not a production election system.
- Authentication, authorization, persistent production storage, audit logging, CSRF/security hardening, and stronger voter identity controls should be added before production use.

## Next step: CI/CD

The repository is intentionally ready for the next pipeline stage: Maven build/test, JaCoCo coverage, SonarQube quality gate, Docker image build, registry push, and deployment can be added without changing the application structure.
