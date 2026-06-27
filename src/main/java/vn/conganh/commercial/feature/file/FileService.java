package vn.conganh.commercial.feature.file;

import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.feature.file.dto.FileUploadResponse;

public interface FileService {

    FileUploadResponse store(MultipartFile file, String folder);
}
