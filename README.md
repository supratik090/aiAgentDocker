# AI Docker Agent

AI Docker Agent is a sprint-built assistant for Java Spring Maven repositories. The long-term flow is:

1. Ask the user for a Git URL with read access.
2. Inspect the project and ask follow-up questions.
3. Create an `aiDocker` branch.
4. Add Docker, CI/CD, and Kubernetes deployment files.
5. Open a pull request back to the source repository.

## Sprint 1 Scope

- Spring Boot backend service.
- MongoDB connection for an existing Mongo database.
- Mongo migration structure.
- React dashboard that starts as a chat agent and asks for a Git URL.

## Project Layout

```text
backend/   Spring Boot API and Mongo persistence
frontend/  React chat dashboard
docs/      Sprint notes and architecture decisions
```

## Local Backend

Create a backend env file from the example:

```bash
cp backend/.env.example backend/.env
```

Set `MONGODB_URI` to your existing MongoDB connection string, then run:

```bash
cd backend
mvn spring-boot:run
```

The backend runs on port `9040` by default and exposes `GET /api/health` and `POST /api/conversations`.

## Local Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on port `9041` by default and expects the backend at `http://localhost:9040`. Set `VITE_API_BASE_URL` if needed.
