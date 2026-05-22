# AI Docker Agent

AI Docker Agent is a deployment automation assistant for Java Spring Maven repositories. The long-term flow is:

1. Ask the user to login with GitHub.
2. Ask the user for an HTTPS Git URL with read access.
3. Inspect the project and ask follow-up questions.
4. Create an `aiDocker` branch.
5. Add Docker, CI/CD, and Kubernetes deployment files.
6. Open a pull request back to the source repository.

## Current Scope

- Spring Boot backend service.
- MongoDB connection for an existing Mongo database.
- Mongo migration structure.
- React dashboard that starts as a chat agent and asks for a Git URL.
- GitHub OAuth login.
- JGit repository cloning into a local workspace.
- Mongo-persisted conversation and repository workspace state.
- Per-user conversation isolation based on the logged-in GitHub user.
- Permission-check PR flow for branch creation, verification commit, push, and pull request access before Docker, CI/CD, and Kubernetes generation.
- Docker deployment file generation for `Dockerfile`, `.dockerignore`, `.env.example`, `docker-compose.yml`, and `README_DEPLOY.md`, followed by a deployment pull request.
- Repository analysis persisted in MongoDB and written to `.ai-docker/repository-analysis.json` inside cloned workspaces.

## Project Layout

```text
backend/   Spring Boot API and Mongo persistence
frontend/  React chat dashboard
docs/      Product capability notes and architecture decisions
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
OLLAMA_ENABLED=true
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=deepseek-coder
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

## Permission Check PR

After a repository is cloned, use the returned `repositoryWorkspaceId`:

```bash
curl -X POST http://localhost:9040/api/deployment-permissions/pull-request-check \
  -H "Content-Type: application/json" \
  -d '{"repositoryWorkspaceId":"<workspace-id>","baseBranch":"main"}'
```

This endpoint requires the active GitHub login session from the browser flow.

## Docker Config Generation

After repository analysis, and after the user confirms in the chat, generate Docker deployment files and open a pull request:

```bash
curl -X POST http://localhost:9040/api/deployment-permissions/docker-configs \
  -H "Content-Type: application/json" \
  -d '{"repositoryWorkspaceId":"<workspace-id>"}'
```

Generated files are written into the cloned repository workspace and committed first. Then local Ollama `deepseek-coder` reviews them; any AI edits are committed separately before the deployment pull request is opened or updated.
