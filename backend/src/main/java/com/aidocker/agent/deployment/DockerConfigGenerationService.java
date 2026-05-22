package com.aidocker.agent.deployment;

import com.aidocker.agent.analysis.GradleMetadata;
import com.aidocker.agent.analysis.NpmMetadata;
import com.aidocker.agent.analysis.PomMetadata;
import com.aidocker.agent.analysis.RepositoryAnalysis;
import com.aidocker.agent.analysis.RepositoryAnalysisRepository;
import com.aidocker.agent.auth.GitHubUser;
import com.aidocker.agent.conversation.ConversationService;
import com.aidocker.agent.conversation.ConversationStatus;
import com.aidocker.agent.repository.GitHubAccessTokenService;
import com.aidocker.agent.repository.RepositoryWorkspace;
import com.aidocker.agent.repository.RepositoryWorkspaceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DockerConfigGenerationService {

    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final RepositoryAnalysisRepository repositoryAnalysisRepository;
    private final GitHubAccessTokenService gitHubAccessTokenService;
    private final DeploymentBranchService deploymentBranchService;
    private final PermissionCheckCommitService permissionCheckCommitService;
    private final GitHubPullRequestService gitHubPullRequestService;
    private final LocalDockerAiReviewService localDockerAiReviewService;
    private final ConversationService conversationService;
    private final Clock clock;

    public DockerConfigGenerationService(
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            RepositoryAnalysisRepository repositoryAnalysisRepository,
            GitHubAccessTokenService gitHubAccessTokenService,
            DeploymentBranchService deploymentBranchService,
            PermissionCheckCommitService permissionCheckCommitService,
            GitHubPullRequestService gitHubPullRequestService,
            LocalDockerAiReviewService localDockerAiReviewService,
            ConversationService conversationService,
            Clock clock
    ) {
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.repositoryAnalysisRepository = repositoryAnalysisRepository;
        this.gitHubAccessTokenService = gitHubAccessTokenService;
        this.deploymentBranchService = deploymentBranchService;
        this.permissionCheckCommitService = permissionCheckCommitService;
        this.gitHubPullRequestService = gitHubPullRequestService;
        this.localDockerAiReviewService = localDockerAiReviewService;
        this.conversationService = conversationService;
        this.clock = clock;
    }

    public GenerateDockerConfigsResponse generateDockerConfigs(
            String principalName,
            GitHubUser user,
            GenerateDockerConfigsRequest request
    ) {
        RepositoryWorkspace workspace = repositoryWorkspaceRepository
                .findByIdAndGithubUserId(request.repositoryWorkspaceId(), user.githubUserId())
                .orElseThrow(() -> new DeploymentPermissionException("Repository workspace was not found for the logged-in user."));
        if (!StringUtils.hasText(workspace.getRepositoryAnalysisId())) {
            throw new DeploymentPermissionException("Repository analysis must complete before Docker config generation.");
        }
        RepositoryAnalysis analysis = repositoryAnalysisRepository
                .findByIdAndGithubUserId(workspace.getRepositoryAnalysisId(), user.githubUserId())
                .orElseThrow(() -> new DeploymentPermissionException("Repository analysis was not found for the logged-in user."));

        Path repositoryPath = Path.of(workspace.getLocalPath()).toAbsolutePath().normalize();
        List<DockerTarget> targets = dockerTargets(analysis);
        int appPort = analysis.getApplicationPorts() == null
                ? 8080
                : analysis.getApplicationPorts().stream().findFirst().orElse(8080);

        try {
            List<Path> generatedFiles = new ArrayList<>();
            for (DockerTarget target : targets) {
                Path dockerfilePath;
                if (".".equals(target.modulePath())) {
                    dockerfilePath = repositoryPath.resolve("Dockerfile");
                } else {
                    Path targetDir = repositoryPath.resolve(target.modulePath());
                    try {
                        Files.createDirectories(targetDir);
                    } catch (IOException exception) {
                        throw new DeploymentPermissionException("Unable to create target directory: " + targetDir, exception);
                    }
                    dockerfilePath = targetDir.resolve("Dockerfile");
                }

                String dockerfileContent;
                if ("GRADLE".equals(target.targetType())) {
                    dockerfileContent = gradleDockerfile(target, appPort);
                } else if ("NPM".equals(target.targetType())) {
                    List<String> scripts = List.of();
                    if (analysis.getNpmMetadata() != null) {
                        scripts = analysis.getNpmMetadata().stream()
                                .filter(n -> target.modulePath().equals(n.path() == null || n.path().equals("package.json") ? "." : Path.of(n.path()).getParent().toString()))
                                .findFirst()
                                .map(NpmMetadata::scripts)
                                .orElse(List.of());
                    }
                    dockerfileContent = npmDockerfile(target, scripts, appPort);
                } else {
                    dockerfileContent = dockerfile(target, appPort);
                }

                generatedFiles.add(write(dockerfilePath, dockerfileContent));
            }

            generatedFiles.add(write(repositoryPath.resolve(".dockerignore"), dockerignore()));
            generatedFiles.add(write(repositoryPath.resolve(".env.example"), environmentTemplate(analysis, appPort)));
            generatedFiles.add(write(repositoryPath.resolve("docker-compose.yml"), dockerCompose(targets, analysis, appPort)));
            generatedFiles.add(write(repositoryPath.resolve("README_DEPLOY.md"), deploymentReadme(targets, analysis, appPort)));

            String accessToken = gitHubAccessTokenService.tokenFor(principalName);
            String deploymentBranch = ensureDeploymentBranch(repositoryPath, workspace);
            List<String> relativeFiles = generatedFiles.stream()
                    .map(path -> repositoryPath.relativize(path).toString())
                    .toList();
            String generatedCommitId = permissionCheckCommitService.commitFilesAndPush(
                    repositoryPath,
                    deploymentBranch,
                    accessToken,
                    relativeFiles,
                    "Add generated Docker deployment files"
            );
            DockerAiReviewResult aiReview = localDockerAiReviewService.reviewAndApply(
                    workspace.getId(),
                    workspace.getConversationId(),
                    workspace.getGithubUserId(),
                    repositoryPath,
                    relativeFiles,
                    analysis
            );
            String aiReviewCommitId = aiReview.updatedFiles().isEmpty()
                    ? generatedCommitId
                    : permissionCheckCommitService.commitFilesAndPush(
                            repositoryPath,
                            deploymentBranch,
                            accessToken,
                            aiReview.updatedFiles(),
                            "Apply local AI review to Docker deployment files"
                    );
            PullRequestResult pullRequest = ensurePullRequest(accessToken, workspace, deploymentBranch, relativeFiles);

            workspace.markPushed(aiReviewCommitId, clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    workspace.getGithubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.PUSHED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );
            workspace.markPullRequestCreated(pullRequest.url(), pullRequest.number(), clock.instant());
            repositoryWorkspaceRepository.save(workspace);

            workspace.markDockerConfigsGenerated(clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    workspace.getGithubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.DOCKER_CONFIGS_GENERATED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );

            return new GenerateDockerConfigsResponse(
                    workspace.getId(),
                    relativeFiles,
                    deploymentBranch,
                    aiReviewCommitId,
                    pullRequest.url(),
                    pullRequest.number(),
                    "DOCKER_CONFIGS_GENERATED",
                    "Docker deployment files generated, reviewed by local deepseek-coder, and pushed.\nFiles: " + relativeFiles
                            + "\nGenerated commit: " + generatedCommitId
                            + "\nAI updated files: " + aiReview.updatedFiles()
                            + "\nAI review commit: " + aiReviewCommitId
                            + "\nAI review: " + aiReview.summary()
                            + "\nBranch: " + deploymentBranch
                            + "\nPull request:\n" + pullRequest.url()
            );
        } catch (RuntimeException exception) {
            workspace.markDockerConfigsFailed(exception.getMessage(), clock.instant());
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    workspace.getGithubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.DOCKER_CONFIGS_FAILED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );
            throw exception;
        }
    }

    private String ensureDeploymentBranch(Path repositoryPath, RepositoryWorkspace workspace) {
        if (StringUtils.hasText(workspace.getDeploymentBranch())) {
            deploymentBranchService.checkoutBranch(repositoryPath, workspace.getDeploymentBranch());
            return workspace.getDeploymentBranch();
        }
        String deploymentBranch = deploymentBranchService.createDeployReadyBranch(repositoryPath);
        workspace.markBranchReady(deploymentBranch, clock.instant());
        repositoryWorkspaceRepository.save(workspace);
        conversationService.updateRepositoryState(
                workspace.getGithubUserId(),
                workspace.getConversationId(),
                ConversationStatus.BRANCH_READY,
                workspace.getId(),
                workspace.getBranch(),
                workspace.getLocalPath()
        );
        return deploymentBranch;
    }

    private PullRequestResult ensurePullRequest(
            String accessToken,
            RepositoryWorkspace workspace,
            String deploymentBranch,
            List<String> generatedFiles
    ) {
        if (StringUtils.hasText(workspace.getPullRequestUrl())) {
            return new PullRequestResult(workspace.getPullRequestUrl(), workspace.getPullRequestNumber());
        }
        String baseBranch = StringUtils.hasText(workspace.getBranch()) ? workspace.getBranch() : "main";
        return gitHubPullRequestService.createPullRequest(
                accessToken,
                GitHubRepository.fromHttpsUrl(workspace.getGitUrl()),
                deploymentBranch,
                baseBranch,
                "Add Docker deployment configuration",
                "AI Docker Agent generated Docker deployment files for this project and reviewed them with local deepseek-coder through Ollama.\n\nFiles:\n- "
                        + String.join("\n- ", generatedFiles)
        );
    }

    private List<DockerTarget> dockerTargets(RepositoryAnalysis analysis) {
        List<String> selectedModules = analysis.getSelectedExecutableModules();
        List<String> executableCandidates = analysis.getExecutableModuleCandidates() == null
                ? List.of()
                : analysis.getExecutableModuleCandidates();
        List<String> targetPaths = selectedModules == null || selectedModules.isEmpty()
                ? executableCandidates
                : selectedModules;
        if (targetPaths.isEmpty()) {
            if (analysis.isGradleProject()) {
                targetPaths = List.of("build.gradle");
            } else if (analysis.isNpmProject()) {
                targetPaths = List.of("package.json");
            } else {
                targetPaths = List.of("pom.xml");
            }
        }
        return targetPaths.stream()
                .map(path -> dockerTarget(path, analysis))
                .toList();
    }

    private DockerTarget dockerTarget(String path, RepositoryAnalysis analysis) {
        Path parentPath = Path.of(path).getParent();
        String modulePath = parentPath == null ? "." : parentPath.toString();

        if (path.endsWith("pom.xml")) {
            List<PomMetadata> pomMetadata = analysis.getPomMetadata() == null ? List.of() : analysis.getPomMetadata();
            PomMetadata pom = pomMetadata.stream()
                    .filter(candidate -> candidate.path().equals(path))
                    .findFirst()
                    .orElse(null);
            String artifactId = pom == null || !StringUtils.hasText(pom.artifactId()) ? "app" : pom.artifactId();
            String javaMajorVersion = pom == null || !StringUtils.hasText(pom.javaVersion()) ? "17" : javaMajorVersion(pom.javaVersion());
            boolean isSpringBoot = pom != null && pom.springBootProject();
            return new DockerTarget(modulePath, artifactId, "MAVEN", javaMajorVersion, isSpringBoot);
        } else if (path.endsWith("build.gradle") || path.endsWith("build.gradle.kts")) {
            List<GradleMetadata> gradleMetadata = analysis.getGradleMetadata() == null ? List.of() : analysis.getGradleMetadata();
            GradleMetadata gradle = gradleMetadata.stream()
                    .filter(candidate -> candidate.path().equals(path))
                    .findFirst()
                    .orElse(null);
            String artifactId = gradle == null || !StringUtils.hasText(gradle.artifactId()) ? "app" : gradle.artifactId();
            String javaMajorVersion = gradle == null || !StringUtils.hasText(gradle.javaVersion()) ? "17" : javaMajorVersion(gradle.javaVersion());
            boolean isSpringBoot = gradle != null && gradle.springBootProject();
            return new DockerTarget(modulePath, artifactId, "GRADLE", javaMajorVersion, isSpringBoot);
        } else if (path.endsWith("package.json")) {
            List<NpmMetadata> npmMetadata = analysis.getNpmMetadata() == null ? List.of() : analysis.getNpmMetadata();
            NpmMetadata npm = npmMetadata.stream()
                    .filter(candidate -> candidate.path().equals(path))
                    .findFirst()
                    .orElse(null);
            String artifactId = npm == null || !StringUtils.hasText(npm.name()) ? "app" : npm.name();
            return new DockerTarget(modulePath, artifactId, "NPM", "20", false);
        }
        return new DockerTarget(modulePath, "app", "MAVEN", "17", false);
    }

    private String javaMajorVersion(String javaVersion) {
        if (javaVersion.startsWith("1.")) {
            String legacyMajorVersion = javaVersion.substring(2).replaceAll("[^0-9].*$", "");
            return StringUtils.hasText(legacyMajorVersion) ? legacyMajorVersion : "17";
        }
        String majorVersion = javaVersion.replaceAll("[^0-9].*$", "");
        return StringUtils.hasText(majorVersion) ? majorVersion : "17";
    }

    private String buildImage(String javaMajorVersion) {
        if ("8".equals(javaMajorVersion)) {
            return "eclipse-temurin:8-jdk";
        }
        return "eclipse-temurin:" + javaMajorVersion + "-jdk";
    }

    private String runtimeImage(String javaMajorVersion) {
        if ("8".equals(javaMajorVersion)) {
            return "eclipse-temurin:8-jre";
        }
        return "eclipse-temurin:" + javaMajorVersion + "-jre";
    }

    private Path write(Path path, String content) {
        try {
            Files.writeString(path, content);
            return path;
        } catch (IOException exception) {
            throw new DeploymentPermissionException("Unable to write " + path.getFileName() + ".", exception);
        }
    }

    private String dockerfile(DockerTarget target, int appPort) {
        return """
                # syntax=docker/dockerfile:1

                FROM %s AS build
                WORKDIR /workspace
                COPY . .
                ARG MODULE_PATH="%s"
                RUN if [ -x ./mvnw ]; then MVN="./mvnw"; else MVN="mvn"; fi \\
                    && if [ "$MODULE_PATH" = "." ]; then $MVN -DskipTests package; else $MVN -pl "$MODULE_PATH" -am -DskipTests package; fi

                FROM %s
                WORKDIR /app
                ARG MODULE_PATH="%s"
                COPY --from=build /workspace/${MODULE_PATH}/target/*.jar /app/app.jar
                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                """.formatted(buildImage(target.versionOrJavaVersion()), target.modulePath(), runtimeImage(target.versionOrJavaVersion()), target.modulePath(), appPort);
    }

    private String gradleDockerfile(DockerTarget target, int appPort) {
        String modulePath = target.modulePath();
        String gradleTask;
        if (target.springBootProject()) {
            if (".".equals(modulePath)) {
                gradleTask = "clean bootJar";
            } else {
                String formattedPath = modulePath.replace('/', ':').replace('\\', ':');
                if (!formattedPath.startsWith(":")) {
                    formattedPath = ":" + formattedPath;
                }
                gradleTask = formattedPath + ":bootJar";
            }
        } else {
            if (".".equals(modulePath)) {
                gradleTask = "clean assemble";
            } else {
                String formattedPath = modulePath.replace('/', ':').replace('\\', ':');
                if (!formattedPath.startsWith(":")) {
                    formattedPath = ":" + formattedPath;
                }
                gradleTask = formattedPath + ":assemble";
            }
        }

        return """
                # syntax=docker/dockerfile:1

                FROM %s AS build
                WORKDIR /workspace
                COPY . .
                RUN if [ -x ./gradlew ]; then GRADLE="./gradlew"; else GRADLE="gradle"; fi \\
                    && $GRADLE --no-daemon %s \\
                    && mkdir -p /workspace/build-out \\
                    && (cp /workspace/%s/build/libs/*[!plain].jar /workspace/build-out/app.jar || cp /workspace/%s/build/libs/*.jar /workspace/build-out/app.jar)

                FROM %s
                WORKDIR /app
                COPY --from=build /workspace/build-out/app.jar /app/app.jar
                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                """.formatted(
                        buildImage(target.versionOrJavaVersion()),
                        gradleTask,
                        modulePath, modulePath,
                        runtimeImage(target.versionOrJavaVersion()),
                        appPort
                );
    }

    private String npmDockerfile(DockerTarget target, List<String> scripts, int appPort) {
        String buildStep = (scripts != null && scripts.contains("build")) ? "RUN npm run build" : "";
        String nodeVersion = target.versionOrJavaVersion();
        String modulePath = target.modulePath();

        if (".".equals(modulePath)) {
            return """
                    # syntax=docker/dockerfile:1

                    FROM node:%s-alpine AS builder
                    WORKDIR /app
                    COPY package*.json ./
                    RUN npm ci
                    COPY . .
                    %s

                    FROM node:%s-alpine
                    WORKDIR /app
                    ENV NODE_ENV=production
                    COPY package*.json ./
                    RUN npm ci --only=production
                    COPY --from=builder /app ./
                    EXPOSE %d
                    CMD ["npm", "start"]
                    """.formatted(nodeVersion, buildStep, nodeVersion, appPort);
        } else {
            return """
                    # syntax=docker/dockerfile:1

                    FROM node:%s-alpine AS builder
                    WORKDIR /app
                    COPY %s/package*.json ./%s/
                    WORKDIR /app/%s
                    RUN npm ci
                    COPY %s/ .
                    %s

                    FROM node:%s-alpine
                    WORKDIR /app
                    ENV NODE_ENV=production
                    COPY %s/package*.json ./%s/
                    WORKDIR /app/%s
                    RUN npm ci --only=production
                    COPY --from=builder /app/%s ./
                    EXPOSE %d
                    CMD ["npm", "start"]
                    """.formatted(
                            nodeVersion,
                            modulePath, modulePath,
                            modulePath,
                            modulePath,
                            buildStep,
                            nodeVersion,
                            modulePath, modulePath,
                            modulePath,
                            modulePath,
                            appPort
                    );
        }
    }

    private String dockerignore() {
        return """
                .git
                .gitignore
                .env
                .env.*
                !.env.example
                .ai-docker
                target
                **/target
                build
                **/build
                .gradle
                .idea
                .vscode
                *.iml
                *.log
                .DS_Store
                node_modules
                npm-debug.log
                docker-compose*.yml
                README_DEPLOY.md
                """;
    }

    private String dockerCompose(List<DockerTarget> targets, RepositoryAnalysis analysis, int appPort) {
        StringBuilder compose = new StringBuilder("services:\n");

        List<String> env = environmentLines(analysis);
        boolean hasDatabase = StringUtils.hasText(databaseService(analysis));
        for (int index = 0; index < targets.size(); index++) {
            DockerTarget target = targets.get(index);
            int hostPort = appPort + index;
            String dockerfilePath = ".".equals(target.modulePath()) ? "Dockerfile" : target.modulePath() + "/Dockerfile";
            compose.append("  ").append(serviceName(target, index)).append(":\n")
                    .append("    build:\n")
                    .append("      context: .\n")
                    .append("      dockerfile: ").append(dockerfilePath).append("\n")
                    .append("      args:\n")
                    .append("        MODULE_PATH: \"").append(target.modulePath()).append("\"\n")
                    .append("    image: ").append(dockerImageName(target.artifactId())).append(":local\n")
                    .append("    ports:\n")
                    .append("      - \"").append(hostPort).append(":").append(appPort).append("\"\n");
        if (!env.isEmpty()) {
            compose.append("    environment:\n");
            env.forEach(line -> compose.append("      - ").append(line).append("\n"));
        }
            if (hasDatabase) {
                compose.append("    depends_on:\n");
                compose.append("      - database\n");
            }
            compose.append("\n");
        }
        String databaseService = databaseService(analysis);
        if (StringUtils.hasText(databaseService)) {
            compose.append(databaseService);
        }

        return compose.toString();
    }

    private List<String> environmentLines(RepositoryAnalysis analysis) {
        Set<String> environmentVariables = new LinkedHashSet<>();
        environmentVariables.add("SPRING_PROFILES_ACTIVE=docker");
        if (analysis.getEnvironmentVariables() != null) {
            analysis.getEnvironmentVariables().stream()
                    .sorted()
                    .forEach(name -> environmentVariables.add(name + "=${" + name + ":-}"));
        }
        if (analysis.getDatabaseTechnologies() != null && analysis.getDatabaseTechnologies().contains("postgresql")) {
            environmentVariables.add("SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/app");
            environmentVariables.add("SPRING_DATASOURCE_USERNAME=app");
            environmentVariables.add("SPRING_DATASOURCE_PASSWORD=app");
        }
        if (analysis.getDatabaseTechnologies() != null && analysis.getDatabaseTechnologies().contains("mysql")) {
            environmentVariables.add("SPRING_DATASOURCE_URL=jdbc:mysql://database:3306/app");
            environmentVariables.add("SPRING_DATASOURCE_USERNAME=app");
            environmentVariables.add("SPRING_DATASOURCE_PASSWORD=app");
        }
        if (analysis.getDatabaseTechnologies() != null && analysis.getDatabaseTechnologies().contains("mongodb")) {
            environmentVariables.add("SPRING_DATA_MONGODB_URI=mongodb://database:27017/app");
        }
        return new ArrayList<>(environmentVariables);
    }

    private String environmentTemplate(RepositoryAnalysis analysis, int appPort) {
        Set<String> lines = new LinkedHashSet<>();
        lines.add("# Copy this file to .env and adjust values for your environment.");
        lines.add("SPRING_PROFILES_ACTIVE=docker");
        lines.add("SERVER_PORT=" + appPort);

        if (analysis.getDatabaseTechnologies() != null && analysis.getDatabaseTechnologies().contains("postgresql")) {
            lines.add("");
            lines.add("# PostgreSQL");
            lines.add("SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/app");
            lines.add("SPRING_DATASOURCE_USERNAME=app");
            lines.add("SPRING_DATASOURCE_PASSWORD=app");
        }
        if (analysis.getDatabaseTechnologies() != null && analysis.getDatabaseTechnologies().contains("mysql")) {
            lines.add("");
            lines.add("# MySQL");
            lines.add("SPRING_DATASOURCE_URL=jdbc:mysql://database:3306/app");
            lines.add("SPRING_DATASOURCE_USERNAME=app");
            lines.add("SPRING_DATASOURCE_PASSWORD=app");
        }
        if (analysis.getDatabaseTechnologies() != null && analysis.getDatabaseTechnologies().contains("mongodb")) {
            lines.add("");
            lines.add("# MongoDB");
            lines.add("SPRING_DATA_MONGODB_URI=mongodb://database:27017/app");
        }

        List<String> detectedVariables = analysis.getEnvironmentVariables() == null
                ? List.of()
                : analysis.getEnvironmentVariables().stream()
                        .sorted()
                        .filter(variable -> lines.stream().noneMatch(line -> line.startsWith(variable + "=")))
                        .toList();
        if (!detectedVariables.isEmpty()) {
            lines.add("");
            lines.add("# Detected application variables");
            detectedVariables.forEach(variable -> lines.add(variable + "="));
        }

        return String.join("\n", lines) + "\n";
    }

    private String databaseService(RepositoryAnalysis analysis) {
        if (analysis.getDatabaseTechnologies() == null || analysis.getDatabaseTechnologies().isEmpty()) {
            return "";
        }
        List<String> databases = analysis.getDatabaseTechnologies().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (databases.contains("postgresql")) {
            return "  database:\n"
                    + "    image: postgres:16-alpine\n"
                    + "    environment:\n"
                    + "      POSTGRES_DB: app\n"
                    + "      POSTGRES_USER: app\n"
                    + "      POSTGRES_PASSWORD: app\n"
                    + "    ports:\n"
                    + "      - \"5432:5432\"\n"
                    + "    volumes:\n"
                    + "      - database-data:/var/lib/postgresql/data\n"
                    + "\n"
                    + "volumes:\n"
                    + "  database-data:\n";
        }
        if (databases.contains("mysql")) {
            return "  database:\n"
                    + "    image: mysql:8.4\n"
                    + "    environment:\n"
                    + "      MYSQL_DATABASE: app\n"
                    + "      MYSQL_USER: app\n"
                    + "      MYSQL_PASSWORD: app\n"
                    + "      MYSQL_ROOT_PASSWORD: root\n"
                    + "    ports:\n"
                    + "      - \"3306:3306\"\n"
                    + "    volumes:\n"
                    + "      - database-data:/var/lib/mysql\n"
                    + "\n"
                    + "volumes:\n"
                    + "  database-data:\n";
        }
        if (databases.contains("mongodb")) {
            return "  database:\n"
                    + "    image: mongo:7\n"
                    + "    ports:\n"
                    + "      - \"27017:27017\"\n"
                    + "    volumes:\n"
                    + "      - database-data:/data/db\n"
                    + "\n"
                    + "volumes:\n"
                    + "  database-data:\n";
        }
        return "";
    }

    private String deploymentReadme(List<DockerTarget> targets, RepositoryAnalysis analysis, int appPort) {
        DockerTarget defaultTarget = targets.get(0);
        String moduleDetails = targets.stream()
                .map(target -> "- `" + target.modulePath() + "` (" + target.targetType() + ") -> image `" + dockerImageName(target.artifactId()) + ":local` using `" + (".".equals(target.modulePath()) ? "Dockerfile" : target.modulePath() + "/Dockerfile") + "`")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- `.` -> image `ai-docker-app:local`");
        return """
                # Deployment

                This repository includes Docker deployment files generated by AI Docker Agent and reviewed with local deepseek-coder through Ollama.

                ## Generated Files

                - `Dockerfile` (or nested `Dockerfile`s) builds production images for the project's services.
                - `.dockerignore` keeps local, build, and VCS artifacts out of the image context.
                - `.env.example` documents runtime environment variables detected in the project.
                - `docker-compose.yml` runs the application locally and includes a database service when one was detected.

                ## Build

                ```bash
                docker build -t %s:local -f %s .
                ```

                ## Run With Docker

                ```bash
                docker run --rm -p %d:%d %s:local
                ```

                ## Run With Compose

                ```bash
                cp .env.example .env
                docker compose up --build
                ```

                ## Application Details

                Detected services:

                %s

                - Default module path: `%s`
                - Container port: `%d`
                - Detected databases: `%s`
                - Detected environment variables: `%s`

                Review environment variable defaults before using this configuration outside local development.
                """.formatted(
                dockerImageName(defaultTarget.artifactId()),
                ".".equals(defaultTarget.modulePath()) ? "Dockerfile" : defaultTarget.modulePath() + "/Dockerfile",
                appPort,
                appPort,
                dockerImageName(defaultTarget.artifactId()),
                moduleDetails,
                defaultTarget.modulePath(),
                appPort,
                analysis.getDatabaseTechnologies(),
                analysis.getEnvironmentVariables()
        );
    }

    private String dockerImageName(String artifactId) {
        String imageName = artifactId.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        return StringUtils.hasText(imageName) ? imageName : "ai-docker-app";
    }

    private String serviceName(DockerTarget target, int index) {
        if (index == 0) {
            return "app";
        }
        String serviceName = dockerImageName(target.artifactId()).replaceAll("[^a-z0-9_-]", "-");
        return StringUtils.hasText(serviceName) ? serviceName : "app-" + (index + 1);
    }

    private record DockerTarget(String modulePath, String artifactId, String targetType, String versionOrJavaVersion, boolean springBootProject) {
    }
}
