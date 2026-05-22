package com.aidocker.agent.analysis;

import com.aidocker.agent.conversation.ConversationService;
import com.aidocker.agent.conversation.ConversationStatus;
import com.aidocker.agent.repository.RepositoryWorkspace;
import com.aidocker.agent.repository.RepositoryWorkspaceRepository;
import com.aidocker.agent.repository.RepositoryWorkspaceStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
public class RepositoryAnalysisService {

    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::[^}]*)?}");
    private static final Pattern SYSTEM_GETENV = Pattern.compile("System\\.getenv\\(\\s*\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*\\)");
    private static final Pattern SERVER_PORT_PROPERTY = Pattern.compile("(?m)^\\s*server\\.port\\s*[:=]\\s*([^\\s#]+)");
    private static final Pattern YAML_PORT_LINE = Pattern.compile("(?m)^\\s*port\\s*:\\s*([^\\s#]+)");

    private final RepositoryAnalysisRepository repositoryAnalysisRepository;
    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RepositoryAnalysisService(
            RepositoryAnalysisRepository repositoryAnalysisRepository,
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            ConversationService conversationService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repositoryAnalysisRepository = repositoryAnalysisRepository;
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public RepositoryAnalysisResponse analyze(RepositoryWorkspace workspace) {
        Path repositoryPath = Path.of(workspace.getLocalPath());
        Instant now = Instant.now(clock);

        workspace.markAnalyzing(now);
        repositoryWorkspaceRepository.save(workspace);
        conversationService.updateRepositoryState(
                workspace.getGithubUserId(),
                workspace.getConversationId(),
                ConversationStatus.ANALYZING,
                workspace.getId(),
                workspace.getBranch(),
                workspace.getLocalPath()
        );

        try {
            List<Path> files = listFiles(repositoryPath);
            
            // Maven detection
            List<Path> pomFiles = files.stream()
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .toList();
            List<PomMetadata> pomMetadata = pomFiles.stream()
                    .map(path -> parsePom(repositoryPath, path))
                    .toList();
            List<String> pomPaths = relativize(repositoryPath, pomFiles);

            // Gradle detection
            List<Path> gradleFiles = files.stream()
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.equals("build.gradle") || name.equals("build.gradle.kts");
                    })
                    .toList();
            List<GradleMetadata> gradleMetadata = gradleFiles.stream()
                    .map(path -> parseGradle(repositoryPath, path))
                    .toList();
            List<String> gradlePaths = relativize(repositoryPath, gradleFiles);

            // NPM detection
            List<Path> npmFiles = files.stream()
                    .filter(path -> path.getFileName().toString().equals("package.json"))
                    .toList();
            List<NpmMetadata> npmMetadata = npmFiles.stream()
                    .map(path -> parseNpm(repositoryPath, path))
                    .toList();
            List<String> npmPaths = relativize(repositoryPath, npmFiles);

            List<Path> configFiles = files.stream()
                    .filter(this::isApplicationConfig)
                    .toList();
            List<String> configPaths = relativize(repositoryPath, configFiles);
            List<Integer> ports = detectPorts(configFiles);
            List<String> databases = detectDatabaseTechnologies(pomFiles, gradleFiles, npmFiles);
            List<String> environmentVariables = detectEnvironmentVariables(files);
            
            List<String> executableModuleCandidates = new ArrayList<>();
            pomMetadata.stream()
                    .filter(PomMetadata::executableCandidate)
                    .map(PomMetadata::path)
                    .forEach(executableModuleCandidates::add);
            gradleMetadata.stream()
                    .filter(GradleMetadata::executableCandidate)
                    .map(GradleMetadata::path)
                    .forEach(executableModuleCandidates::add);
            npmMetadata.stream()
                    .filter(NpmMetadata::executableCandidate)
                    .map(NpmMetadata::path)
                    .forEach(executableModuleCandidates::add);

            boolean hasMaven = !pomFiles.isEmpty();
            boolean hasGradle = !gradleFiles.isEmpty();
            boolean hasNpm = !npmFiles.isEmpty();

            boolean springBootProject = pomMetadata.stream().anyMatch(PomMetadata::springBootProject)
                    || gradleMetadata.stream().anyMatch(GradleMetadata::springBootProject);
            Path artifactPath = writeAnalysisArtifact(repositoryPath);

            RepositoryAnalysis analysis = repositoryAnalysisRepository.save(new RepositoryAnalysis(
                    workspace.getId(),
                    workspace.getConversationId(),
                    workspace.getGithubUserId(),
                    workspace.getGithubLogin(),
                    workspace.getGitUrl(),
                    workspace.getLocalPath(),
                    hasMaven,
                    hasGradle,
                    hasNpm,
                    springBootProject,
                    pomMetadata,
                    gradleMetadata,
                    npmMetadata,
                    pomPaths,
                    gradlePaths,
                    npmPaths,
                    configPaths,
                    ports,
                    databases,
                    environmentVariables,
                    executableModuleCandidates,
                    List.of(),
                    artifactPath.toString(),
                    RepositoryAnalysisStatus.ANALYZED,
                    null,
                    now,
                    Instant.now(clock)
            ));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(artifactPath.toFile(), analysis);
            ignoreAnalysisArtifact(repositoryPath);
            workspace.markAnalyzed(analysis.getId(), artifactPath.toString(), Instant.now(clock));
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    workspace.getGithubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.ANALYZED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );

            return new RepositoryAnalysisResponse(
                    analysis.getId(),
                    artifactPath.toString(),
                    executableModuleCandidates,
                    List.of(),
                    "ANALYZED",
                    "Repository analysis completed.\nSpring Boot: " + springBootProject
                            + "\nPorts: " + ports
                            + "\nDatabases: " + databases
                            + "\nEnvironment variables: " + environmentVariables
                            + "\nExecutable module candidates: " + executableModuleCandidates
            );
        } catch (Exception exception) {
            workspace.markAnalysisFailed(exception.getMessage(), Instant.now(clock));
            repositoryWorkspaceRepository.save(workspace);
            conversationService.updateRepositoryState(
                    workspace.getGithubUserId(),
                    workspace.getConversationId(),
                    ConversationStatus.ANALYSIS_FAILED,
                    workspace.getId(),
                    workspace.getBranch(),
                    workspace.getLocalPath()
            );
            throw new RepositoryAnalysisException("Unable to analyze cloned repository.", exception);
        }
    }

    public RepositoryAnalysisResponse selectExecutableModules(
            String githubUserId,
            String repositoryAnalysisId,
            List<String> selectedModules
    ) {
        RepositoryAnalysis analysis = repositoryAnalysisRepository
                .findByIdAndGithubUserId(repositoryAnalysisId, githubUserId)
                .orElseThrow(() -> new RepositoryAnalysisException("Repository analysis was not found for the logged-in user.", null));
        List<String> validSelections = selectedModules.stream()
                .filter(analysis.getExecutableModuleCandidates()::contains)
                .distinct()
                .toList();
        if (validSelections.isEmpty()) {
            throw new RepositoryAnalysisException("Select at least one executable module from the detected candidates.", null);
        }

        analysis.setSelectedExecutableModules(validSelections);
        RepositoryAnalysis saved = repositoryAnalysisRepository.save(analysis);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    Path.of(saved.getAnalysisArtifactPath()).toFile(),
                    saved
            );
        } catch (IOException exception) {
            throw new RepositoryAnalysisException("Unable to update repository analysis artifact.", exception);
        }

        return new RepositoryAnalysisResponse(
                saved.getId(),
                saved.getAnalysisArtifactPath(),
                saved.getExecutableModuleCandidates(),
                saved.getSelectedExecutableModules(),
                saved.getStatus().name(),
                "Executable modules confirmed.\nModules: " + saved.getSelectedExecutableModules()
                        + "\nNext: I can use these modules for Dockerfile, CI/CD, and Kubernetes generation."
        );
    }

    private List<Path> listFiles(Path repositoryPath) throws IOException {
        try (var stream = Files.walk(repositoryPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/.git/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .filter(path -> !path.toString().contains("/node_modules/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private PomMetadata parsePom(Path repositoryPath, Path pomPath) {
        try {
            Element project = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pomPath.toFile())
                    .getDocumentElement();
            String groupId = firstDirectText(project, "groupId")
                    .or(() -> firstNestedText(project, "parent", "groupId"))
                    .orElse(null);
            String artifactId = firstDirectText(project, "artifactId").orElse(null);
            String version = firstDirectText(project, "version")
                    .or(() -> firstNestedText(project, "parent", "version"))
                    .orElse(null);
            String packaging = firstDirectText(project, "packaging").orElse("jar");
            String javaVersion = firstProperty(project, "java.version")
                    .or(() -> firstProperty(project, "maven.compiler.source"))
                    .orElse(null);
            String xml = Files.readString(pomPath);
            boolean springBootProject = xml.contains("spring-boot-starter")
                    || xml.contains("spring-boot-maven-plugin")
                    || xml.contains("spring-boot-starter-parent");
            boolean executableCandidate = ("jar".equals(packaging) || "war".equals(packaging))
                    && springBootProject
                    && !isAggregatorPom(project);

            return new PomMetadata(
                    repositoryPath.relativize(pomPath).toString(),
                    groupId,
                    artifactId,
                    version,
                    packaging,
                    javaVersion,
                    springBootProject,
                    executableCandidate
            );
        } catch (Exception exception) {
            throw new RepositoryAnalysisException("Unable to parse pom.xml at " + pomPath, exception);
        }
    }

    private GradleMetadata parseGradle(Path repositoryPath, Path gradlePath) {
        try {
            String content = Files.readString(gradlePath);
            String group = null;
            String version = null;
            String javaVersion = null;

            Pattern groupPattern = Pattern.compile("(?m)^\\s*group\\s*=\\s*['\"]([^'\"]+)['\"]");
            Matcher groupMatcher = groupPattern.matcher(content);
            if (groupMatcher.find()) {
                group = groupMatcher.group(1);
            }

            Pattern versionPattern = Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]");
            Matcher versionMatcher = versionPattern.matcher(content);
            if (versionMatcher.find()) {
                version = versionMatcher.group(1);
            }

            Pattern javaVerPattern = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?([\\d\\.]+)['\"]?");
            Matcher javaVerMatcher = javaVerPattern.matcher(content);
            if (javaVerMatcher.find()) {
                javaVersion = javaVerMatcher.group(1);
            } else {
                Pattern toolchainPattern = Pattern.compile("languageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)");
                Matcher toolchainMatcher = toolchainPattern.matcher(content);
                if (toolchainMatcher.find()) {
                    javaVersion = toolchainMatcher.group(1);
                }
            }

            boolean springBootProject = content.contains("org.springframework.boot")
                    || content.contains("spring-boot");
            
            boolean hasApplicationPlugin = content.contains("id 'application'")
                    || content.contains("id(\"application\")")
                    || content.contains("apply plugin: 'application'")
                    || content.contains("apply plugin: \"application\"");
            
            boolean executableCandidate = springBootProject || hasApplicationPlugin;
            String artifactId = gradlePath.getParent().getFileName().toString();

            return new GradleMetadata(
                    repositoryPath.relativize(gradlePath).toString(),
                    group,
                    artifactId,
                    version,
                    javaVersion,
                    springBootProject,
                    executableCandidate
            );
        } catch (Exception exception) {
            throw new RepositoryAnalysisException("Unable to parse gradle build file at " + gradlePath, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private NpmMetadata parseNpm(Path repositoryPath, Path npmPath) {
        try {
            String content = Files.readString(npmPath);
            Map<String, Object> pkg = objectMapper.readValue(content, Map.class);
            String name = (String) pkg.get("name");
            if (name == null) {
                name = npmPath.getParent().getFileName().toString();
            }
            String version = (String) pkg.get("version");
            String mainScript = (String) pkg.get("main");
            
            List<String> scripts = new ArrayList<>();
            if (pkg.get("scripts") instanceof Map<?, ?> scriptsMap) {
                for (Object key : scriptsMap.keySet()) {
                    scripts.add(String.valueOf(key));
                }
            }
            
            boolean executableCandidate = true;

            return new NpmMetadata(
                    repositoryPath.relativize(npmPath).toString(),
                    name,
                    version,
                    mainScript,
                    scripts,
                    executableCandidate
            );
        } catch (Exception exception) {
            throw new RepositoryAnalysisException("Unable to parse package.json at " + npmPath, exception);
        }
    }

    private Optional<String> firstDirectText(Element element, String tagName) {
        NodeList nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element child && child.getTagName().equals(tagName)) {
                return Optional.ofNullable(child.getTextContent()).map(String::trim).filter(value -> !value.isBlank());
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstNestedText(Element element, String parentTagName, String childTagName) {
        NodeList parents = element.getElementsByTagName(parentTagName);
        if (parents.getLength() == 0 || !(parents.item(0) instanceof Element parent)) {
            return Optional.empty();
        }
        return firstDirectText(parent, childTagName);
    }

    private Optional<String> firstProperty(Element project, String propertyName) {
        NodeList properties = project.getElementsByTagName("properties");
        if (properties.getLength() == 0 || !(properties.item(0) instanceof Element propertyElement)) {
            return Optional.empty();
        }
        return firstDirectText(propertyElement, propertyName);
    }

    private boolean isAggregatorPom(Element project) {
        return project.getElementsByTagName("modules").getLength() > 0;
    }

    private boolean isApplicationConfig(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.equals("application.properties")
                || fileName.equals("application.yml")
                || fileName.equals("application.yaml")
                || fileName.startsWith("application-") && (fileName.endsWith(".properties") || fileName.endsWith(".yml") || fileName.endsWith(".yaml"));
    }

    private List<Integer> detectPorts(List<Path> configFiles) {
        Set<Integer> ports = new LinkedHashSet<>();
        for (Path configFile : configFiles) {
            try {
                String text = Files.readString(configFile);
                collectPorts(text, SERVER_PORT_PROPERTY, ports);
                collectPorts(text, YAML_PORT_LINE, ports);
            } catch (IOException ignored) {
                // Best-effort analysis should continue across unreadable files.
            }
        }
        return new ArrayList<>(ports);
    }

    private void collectPorts(String text, Pattern pattern, Set<Integer> ports) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            String defaultValue = value.replaceAll("^\\$\\{[^:}]+:([^}]+)}$", "$1");
            try {
                ports.add(Integer.parseInt(defaultValue));
            } catch (NumberFormatException ignored) {
                // Non-literal ports are captured as environment variables separately.
            }
        }
    }

    private List<String> detectDatabaseTechnologies(List<Path> pomFiles, List<Path> gradleFiles, List<Path> npmFiles) {
        Set<String> databases = new LinkedHashSet<>();
        List<Path> files = new ArrayList<>();
        files.addAll(pomFiles);
        files.addAll(gradleFiles);
        files.addAll(npmFiles);

        for (Path file : files) {
            try {
                String content = Files.readString(file).toLowerCase();
                Map<String, String> matches = Map.of(
                        "mongodb", "mongodb",
                        "mongoose", "mongodb",
                        "postgresql", "postgresql",
                        "pg", "postgresql",
                        "mysql", "mysql",
                        "mysql2", "mysql",
                        "mariadb", "mariadb",
                        "h2", "h2",
                        "redis", "redis"
                );
                matches.forEach((needle, database) -> {
                    if (content.contains(needle)) {
                        databases.add(database);
                    }
                });
            } catch (IOException ignored) {
                // Best-effort analysis should continue across unreadable files.
            }
        }
        return new ArrayList<>(databases);
    }

    private List<String> detectEnvironmentVariables(List<Path> files) {
        Set<String> envVars = new LinkedHashSet<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            if (!(isApplicationConfig(file) || fileName.endsWith(".java") || fileName.equals("Dockerfile") || fileName.endsWith(".sh"))) {
                continue;
            }
            try {
                String text = Files.readString(file);
                collectMatches(text, ENV_PLACEHOLDER, envVars);
                collectMatches(text, SYSTEM_GETENV, envVars);
            } catch (IOException ignored) {
                // Best-effort analysis should continue across unreadable files.
            }
        }
        return new ArrayList<>(envVars);
    }

    private void collectMatches(String text, Pattern pattern, Set<String> values) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
    }

    private List<String> relativize(Path repositoryPath, List<Path> paths) {
        return paths.stream()
                .map(path -> repositoryPath.relativize(path).toString())
                .toList();
    }

    private Path writeAnalysisArtifact(Path repositoryPath) throws IOException {
        Path outputDirectory = repositoryPath.resolve(".ai-docker");
        Files.createDirectories(outputDirectory);
        return outputDirectory.resolve("repository-analysis.json");
    }

    private void ignoreAnalysisArtifact(Path repositoryPath) throws IOException {
        Path excludeFile = repositoryPath.resolve(".git").resolve("info").resolve("exclude");
        Files.createDirectories(excludeFile.getParent());
        String ignoreEntry = "\n# AI Docker Agent local analysis artifacts\n.ai-docker/\n";
        String existing = Files.exists(excludeFile) ? Files.readString(excludeFile) : "";
        if (!existing.contains(".ai-docker/")) {
            Files.writeString(excludeFile, existing + ignoreEntry);
        }
    }
}
