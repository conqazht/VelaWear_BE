package vn.conganh.commercial.feature.file;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.file.dto.FileUploadResponse;
import vn.conganh.commercial.feature.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Upload and serve user avatar or company logo files")
public class FileController {

    private final FileService fileService;
    private final AvatarUploadService avatarUploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("folder") String folder) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(fileService.store(file, folder)));
    }

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(avatarUploadService.replaceAvatar(jwt, request, file)));
    }
}
