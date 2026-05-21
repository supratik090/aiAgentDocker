# AI Docker Agent

AI Docker Agent is a sprint-built assistant for Java Spring Maven repositories. The long-term flow is:

1. Ask the user to login with GitHub.
2. Ask the user for an HTTPS Git URL with read access.
3. Inspect the project and ask follow-up questions.
4. Create an `aiDocker` branch.
5. Add Docker, CI/CD, and Kubernetes deployment files.
6. Open a pull request back to the source repository.

## Sprint 1 Scope

- Spring Boot backend service.
- MongoDB connection for an existing Mongo database.
- Mongo migration structure.
- React dashboard that starts as a chat agent and asks for a Git URL.
- GitHub OAuth login.
- JGit repository cloning into a local workspace.
- Mongo-persisted conversation and repository workspace state.
- Per-user conversation isolation based on the logged-in GitHub user.

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

Set `MONGODB_URI` to your existing MongoDB connection string. The backend imports `backend/.env` automatically when started from the `backend` directory:

For GitHub OAuth, create a GitHub OAuth App with this callback URL:

```text
http://localhost:9040/login/oauth2/code/github
```

Then set these values in `backend/.env`:

```text
GITHUB_CLIENT_ID=your-client-id
GITHUB_CLIENT_SECRET=your-client-secret
```

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
