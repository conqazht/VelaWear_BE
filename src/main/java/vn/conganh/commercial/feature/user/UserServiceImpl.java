package vn.conganh.commercial.feature.user;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
import vn.conganh.commercial.feature.user.dto.UpdateUserRequest;
import lombok.RequiredArgsConstructor;
import vn.conganh.commercial.feature.user.dto.UserResponse;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserHasRoleRepository userHasRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllUsers(Pageable pageable) {
        return ResultPaginationDTO.fromPage(userRepository.findAllByDeletedAtIsNull(pageable)
                .map(UserResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = findActiveUser(id);
        List<UserResponse.RoleSummaryResponse> roles = userHasRoleRepository.findRolesByUserId(user.getId()).stream()
                .map(UserResponse.RoleSummaryResponse::fromEntity)
                .toList();
        List<UserResponse.PermissionSummaryResponse> permissions =
                userHasRoleRepository.findEffectivePermissionsByUserId(user.getId()).stream()
                        .map(UserResponse.PermissionSummaryResponse::fromEntity)
                        .toList();
        return UserResponse.fromEntity(user, roles, permissions);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBirthDate(request.birthDate());
        user.setAvatar(request.avatar());
        user.setGender(request.gender());

        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findActiveUser(id);
        user.setFullName(request.fullName());
        user.setBirthDate(request.birthDate());
        user.setAvatar(request.avatar());
        user.setGender(request.gender());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = findActiveUser(id);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    private User findActiveUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
