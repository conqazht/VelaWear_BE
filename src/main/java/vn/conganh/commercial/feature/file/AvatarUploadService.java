package vn.conganh.commercial.feature.file;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.file.dto.FileUploadResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.user.dto.UserResponse;
import vn.conganh.commercial.security.ClientIpResolver;
import vn.conganh.commercial.security.ratelimit.AuthRateLimitService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarUploadService {

    private static final String POLICY = "avatar-upload";
    private static final String RATE_LIMIT_CODE = "AUTH_RATE_LIMITED";

    private final FileService fileService;
    private final UserRepository userRepository;
    private final AuthRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    @Transactional
    public UserResponse replaceAvatar(Jwt jwt, HttpServletRequest request, MultipartFile file) {
        Long userId = requireUserId(jwt);
        ClientIpResolver.ClientIp clientIp = clientIpResolver.resolve(request);
        rateLimitService.enforce(POLICY, RATE_LIMIT_CODE, Map.of(
                "user", String.valueOf(userId),
                "ip", clientIp.rateLimitPrefix(),
                "global", "all"), clientIp.rateLimitPrefix());

        User user = userRepository.findByIdAndDeletedAtIsNullWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        String previousAvatar = user.getAvatar();
        FileUploadResponse storedAvatar = fileService.storeAvatar(file);

        registerCleanup(storedAvatar.fileUrl(), previousAvatar);
        user.setAvatar(storedAvatar.fileUrl());
        User savedUser = userRepository.save(user);
        List<UserResponse.RoleSummaryResponse> roles = userRepository.findRolesByUserId(userId).stream()
                .map(UserResponse.RoleSummaryResponse::fromEntity)
                .toList();
        return UserResponse.fromEntity(savedUser, roles);
    }

    private Long requireUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        throw new ResourceNotFoundException("User", "id", "JWT");
    }

    private void registerCleanup(String newAvatar, String previousAvatar) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!fileService.deleteManagedAvatar(previousAvatar)) {
                    return;
                }
                log.info("[VelaWear/File] - AVATAR_CLEANUP: previous managed avatar removed after commit");
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    fileService.deleteManagedAvatar(newAvatar);
                }
            }
        });
    }
}
