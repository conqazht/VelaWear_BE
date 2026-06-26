package vn.conganh.commercial.feature.productvariant;

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
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;

@RestController
@RequestMapping("/api/v1/product-variants")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(productVariantService.getAll()));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productVariantService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductVariantResponse>> create(
            @RequestBody @Valid CreateProductVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(productVariantService.create(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProductVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productVariantService.update(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productVariantService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
