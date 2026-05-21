# Sprint 1 - Basic Project Setup

## Goals

- Initialize a backend service for the AI Docker Agent.
- Configure MongoDB access using an existing database.
- Add a migration mechanism for MongoDB.
- Create a React dashboard with a chat-agent first screen.
- Capture the first user input: a Git URL with read access.
- Authenticate with GitHub OAuth.
- Clone repositories into a local workspace.
- Persist clone and conversation state in MongoDB.
- Scope conversations and repository workspaces to the logged-in GitHub user.

## Out Of Scope

- AI project analysis.
- Creating Docker, CI/CD, or Kubernetes files.
- Creating branches or pull requests.
- Multi-user authorization beyond GitHub OAuth login.

## Initial Conversation Flow

1. Agent greets the user.
2. Agent asks the user to login with GitHub.
3. Agent asks for a Java Spring Maven HTTPS Git URL with read access.
4. User submits the URL.
5. Backend creates a conversation record with status `GIT_URL_RECEIVED`.
6. Backend creates a `repository_workspaces` Mongo record with status `CLONING` and the planned local workspace path.
7. Backend updates the conversation status to `CLONING` and stores the workspace record id and path.
8. Backend clones the repository locally using JGit and the GitHub OAuth token.
9. Backend updates the workspace record and conversation to `CLONED` or `CLONE_FAILED`.
10. Frontend confirms the repository clone path.

## MongoDB

The backend uses the existing MongoDB instance configured through `MONGODB_URI`. Migration classes live under `com.aidocker.agent.migration`.

Repository clone attempts are persisted in the `repository_workspaces` collection. Conversation records also keep the current clone status, workspace record id, branch, and local path so the service can recover state after restart.

## User Isolation

The GitHub numeric user id is stored on every conversation and repository workspace record. Conversation reads and clone-state updates are filtered by that id, and local clones are written under a per-user workspace folder.
