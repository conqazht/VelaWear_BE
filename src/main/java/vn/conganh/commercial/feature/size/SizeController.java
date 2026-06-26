package vn.conganh.commercial.feature.size;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

@RestController
@RequestMapping("/api/v1/sizes")
@RequiredArgsConstructor
public class SizeController {

    private final SizeService sizeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SizeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(sizeService.getAll()));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<SizeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(sizeService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SizeResponse>> create(@RequestBody @Valid CreateSizeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(sizeService.create(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<SizeResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateSizeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(sizeService.update(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        sizeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
