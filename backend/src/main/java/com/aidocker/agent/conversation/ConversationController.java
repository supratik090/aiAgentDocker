package com.aidocker.agent.conversation;

import com.aidocker.agent.auth.GitHubUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    ConversationResponse create(
            @AuthenticationPrincipal OAuth2User user,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        return conversationService.create(GitHubUser.from(user), request);
    }

    @GetMapping("/conversations")
    List<ConversationResponse> list(@AuthenticationPrincipal OAuth2User user) {
        return conversationService.findForUser(GitHubUser.from(user));
    }
}
