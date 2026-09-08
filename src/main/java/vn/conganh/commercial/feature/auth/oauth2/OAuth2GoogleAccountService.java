package vn.conganh.commercial.feature.auth.oauth2;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.role.Role;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.feature.socialaccount.SocialAccount;
import vn.conganh.commercial.feature.socialaccount.SocialAccountRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserHasRole;
import vn.conganh.commercial.feature.user.UserHasRoleRepository;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
@RequiredArgsConstructor
public class OAuth2GoogleAccountService {

    public static final String GOOGLE_PROVIDER = "GOOGLE";

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserHasRoleRepository userHasRoleRepository;

    @Transactional
    public User resolveOrCreateUser(OAuth2User oauth2User) {
        GoogleProfile profile = GoogleProfile.from(oauth2User);

        return socialAccountRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, profile.providerUserId())
                .map(socialAccount -> syncExistingSocialAccount(socialAccount, profile))
                .map(this::requireActiveUser)
                .orElseGet(() -> resolveByVerifiedEmail(profile));
    }

    private User syncExistingSocialAccount(SocialAccount socialAccount, GoogleProfile profile) {
        User user = socialAccount.getUser();
        boolean userDirty = false;
        boolean socialAccountDirty = false;

        boolean emailOriginallySynced = socialAccount.getProviderEmail() != null
                && socialAccount.getProviderEmail().equalsIgnoreCase(user.getEmail());
        boolean googleEmailChanged = !profile.email().equalsIgnoreCase(socialAccount.getProviderEmail());

        if (profile.emailVerified() && emailOriginallySynced && googleEmailChanged) {
            userRepository.findByEmailAndDeletedAtIsNull(profile.email())
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new InvalidRequestException("GOOGLE_EMAIL_ALREADY_IN_USE");
                    });

            if (userRepository.existsByEmail(profile.email())
                    && userRepository.findByEmailAndDeletedAtIsNull(profile.email()).isEmpty()) {
                throw new InvalidRequestException("ACCOUNT_DISABLED_OR_EMAIL_UNAVAILABLE");
            }

            user.setEmail(profile.email());
            userDirty = true;
        }

        if (profile.displayName() != null && !profile.displayName().isBlank()
                && (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(profile.displayName());
            userDirty = true;
        }

        if (profile.picture() != null && !profile.picture().isBlank()
                && (user.getAvatar() == null || user.getAvatar().isBlank() || user.getAvatar().startsWith("http"))
                && !profile.picture().equals(user.getAvatar())) {
            user.setAvatar(profile.picture());
            userDirty = true;
        }

        if (!profile.email().equalsIgnoreCase(socialAccount.getProviderEmail())) {
            socialAccount.setProviderEmail(profile.email());
            socialAccountDirty = true;
        }

        if (profile.emailVerified() != socialAccount.isProviderEmailVerified()) {
            socialAccount.setProviderEmailVerified(profile.emailVerified());
            socialAccountDirty = true;
        }

        if (userDirty) {
            userRepository.saveAndFlush(user);
        }

        if (socialAccountDirty) {
            socialAccountRepository.saveAndFlush(socialAccount);
        }

        return user;
    }

    private User resolveByVerifiedEmail(GoogleProfile profile) {
        if (!profile.emailVerified()) {
            throw new InvalidRequestException("Google email is not verified");
        }

        return userRepository.findByEmailAndDeletedAtIsNull(profile.email())
                .map(user -> linkExistingUser(user, profile))
                .orElseGet(() -> createUserAndLink(profile));
    }

    private User linkExistingUser(User user, GoogleProfile profile) {
        socialAccountRepository.findByUserIdAndProvider(user.getId(), GOOGLE_PROVIDER)
                .ifPresent(existing -> {
                    if (!existing.getProviderUserId().equals(profile.providerUserId())) {
                        throw new InvalidRequestException("EMAIL_ALREADY_LINKED_TO_ANOTHER_GOOGLE_ACCOUNT");
                    }
                });
        try {
            socialAccountRepository.saveAndFlush(newSocialAccount(user, profile));
            return user;
        } catch (DataIntegrityViolationException exception) {
            return recoverConcurrentLink(profile);
        }
    }

    private User createUserAndLink(GoogleProfile profile) {
        if (userRepository.existsByEmail(profile.email())) {
            throw new InvalidRequestException("ACCOUNT_DISABLED_OR_EMAIL_UNAVAILABLE");
        }

        try {
            User user = new User();
            user.setEmail(profile.email());
            user.setFullName(profile.displayName());
            user.setAvatar(profile.picture());
            User savedUser = userRepository.saveAndFlush(user);

            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "USER"));
            UserHasRole userHasRole = new UserHasRole();
            userHasRole.setUser(savedUser);
            userHasRole.setRole(userRole);
            userHasRoleRepository.saveAndFlush(userHasRole);

            socialAccountRepository.saveAndFlush(newSocialAccount(savedUser, profile));
            return savedUser;
        } catch (DataIntegrityViolationException exception) {
            return recoverConcurrentLink(profile);
        }
    }

    private User recoverConcurrentLink(GoogleProfile profile) {
        return socialAccountRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, profile.providerUserId())
                .map(SocialAccount::getUser)
                .map(this::requireActiveUser)
                .orElseThrow(() -> new InvalidRequestException("GOOGLE_ACCOUNT_LINK_CONFLICT"));
    }

    private SocialAccount newSocialAccount(User user, GoogleProfile profile) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUser(user);
        socialAccount.setProvider(GOOGLE_PROVIDER);
        socialAccount.setProviderUserId(profile.providerUserId());
        socialAccount.setProviderEmail(profile.email());
        socialAccount.setProviderEmailVerified(profile.emailVerified());
        return socialAccount;
    }

    private User requireActiveUser(User user) {
        if (user.getDeletedAt() != null) {
            throw new InvalidRequestException("ACCOUNT_DISABLED_OR_EMAIL_UNAVAILABLE");
        }
        return user;
    }

    private record GoogleProfile(
            String providerUserId,
            String email,
            boolean emailVerified,
            String displayName,
            String picture
    ) {

        static GoogleProfile from(OAuth2User oauth2User) {
            String providerUserId = requiredAttribute(oauth2User, "sub");
            String email = normalizeEmail(requiredAttribute(oauth2User, "email"));
            boolean emailVerified = booleanAttribute(oauth2User, "email_verified");
            String displayName = optionalAttribute(oauth2User, "name");
            String picture = optionalAttribute(oauth2User, "picture");

            if (displayName == null || displayName.isBlank()) {
                displayName = email.substring(0, email.indexOf('@'));
            }
            return new GoogleProfile(providerUserId, email, emailVerified, displayName, picture);
        }

        private static String requiredAttribute(OAuth2User oauth2User, String name) {
            String value = optionalAttribute(oauth2User, name);
            if (value == null || value.isBlank()) {
                throw new InvalidRequestException("Google profile is missing required attribute: " + name);
            }
            return value;
        }

        private static String optionalAttribute(OAuth2User oauth2User, String name) {
            Object value = oauth2User.getAttribute(name);
            return value == null ? null : String.valueOf(value);
        }

        private static boolean booleanAttribute(OAuth2User oauth2User, String name) {
            Object value = oauth2User.getAttribute(name);
            if (value instanceof Boolean bool) {
                return bool;
            }
            return value != null && Boolean.parseBoolean(String.valueOf(value));
        }

        private static String normalizeEmail(String email) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
