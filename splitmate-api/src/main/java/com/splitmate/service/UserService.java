package com.splitmate.service;

import com.splitmate.entity.User;
import com.splitmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Resolves a Supabase JWT to a local {@link User}, auto-creating the record
 * on first login (email/password or Google SSO).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        String supabaseUserId = jwt.getSubject();
        return userRepository.findById(supabaseUserId)
                .orElseGet(() -> {
                    String email = resolveEmail(jwt);
                    String name  = resolveName(jwt, email);
                    return userRepository.save(User.builder()
                            .supabaseUserId(supabaseUserId)
                            .email(email)
                            .name(name)
                            .build());
                });
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return (email != null && !email.isBlank()) ? email : jwt.getSubject() + "@unknown.local";
    }

    /**
     * Supabase email/password:   name claim or email prefix.
     * Google SSO:                user_metadata.full_name is populated by the provider.
     */
    private String resolveName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (isPresent(name)) return name;

        // user_metadata is a nested map injected by Supabase for social logins
        Map<String, Object> userMeta = jwt.getClaim("user_metadata");
        if (userMeta != null) {
            Object fullName = userMeta.get("full_name");
            if (fullName != null && isPresent(fullName.toString())) return fullName.toString();
            Object metaName = userMeta.get("name");
            if (metaName != null && isPresent(metaName.toString())) return metaName.toString();
        }

        // Fall back to email prefix
        return email.contains("@") ? email.split("@")[0] : email;
    }

    private boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }
}
