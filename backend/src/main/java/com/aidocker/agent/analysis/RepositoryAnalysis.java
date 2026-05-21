package com.aidocker.agent.analysis;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("repository_analysis")
public class RepositoryAnalysis {

    @Id
    private String id;

    @Indexed
    private String repositoryWorkspaceId;

    @Indexed
    private String conversationId;

    @Indexed
    private String githubUserId;

    private String githubLogin;
    private String gitUrl;
    private String localPath;
    private boolean mavenProject;
    private boolean springBootProject;
    private List<PomMetadata> pomMetadata;
    private List<String> pomPaths;
    private List<String> applicationConfigPaths;
    private List<Integer> applicationPorts;
    private List<String> databaseTechnologies;
    private List<String> environmentVariables;
    private List<String> executableModuleCandidates;
    private List<String> selectedExecutableModules;
    private String analysisArtifactPath;
    private RepositoryAnalysisStatus status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public RepositoryAnalysis() {
    }

    public RepositoryAnalysis(
            String repositoryWorkspaceId,
            String conversationId,
            String githubUserId,
            String githubLogin,
            String gitUrl,
            String localPath,
            boolean mavenProject,
            boolean springBootProject,
            List<PomMetadata> pomMetadata,
            List<String> pomPaths,
            List<String> applicationConfigPaths,
            List<Integer> applicationPorts,
            List<String> databaseTechnologies,
            List<String> environmentVariables,
            List<String> executableModuleCandidates,
            List<String> selectedExecutableModules,
            String analysisArtifactPath,
            RepositoryAnalysisStatus status,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.repositoryWorkspaceId = repositoryWorkspaceId;
        this.conversationId = conversationId;
        this.githubUserId = githubUserId;
        this.githubLogin = githubLogin;
        this.gitUrl = gitUrl;
        this.localPath = localPath;
        this.mavenProject = mavenProject;
        this.springBootProject = springBootProject;
        this.pomMetadata = pomMetadata;
        this.pomPaths = pomPaths;
        this.applicationConfigPaths = applicationConfigPaths;
        this.applicationPorts = applicationPorts;
        this.databaseTechnologies = databaseTechnologies;
        this.environmentVariables = environmentVariables;
        this.executableModuleCandidates = executableModuleCandidates;
        this.selectedExecutableModules = selectedExecutableModules;
        this.analysisArtifactPath = analysisArtifactPath;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getRepositoryWorkspaceId() {
        return repositoryWorkspaceId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public String getGithubLogin() {
        return githubLogin;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public String getLocalPath() {
        return localPath;
    }

    public boolean isMavenProject() {
        return mavenProject;
    }

    public boolean isSpringBootProject() {
        return springBootProject;
    }

    public List<PomMetadata> getPomMetadata() {
        return pomMetadata;
    }

    public List<String> getPomPaths() {
        return pomPaths;
    }

    public List<String> getApplicationConfigPaths() {
        return applicationConfigPaths;
    }

    public List<Integer> getApplicationPorts() {
        return applicationPorts;
    }

    public List<String> getDatabaseTechnologies() {
        return databaseTechnologies;
    }

    public List<String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public List<String> getExecutableModuleCandidates() {
        return executableModuleCandidates;
    }

    public List<String> getSelectedExecutableModules() {
        return selectedExecutableModules;
    }

    public void setSelectedExecutableModules(List<String> selectedExecutableModules) {
        this.selectedExecutableModules = selectedExecutableModules;
        this.updatedAt = Instant.now();
    }

    public String getAnalysisArtifactPath() {
        return analysisArtifactPath;
    }

    public RepositoryAnalysisStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
