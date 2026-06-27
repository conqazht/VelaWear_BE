package vn.conganh.commercial.feature.user;

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
import vn.conganh.commercial.feature.user.dto.UserResponse;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAllByDeletedAtIsNull(pageable);
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
