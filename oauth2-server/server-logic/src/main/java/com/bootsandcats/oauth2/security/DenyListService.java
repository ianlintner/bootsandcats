package com.bootsandcats.oauth2.security;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyMatchType;
import com.bootsandcats.oauth2.model.DenyRuleEntity;
import com.bootsandcats.oauth2.repository.DenyRuleRepository;

@Service
public class DenyListService {

    private final DenyRuleRepository denyRuleRepository;

    public DenyListService(DenyRuleRepository denyRuleRepository) {
        this.denyRuleRepository = denyRuleRepository;
    }

    public Optional<DenyRuleEntity> findMatchingRule(
            String provider, String email, String username, String providerId) {
        // Provider string used for provider-specific rules; treat null/blank as "local"
        String resolvedProvider = StringUtils.hasText(provider) ? provider : "local";

        Optional<DenyRuleEntity> emailMatch =
                findMatchForField(resolvedProvider, DenyMatchField.EMAIL, normalize(email), email);
        if (emailMatch.isPresent()) {
            return emailMatch;
        }

        Optional<DenyRuleEntity> usernameMatch =
                findMatchForField(
                        resolvedProvider,
                        DenyMatchField.USERNAME,
                        normalize(username),
                        username);
        if (usernameMatch.isPresent()) {
            return usernameMatch;
        }

        return findMatchForField(
                resolvedProvider,
                DenyMatchField.PROVIDER_ID,
                normalize(providerId),
                providerId);
    }

    public void assertNotDenied(String provider, String email, String username, String providerId) {
        Optional<DenyRuleEntity> match = findMatchingRule(provider, email, username, providerId);
        if (match.isPresent()) {
            DenyRuleEntity rule = match.get();
            String message =
                    rule.getReason() != null && !rule.getReason().isBlank()
                            ? rule.getReason()
                            : "Login denied by policy";
            throw new LoginDeniedException(message, rule);
        }
    }

    private Optional<DenyRuleEntity> findMatchForField(
            String provider,
            DenyMatchField field,
            String normalizedCandidate,
            String rawCandidate) {
        if (!StringUtils.hasText(rawCandidate)) {
            return Optional.empty();
        }

        List<DenyRuleEntity> activeRules = denyRuleRepository.findActiveRulesForProvider(provider, field);
        for (DenyRuleEntity rule : activeRules) {
            if (matches(rule, normalizedCandidate, rawCandidate)) {
                return Optional.of(rule);
            }
        }

        // If provider-specific query already includes global rules, no need to separately query globals.
        return Optional.empty();
    }

    private boolean matches(DenyRuleEntity rule, String normalizedCandidate, String rawCandidate) {
        if (rule.getMatchType() == DenyMatchType.EXACT) {
            String expected =
                    rule.getNormalizedValue() != null && !rule.getNormalizedValue().isBlank()
                            ? rule.getNormalizedValue()
                            : normalize(rule.getPattern());
            return expected != null && expected.equals(normalizedCandidate);
        }

        if (rule.getMatchType() == DenyMatchType.REGEX) {
            try {
                Pattern p = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);
                return p.matcher(rawCandidate).matches();
            } catch (PatternSyntaxException ex) {
                // Invalid patterns should never deny logins unexpectedly.
                return false;
            }
        }

        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
