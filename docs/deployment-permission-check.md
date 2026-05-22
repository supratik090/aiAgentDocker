# Deployment Permission Check Before Generation

## Goals

- Create deploy-ready `aiDocker/...` branches from cloned workspaces.
- Add generated files to the branch.
- Commit and push generated files to GitHub.
- Create a pull request automatically.
- Add a verification file first to verify branch, push, and PR permissions before the agent asks for Docker, CI/CD, and Kubernetes generation details.

## Smoke Test Flow

1. Login with GitHub.
2. Submit a repository Git URL through the chat flow.
3. Backend clones the repository and returns `repositoryWorkspaceId`.
4. Backend analyzes the cloned repository and writes `.ai-docker/repository-analysis.json`.
5. If multiple executable Maven module candidates are detected, the agent asks the user to confirm which modules are deployable services.
6. Clone response returns `nextAction: ASK_PULL_REQUEST_PERMISSION_CHECK` and an assistant prompt explaining this is only a permission check before collecting Docker, CI/CD, and Kubernetes details.
7. If the user confirms, call `POST /api/deployment-permissions/pull-request-check` with that workspace id.
8. Backend creates an `aiDocker/deploy-ready-*` branch.
9. Backend writes `aiDocker-permission-check.txt`.
10. Backend commits and pushes the branch.
11. Backend opens a GitHub pull request.
12. Backend stores branch, commit, PR number, PR URL, and status on the workspace record.
13. Agent asks whether to generate Docker deployment files and create a pull request.
14. If the user confirms, call `POST /api/deployment-permissions/docker-configs` with that workspace id.
15. Backend writes `Dockerfile`, `.dockerignore`, `.env.example`, `docker-compose.yml`, and `README_DEPLOY.md` into the cloned workspace.
16. Backend commits and pushes the rule-based generated files to the deployment branch.
17. Backend asks local Ollama `deepseek-coder` to review and improve those generated files.
18. Backend applies structured file replacements returned by the local model.
19. If the AI changed files, backend commits and pushes those changes as a separate AI-review commit.
20. If no pull request exists yet, backend opens a GitHub pull request. If the permission-check PR already exists, the new commits update that PR.

## API

```http
POST /api/deployment-permissions/pull-request-check
Content-Type: application/json
```

```json
{
  "repositoryWorkspaceId": "mongo-workspace-id",
  "baseBranch": "main"
}
```

`baseBranch` is optional. If omitted, the backend uses the branch detected during clone, then falls back to `main`.

## Persisted State

The `repository_workspaces` record tracks:

- `deploymentBranch`
- `lastCommitId`
- `pullRequestUrl`
- `pullRequestNumber`
- status: `BRANCH_READY`, `PUSHED`, `PR_CREATED`, or `PR_FAILED`

Conversation status is updated alongside the workspace status.

## Docker Config Generation

```http
POST /api/deployment-permissions/docker-configs
Content-Type: application/json
```

```json
{
  "repositoryWorkspaceId": "mongo-workspace-id"
}
```

Response fields:

- `repositoryWorkspaceId`
- `generatedFiles`
- `deploymentBranch`
- `commitId`
- `pullRequestUrl`
- `pullRequestNumber`
- status: `DOCKER_CONFIGS_GENERATED` or `DOCKER_CONFIGS_FAILED`
- `assistantMessage`
