package com.aidocker.agent.deployment;

import com.aidocker.agent.analysis.RepositoryAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class LocalDockerAiReviewService {

    private static final int MAX_CONTEXT_FILES = 16;
    private static final int MAX_CONTEXT_CHARS_PER_FILE = 6000;
    private static final int MAX_SNIPPET_CHARS_PER_FILE = 2500;
    private static final Pattern ENV_USAGE = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*|System\\.getenv\\(");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DockerAiReviewRepository dockerAiReviewRepository;
    private final String model;
    private final boolean enabled;
    private final Clock clock;

    public LocalDockerAiReviewService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DockerAiReviewRepository dockerAiReviewRepository,
            @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ollama.model:deepseek-coder}") String model,
            @Value("${app.ollama.enabled:true}") boolean enabled,
            Clock clock
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.dockerAiReviewRepository = dockerAiReviewRepository;
        this.model = model;
        this.enabled = enabled;
        this.clock = clock;
    }

    public DockerAiReviewResult reviewAndApply(
            String repositoryWorkspaceId,
            String conversationId,
            String githubUserId,
            Path repositoryPath,
            List<String> relativeFiles,
            RepositoryAnalysis analysis
    ) {
        if (!enabled) {
            return new DockerAiReviewResult(List.of(), "Local AI review is disabled.");
        }

        Map<String, String> fileContents = readFiles(repositoryPath, relativeFiles);
        String promptText = prompt(repositoryPath, fileContents, analysis);
        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .body(Map.of(
                        "model", model,
                        "stream", false,
                        "format", "json",
                        "prompt", promptText
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("response") == null) {
            throw new DeploymentPermissionException("Ollama did not return a Docker review response.");
        }

        String rawResponse = response.get("response").toString();
        DockerAiReviewResult result;
        try {
            result = applyReview(repositoryPath, relativeFiles, rawResponse);
        } catch (Exception exception) {
            System.err.println("Failed to parse or apply local AI review: " + exception.getMessage());
            exception.printStackTrace();
            result = new DockerAiReviewResult(
                    List.of(),
                    "Local AI review failed to parse or apply response: " + exception.getMessage()
            );
        }

        try {
            DockerAiReview aiReview = new DockerAiReview(
                    repositoryWorkspaceId,
                    conversationId,
                    githubUserId,
                    promptText,
                    rawResponse,
                    result.summary(),
                    result.updatedFiles(),
                    Instant.now(clock)
            );
            dockerAiReviewRepository.save(aiReview);
        } catch (Exception exception) {
            System.err.println("Failed to save Docker AI review to MongoDB: " + exception.getMessage());
            exception.printStackTrace();
        }

        return result;
    }

    private Map<String, String> readFiles(Path repositoryPath, List<String> relativeFiles) {
        Map<String, String> fileContents = new LinkedHashMap<>();
        for (String relativeFile : relativeFiles) {
            try {
                fileContents.put(relativeFile, Files.readString(repositoryPath.resolve(relativeFile)));
            } catch (IOException exception) {
                throw new DeploymentPermissionException("Unable to read generated file for local AI review: " + relativeFile, exception);
            }
        }
        return fileContents;
    }

    private String prompt(Path repositoryPath, Map<String, String> fileContents, RepositoryAnalysis analysis) {
        StringBuilder filesJsonSchema = new StringBuilder();
        for (String relativeFile : fileContents.keySet()) {
            if (filesJsonSchema.length() > 0) {
                filesJsonSchema.append(",\n");
            }
            filesJsonSchema.append("    \"").append(relativeFile).append("\": \"complete replacement content\"");
        }

        StringBuilder prompt = new StringBuilder("""
                You are reviewing generated Docker deployment files for a Java (Maven/Gradle) or Node.js (NPM) project.
                Improve production readiness while keeping the files simple and runnable locally.
                Do not invent secrets. Keep .env.example safe for commit.
                Use the project context to verify module paths, ports, profiles, environment variables, and startup behavior.
                Return only JSON in this exact shape:
                {
                  "summary": "short summary",
                  "files": {
                """);
        prompt.append(filesJsonSchema).append("\n");
        prompt.append("""
                  }
                }

                Repository analysis:
                """);
        prompt.append("- Spring Boot: ").append(analysis.isSpringBootProject()).append("\n");
        prompt.append("- Ports: ").append(analysis.getApplicationPorts()).append("\n");
        prompt.append("- Databases: ").append(analysis.getDatabaseTechnologies()).append("\n");
        prompt.append("- Environment variables: ").append(analysis.getEnvironmentVariables()).append("\n");
        prompt.append("- Selected modules: ").append(analysis.getSelectedExecutableModules()).append("\n\n");
        prompt.append("Targeted project context:\n");
        projectContext(repositoryPath, analysis).forEach((fileName, content) -> prompt
                .append("\n--- ")
                .append(fileName)
                .append(" ---\n")
                .append(content)
                .append("\n"));
        prompt.append("\n");
        prompt.append("Generated files:\n");
        fileContents.forEach((fileName, content) -> prompt
                .append("\n--- ")
                .append(fileName)
                .append(" ---\n")
                .append(content)
                .append("\n"));
        return prompt.toString();
    }

    private Map<String, String> projectContext(Path repositoryPath, RepositoryAnalysis analysis) {
        Set<String> candidateFiles = new LinkedHashSet<>();
        candidateFiles.add(analysis.getAnalysisArtifactPath());
        candidateFiles.add("pom.xml");
        candidateFiles.add("build.gradle");
        candidateFiles.add("build.gradle.kts");
        candidateFiles.add("package.json");
        addAll(candidateFiles, analysis.getPomPaths());
        addAll(candidateFiles, analysis.getGradlePaths());
        addAll(candidateFiles, analysis.getNpmPaths());
        addAll(candidateFiles, analysis.getSelectedExecutableModules());
        addAll(candidateFiles, analysis.getApplicationConfigPaths());
        candidateFiles.add("Dockerfile");
        candidateFiles.add("docker-compose.yml");
        candidateFiles.add(".dockerignore");
        candidateFiles.add("README.md");

        Map<String, String> context = new LinkedHashMap<>();
        for (String relativeFile : candidateFiles) {
            if (context.size() >= MAX_CONTEXT_FILES) {
                break;
            }
            addFileContext(repositoryPath, relativeFile, context, MAX_CONTEXT_CHARS_PER_FILE);
        }
        for (String relativeFile : discoverSnippetFiles(repositoryPath)) {
            if (context.size() >= MAX_CONTEXT_FILES) {
                break;
            }
            addFileContext(repositoryPath, relativeFile, context, MAX_SNIPPET_CHARS_PER_FILE);
        }
        return context;
    }

    private void addAll(Set<String> target, List<String> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    private void addFileContext(Path repositoryPath, String relativeFile, Map<String, String> context, int maxChars) {
        if (!StringUtils.hasText(relativeFile) || context.containsKey(relativeFile)) {
            return;
        }
        Path path = Path.of(relativeFile);
        if (!path.isAbsolute()) {
            path = repositoryPath.resolve(relativeFile).normalize();
        }
        if (!path.startsWith(repositoryPath) || !Files.isRegularFile(path)) {
            return;
        }
        String contextName = repositoryPath.relativize(path).toString();
        if (context.containsKey(contextName)) {
            return;
        }
        try {
            context.put(contextName, truncate(Files.readString(path), maxChars));
        } catch (IOException ignored) {
            // Context gathering is best-effort; Docker review can proceed without this file.
        }
    }

    private List<String> discoverSnippetFiles(Path repositoryPath) {
        List<String> files = new ArrayList<>();
        try (var stream = Files.walk(repositoryPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/.git/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .filter(path -> !path.toString().contains("/node_modules/"))
                    .filter(this::isUsefulSnippetFile)
                    .limit(MAX_CONTEXT_FILES)
                    .forEach(path -> files.add(repositoryPath.relativize(path).toString()));
        } catch (IOException ignored) {
            // Context gathering is best-effort.
        }
        return files;
    }

    private boolean isUsefulSnippetFile(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".sh") || fileName.equals("Procfile") || fileName.endsWith(".js") || fileName.endsWith(".ts")) {
            return true;
        }
        if (!(fileName.endsWith(".java") || fileName.endsWith(".properties") || fileName.endsWith(".yml") || fileName.endsWith(".yaml"))) {
            return false;
        }
        try {
            return ENV_USAGE.matcher(Files.readString(path)).find();
        } catch (IOException ignored) {
            return false;
        }
    }

    private String truncate(String content, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n# ... truncated for local AI review ...\n";
    }

    private DockerAiReviewResult applyReview(Path repositoryPath, List<String> relativeFiles, String reviewJson) {
        try {
            JsonNode root = objectMapper.readTree(reviewJson);
            JsonNode files = root.path("files");
            if (!files.isObject()) {
                throw new DeploymentPermissionException("Local AI review did not include file replacements.");
            }

            List<String> updatedFiles = relativeFiles.stream()
                    .filter(relativeFile -> {
                        JsonNode replacement = files.get(relativeFile);
                        return replacement != null
                                && replacement.isTextual()
                                && StringUtils.hasText(replacement.asText());
                    })
                    .toList();

            for (String updatedFile : updatedFiles) {
                Files.writeString(repositoryPath.resolve(updatedFile), files.get(updatedFile).asText());
            }

            String summary = root.path("summary").asText("Local AI review applied.");
            return new DockerAiReviewResult(updatedFiles, summary);
        } catch (IOException exception) {
            throw new DeploymentPermissionException("Unable to apply local AI Docker review.", exception);
        }
    }
}
