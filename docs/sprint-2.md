# Sprint 2 - Permission Check Before Generation

## Goals

- Create deploy-ready `aiDocker/...` branches from cloned workspaces.
- Add generated files to the branch.
- Commit and push generated files to GitHub.
- Create a pull request automatically.
- Add a dummy file first to verify branch, push, and PR permissions before the agent asks for Docker, CI/CD, and Kubernetes generation details.

## Smoke Test Flow

1. Login with GitHub.
2. Submit a repository Git URL through the chat flow.
3. Backend clones the repository and returns `repositoryWorkspaceId`.
4. Backend analyzes the cloned repository and writes `.ai-docker/repository-analysis.json`.
5. If multiple executable Maven module candidates are detected, the agent asks the user to confirm which modules are deployable services.
6. Clone response returns `nextAction: ASK_DUMMY_PR_PERMISSION` and an assistant prompt explaining this is only a permission check before collecting Docker, CI/CD, and Kubernetes details.
7. If the user confirms, call `POST /api/sprint2/dummy-pull-request` with that workspace id.
8. Backend creates an `aiDocker/deploy-ready-*` branch.
9. Backend writes `aiDocker-pr-check.txt`.
10. Backend commits and pushes the branch.
11. Backend opens a GitHub pull request.
12. Backend stores branch, commit, PR number, PR URL, and status on the workspace record.
13. Agent proceeds to ask for Docker, CI/CD, and Kubernetes requirements.

## API

```http
POST /api/sprint2/dummy-pull-request
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
