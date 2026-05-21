# Repository Analysis

After a repository is cloned, the backend analyzes the workspace and stores the result in two places:

- MongoDB collection: `repository_analysis`
- Local workspace artifact: `.ai-docker/repository-analysis.json`

The local artifact is added to the cloned repository's `.git/info/exclude`, so `.ai-docker/` is not checked back into the target repository.

## Extracted Data

- Maven project detection.
- Spring Boot project detection.
- `pom.xml` metadata: group id, artifact id, version, packaging, Java version.
- Application config files.
- Application ports from Spring config.
- Database technologies from Maven dependencies.
- Environment variables from Spring placeholders and `System.getenv(...)` usage.
- Executable Maven module candidates, based on Spring Boot `jar` or `war` modules.

## API

Analysis runs automatically after clone. It can also be re-run manually:

```http
POST /api/analysis/{repositoryWorkspaceId}
```

The endpoint is scoped to the logged-in GitHub user.

Executable modules can be confirmed after analysis:

```http
POST /api/analysis/{repositoryAnalysisId}/executable-modules
```

```json
{
  "executableModules": ["service-a/pom.xml", "service-b/pom.xml"]
}
```

The selected modules are persisted in MongoDB and written back to `.ai-docker/repository-analysis.json`.

## Debug Records

The UI has a small hidden-style `Debug records` button in the sidebar once a conversation is active. It calls:

```http
GET /api/debug/conversations/{conversationId}
```

The response includes the conversation, repository workspace records, and repository analysis records for the logged-in user only.
