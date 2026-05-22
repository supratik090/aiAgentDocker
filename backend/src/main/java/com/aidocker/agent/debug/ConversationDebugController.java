package com.aidocker.agent.debug;

import com.aidocker.agent.analysis.RepositoryAnalysisRepository;
import com.aidocker.agent.auth.GitHubUser;
import com.aidocker.agent.conversation.Conversation;
import com.aidocker.agent.conversation.ConversationService;
import com.aidocker.agent.deployment.DockerAiReviewRepository;
import com.aidocker.agent.repository.RepositoryWorkspaceRepository;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
public class ConversationDebugController {

    private final ConversationService conversationService;
    private final RepositoryWorkspaceRepository repositoryWorkspaceRepository;
    private final RepositoryAnalysisRepository repositoryAnalysisRepository;
    private final DockerAiReviewRepository dockerAiReviewRepository;

    public ConversationDebugController(
            ConversationService conversationService,
            RepositoryWorkspaceRepository repositoryWorkspaceRepository,
            RepositoryAnalysisRepository repositoryAnalysisRepository,
            DockerAiReviewRepository dockerAiReviewRepository
    ) {
        this.conversationService = conversationService;
        this.repositoryWorkspaceRepository = repositoryWorkspaceRepository;
        this.repositoryAnalysisRepository = repositoryAnalysisRepository;
        this.dockerAiReviewRepository = dockerAiReviewRepository;
    }

    @GetMapping("/conversations/{conversationId}")
    Map<String, Object> debugConversation(
            @AuthenticationPrincipal OAuth2User user,
            @PathVariable String conversationId
    ) {
        GitHubUser gitHubUser = GitHubUser.from(user);
        Conversation conversation = conversationService.findForUser(conversationId, gitHubUser.githubUserId());
        return Map.of(
                "conversation", conversation,
                "repositoryWorkspaces", repositoryWorkspaceRepository.findByConversationIdAndGithubUserIdOrderByUpdatedAtDesc(conversationId, gitHubUser.githubUserId()),
                "repositoryAnalysis", repositoryAnalysisRepository.findByConversationIdAndGithubUserIdOrderByUpdatedAtDesc(conversationId, gitHubUser.githubUserId()),
                "dockerAiReviews", dockerAiReviewRepository.findByConversationIdAndGithubUserIdOrderByCreatedAtDesc(conversationId, gitHubUser.githubUserId())
        );
    }
}
