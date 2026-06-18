package com.example.vault.security;

import com.example.vault.config.GoogleProperties;
import com.example.vault.exception.ApiException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GoogleIdTokenVerifierService {

    private final GoogleProperties googleProperties;

    public GoogleUserInfo verify(String idToken) {
        if (!StringUtils.hasText(googleProperties.getClientId())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_AUTH_DISABLED",
                    "Google sign-in is not configured");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleProperties.getClientId()))
                .build();

        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                        "Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = token.getPayload();
            String sub = payload.getSubject();
            String email = payload.getEmail();

            if (!StringUtils.hasText(sub) || !StringUtils.hasText(email)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                        "Google token is missing required claims");
            }

            if (Boolean.FALSE.equals(payload.getEmailVerified())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED",
                        "Google account email is not verified");
            }

            return new GoogleUserInfo(sub, email.toLowerCase(), payload.get("name") instanceof String name ? name : null);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                    "Failed to verify Google ID token");
        }
    }

    public record GoogleUserInfo(String sub, String email, String name) {
    }
}
