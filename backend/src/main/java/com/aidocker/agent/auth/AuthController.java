package com.aidocker.agent.auth;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    AuthenticatedUserResponse me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return AuthenticatedUserResponse.anonymous();
        }

        Map<String, Object> attributes = user.getAttributes();
        return new AuthenticatedUserResponse(
                true,
                stringValue(attributes.get("login")),
                stringValue(attributes.get("name")),
                stringValue(attributes.get("avatar_url"))
        );
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
