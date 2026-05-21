# Sprint 1 - Basic Project Setup

## Goals

- Initialize a backend service for the AI Docker Agent.
- Configure MongoDB access using an existing database.
- Add a migration mechanism for MongoDB.
- Create a React dashboard with a chat-agent first screen.
- Capture the first user input: a Git URL with read access.

## Out Of Scope

- Cloning repositories.
- AI project analysis.
- Creating Docker, CI/CD, or Kubernetes files.
- Creating branches or pull requests.
- Authentication and multi-user support.

## Initial Conversation Flow

1. Agent greets the user.
2. Agent asks for a Java Spring Maven Git URL with read access.
3. User submits the URL.
4. Backend creates a conversation record with status `GIT_URL_RECEIVED`.
5. Frontend confirms the next sprint will inspect the repository and ask follow-up questions.

## MongoDB

The backend uses the existing MongoDB instance configured through `MONGODB_URI`. Migration classes live under `com.aidocker.agent.migration`.
