package com.aidocker.agent.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateConversationRequest(
        @NotBlank
        @Pattern(regexp = "^(https://|git@).+", message = "Git URL must start with https:// or git@")
        String gitUrl
) {
}
