package com.bootsandcats.oauth2.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyMatchType;
import com.bootsandcats.oauth2.model.DenyRuleEntity;

@ExtendWith(MockitoExtension.class)
class DenyListServiceTest {

        @Mock private DenyRuleStore denyRuleStore;

    @Test
    void findsEmailExactMatch_caseInsensitive_andUsesNormalizedValueWhenPresent() {
        DenyRuleEntity rule = new DenyRuleEntity();
        rule.setEnabled(true);
        rule.setProvider(null);
        rule.setMatchField(DenyMatchField.EMAIL);
        rule.setMatchType(DenyMatchType.EXACT);
        rule.setPattern("Alice@Example.com");
        rule.setNormalizedValue("alice@example.com");
        rule.setReason("blocked");

        when(denyRuleStore.findActiveRulesForProvider("github", DenyMatchField.EMAIL))
                .thenReturn(List.of(rule));

        DenyListService service = new DenyListService(denyRuleStore);

        assertThat(service.findMatchingRule("github", "ALICE@EXAMPLE.COM", "alice", "123"))
                .contains(rule);

        assertThatThrownBy(
                        () ->
                                service.assertNotDenied(
                                        "github", "ALICE@EXAMPLE.COM", "alice", "123"))
                .isInstanceOf(LoginDeniedException.class)
                .hasMessage("blocked");
    }

    @Test
    void findsUsernameRegexMatch_andMatchesFullString() {
        DenyRuleEntity rule = new DenyRuleEntity();
        rule.setEnabled(true);
        rule.setProvider("google");
        rule.setMatchField(DenyMatchField.USERNAME);
        rule.setMatchType(DenyMatchType.REGEX);
        rule.setPattern("^bad-.*$");
        rule.setReason("nope");

        when(denyRuleStore.findActiveRulesForProvider("google", DenyMatchField.EMAIL))
                .thenReturn(List.of());
        when(denyRuleStore.findActiveRulesForProvider("google", DenyMatchField.USERNAME))
                .thenReturn(List.of(rule));

        DenyListService service = new DenyListService(denyRuleStore);

        assertThat(service.findMatchingRule("google", "ok@example.com", "bad-user", "abc"))
                .contains(rule);
        assertThat(service.findMatchingRule("google", "ok@example.com", "not-bad-user", "abc"))
                .isEmpty();
    }

    @Test
    void invalidRegexNeverDenies() {
        DenyRuleEntity rule = new DenyRuleEntity();
        rule.setEnabled(true);
        rule.setProvider(null);
        rule.setMatchField(DenyMatchField.EMAIL);
        rule.setMatchType(DenyMatchType.REGEX);
        rule.setPattern("(");

        when(denyRuleStore.findActiveRulesForProvider("github", DenyMatchField.EMAIL))
                .thenReturn(List.of(rule));
        when(denyRuleStore.findActiveRulesForProvider("github", DenyMatchField.USERNAME))
                .thenReturn(List.of());
        when(denyRuleStore.findActiveRulesForProvider("github", DenyMatchField.PROVIDER_ID))
                .thenReturn(List.of());

        DenyListService service = new DenyListService(denyRuleStore);

        assertThat(service.findMatchingRule("github", "x@example.com", "user", "id")).isEmpty();
    }

    @Test
    void nullProviderResolvesToLocal() {
        when(denyRuleStore.findActiveRulesForProvider("local", DenyMatchField.EMAIL))
                .thenReturn(List.of());
        when(denyRuleStore.findActiveRulesForProvider("local", DenyMatchField.USERNAME))
                .thenReturn(List.of());
        when(denyRuleStore.findActiveRulesForProvider("local", DenyMatchField.PROVIDER_ID))
                .thenReturn(List.of());

        DenyListService service = new DenyListService(denyRuleStore);
        service.findMatchingRule(null, "x@example.com", "x", "y");

        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        verify(denyRuleStore)
                .findActiveRulesForProvider(
                        providerCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(DenyMatchField.EMAIL));
        assertThat(providerCaptor.getValue()).isEqualTo("local");
    }
}
