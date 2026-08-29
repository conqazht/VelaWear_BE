package vn.conganh.commercial.feature.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.feature.socialaccount.SocialAccount;
import vn.conganh.commercial.feature.socialaccount.SocialAccountRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserHasRoleRepository;
import vn.conganh.commercial.feature.user.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Auth OAuth2 - OAuth2GoogleAccountService")
class OAuth2GoogleAccountServiceTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserHasRoleRepository userHasRoleRepository;

    @Captor
    private ArgumentCaptor<SocialAccount> socialAccountCaptor;

    @Test
    @DisplayName("resolveOrCreateUser - existing Google social account logs in same user")
    void resolveOrCreateUser_existingSocialAccount_returnsUser() {
        OAuth2GoogleAccountService service = service();
        User user = user(1L, "buyer@example.com");
        SocialAccount socialAccount = socialAccount(user, "google-sub-1");

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-1"))
                .thenReturn(Optional.of(socialAccount));

        User result = service.resolveOrCreateUser(googleProfile("google-sub-1", "Buyer@Example.com", true));

        assertThat(result).isSameAs(user);
        verify(userRepository, never()).saveAndFlush(any());
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("resolveOrCreateUser - existing Google social account syncs updated email and profile")
    void resolveOrCreateUser_existingSocialAccount_syncsUpdatedEmailAndProfile() {
        OAuth2GoogleAccountService service = service();
        User user = user(1L, "old-email@example.com");
        user.setFullName("");
        user.setAvatar("https://example.com/old-avatar.png");
        SocialAccount socialAccount = socialAccount(user, "google-sub-1");

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-1"))
                .thenReturn(Optional.of(socialAccount));
        when(userRepository.findByEmailAndDeletedAtIsNull("new-email@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new-email@example.com")).thenReturn(false);

        OAuth2User updatedGoogleProfile = new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "sub", "google-sub-1",
                        "email", "new-email@example.com",
                        "email_verified", true,
                        "name", "New Display Name",
                        "picture", "https://example.com/new-avatar.png"),
                "sub");

        User result = service.resolveOrCreateUser(updatedGoogleProfile);

        assertThat(result).isSameAs(user);
        assertThat(user.getEmail()).isEqualTo("new-email@example.com");
        assertThat(user.getFullName()).isEqualTo("New Display Name");
        assertThat(user.getAvatar()).isEqualTo("https://example.com/new-avatar.png");
        assertThat(socialAccount.getProviderEmail()).isEqualTo("new-email@example.com");
        verify(userRepository).saveAndFlush(user);
        verify(socialAccountRepository).saveAndFlush(socialAccount);
    }

    @Test
    @DisplayName("resolveOrCreateUser - existing Google social account with conflicting new email rejects")
    void resolveOrCreateUser_existingSocialAccount_conflictingEmail_rejects() {
        OAuth2GoogleAccountService service = service();
        User user = user(1L, "old-email@example.com");
        User anotherUser = user(2L, "new-email@example.com");
        SocialAccount socialAccount = socialAccount(user, "google-sub-1");

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-1"))
                .thenReturn(Optional.of(socialAccount));
        when(userRepository.findByEmailAndDeletedAtIsNull("new-email@example.com")).thenReturn(Optional.of(anotherUser));

        OAuth2User updatedGoogleProfile = googleProfile("google-sub-1", "new-email@example.com", true);

        assertThatThrownBy(() -> service.resolveOrCreateUser(updatedGoogleProfile))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("GOOGLE_EMAIL_ALREADY_IN_USE");
    }

    @Test
    @DisplayName("resolveOrCreateUser - existing Google social account with soft-deleted new email rejects")
    void resolveOrCreateUser_existingSocialAccount_softDeletedEmail_rejects() {
        OAuth2GoogleAccountService service = service();
        User user = user(1L, "old-email@example.com");
        SocialAccount socialAccount = socialAccount(user, "google-sub-1");

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-1"))
                .thenReturn(Optional.of(socialAccount));
        when(userRepository.findByEmailAndDeletedAtIsNull("new-email@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new-email@example.com")).thenReturn(true);

        OAuth2User updatedGoogleProfile = googleProfile("google-sub-1", "new-email@example.com", true);

        assertThatThrownBy(() -> service.resolveOrCreateUser(updatedGoogleProfile))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("ACCOUNT_DISABLED_OR_EMAIL_UNAVAILABLE");
    }

    @Test
    @DisplayName("resolveOrCreateUser - verified Google email links existing password user")
    void resolveOrCreateUser_verifiedEmail_linksExistingUser() {
        OAuth2GoogleAccountService service = service();
        User user = user(2L, "buyer@example.com");

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("buyer@example.com")).thenReturn(Optional.of(user));
        when(socialAccountRepository.findByUserIdAndProvider(2L, "GOOGLE")).thenReturn(Optional.empty());

        User result = service.resolveOrCreateUser(googleProfile("google-sub-2", " Buyer@Example.com ", true));

        assertThat(result).isSameAs(user);
        verify(socialAccountRepository).saveAndFlush(socialAccountCaptor.capture());
        assertThat(socialAccountCaptor.getValue().getUser()).isSameAs(user);
        assertThat(socialAccountCaptor.getValue().getProvider()).isEqualTo("GOOGLE");
        assertThat(socialAccountCaptor.getValue().getProviderUserId()).isEqualTo("google-sub-2");
        assertThat(socialAccountCaptor.getValue().getProviderEmail()).isEqualTo("buyer@example.com");
        assertThat(socialAccountCaptor.getValue().isProviderEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("resolveOrCreateUser - email already linked to different Google sub is rejected")
    void resolveOrCreateUser_emailLinkedToDifferentGoogleSub_rejects() {
        OAuth2GoogleAccountService service = service();
        User user = user(3L, "buyer@example.com");

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "new-google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("buyer@example.com")).thenReturn(Optional.of(user));
        when(socialAccountRepository.findByUserIdAndProvider(3L, "GOOGLE"))
                .thenReturn(Optional.of(socialAccount(user, "old-google-sub")));

        assertThatThrownBy(() -> service.resolveOrCreateUser(googleProfile("new-google-sub", "buyer@example.com", true)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("EMAIL_ALREADY_LINKED_TO_ANOTHER_GOOGLE_ACCOUNT");
    }

    @Test
    @DisplayName("resolveOrCreateUser - unverified Google email is rejected")
    void resolveOrCreateUser_unverifiedEmail_rejects() {
        OAuth2GoogleAccountService service = service();

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-4"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOrCreateUser(googleProfile("google-sub-4", "buyer@example.com", false)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Google email is not verified");
    }

    @Test
    @DisplayName("resolveOrCreateUser - soft-deleted email is rejected instead of reused")
    void resolveOrCreateUser_softDeletedEmail_rejects() {
        OAuth2GoogleAccountService service = service();

        when(socialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-5"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("buyer@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("buyer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.resolveOrCreateUser(googleProfile("google-sub-5", "buyer@example.com", true)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("ACCOUNT_DISABLED_OR_EMAIL_UNAVAILABLE");
    }

    private OAuth2GoogleAccountService service() {
        return new OAuth2GoogleAccountService(
                socialAccountRepository,
                userRepository,
                roleRepository,
                userHasRoleRepository);
    }

    private OAuth2User googleProfile(String sub, String email, boolean emailVerified) {
        return new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "sub", sub,
                        "email", email,
                        "email_verified", emailVerified,
                        "name", "Buyer Example",
                        "picture", "https://example.com/avatar.png"),
                "sub");
    }

    private User user(Long id, String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setFullName("Buyer Example");
        user.setAvatar("https://example.com/avatar.png");
        return user;
    }

    private SocialAccount socialAccount(User user, String providerUserId) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUser(user);
        socialAccount.setProvider("GOOGLE");
        socialAccount.setProviderUserId(providerUserId);
        socialAccount.setProviderEmail(user.getEmail());
        socialAccount.setProviderEmailVerified(true);
        return socialAccount;
    }
}
