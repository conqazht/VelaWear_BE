package vn.conganh.commercial.feature.brand;

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
import vn.conganh.commercial.feature.brand.dto.BrandResponse;
import vn.conganh.commercial.feature.brand.dto.CreateBrandRequest;
import vn.conganh.commercial.feature.brand.dto.UpdateBrandRequest;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(brandService.getAll()));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(brandService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> create(@RequestBody @Valid CreateBrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(brandService.create(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateBrandRequest request) {
        return ResponseEntity.ok(ApiResponse.success(brandService.update(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
