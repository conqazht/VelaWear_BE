package vn.conganh.commercial.feature.size;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeFilterRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

@RestController
@RequestMapping("/api/v1/sizes")
@RequiredArgsConstructor
@Tag(name = "Sizes", description = "Product size management endpoints")
public class SizeController {

    private final SizeService sizeService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getAll(
            @ParameterObject SizeFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(sizeService.getAll(filter, pageable)));
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
