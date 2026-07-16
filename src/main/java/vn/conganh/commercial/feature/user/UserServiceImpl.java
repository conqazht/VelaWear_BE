package vn.conganh.commercial.feature.user;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
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
import vn.conganh.commercial.feature.user.dto.UserFilterRequest;
import vn.conganh.commercial.feature.user.dto.UserResponse;
import vn.conganh.commercial.feature.user.dto.UpdateUserRolesRequest;
import vn.conganh.commercial.feature.role.Role;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.security.session.SessionRevocationReason;
import vn.conganh.commercial.security.session.SessionRevocationService;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserHasRoleRepository userHasRoleRepository;
    private final RoleRepository roleRepository;
    private final SessionRevocationService sessionRevocationService;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllUsers(UserFilterRequest filter, Pageable pageable) {
        Page<User> users = userRepository.findAll(Specification.where(UserSpecification.build(filter)), pageable);
        Map<Long, List<UserResponse.RoleSummaryResponse>> rolesByUserId = rolesByUserId(users.getContent());

        return ResultPaginationDTO.fromPage(users.map(user ->
                UserResponse.fromEntity(user, rolesByUserId.getOrDefault(user.getId(), List.of()))));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = findActiveUser(id);
        List<UserResponse.RoleSummaryResponse> roles =
                rolesByUserId(List.of(user)).getOrDefault(user.getId(), List.of());
        return UserResponse.fromEntity(user, roles);
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
    public UserResponse updateUserRoles(Long id, UpdateUserRolesRequest request) {
        User user = findActiveUser(id);

        // Thay thế danh sách role của user một cách nguyên tử trong transaction này.
        userHasRoleRepository.deleteByUserId(id);

        for (String roleName : request.roles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
            UserHasRole uhr = new UserHasRole();
            uhr.setUser(user);
            uhr.setRole(role);
            userHasRoleRepository.save(uhr);
        }

        // Mọi access/refresh token cũ bị vô hiệu ngay sau khi thay đổi quyền.
        sessionRevocationService.revokeAll(user, SessionRevocationReason.ROLE_CHANGE);

        List<UserResponse.RoleSummaryResponse> roles =
                rolesByUserId(List.of(user)).getOrDefault(user.getId(), List.of());
        return UserResponse.fromEntity(user, roles);
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

    private Map<Long, List<UserResponse.RoleSummaryResponse>> rolesByUserId(List<User> users) {
        if (users.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> userIds = users.stream()
                .map(User::getId)
                .toList();

        return userRepository.findAllWithRoleByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        userHasRole -> userHasRole.getUser().getId(),
                        Collectors.mapping(
                                userHasRole -> UserResponse.RoleSummaryResponse.fromEntity(userHasRole.getRole()),
                                Collectors.toList())));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
