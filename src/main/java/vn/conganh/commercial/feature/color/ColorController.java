package vn.conganh.commercial.feature.color;

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
import vn.conganh.commercial.feature.color.dto.ColorResponse;
import vn.conganh.commercial.feature.color.dto.CreateColorRequest;
import vn.conganh.commercial.feature.color.dto.UpdateColorRequest;

@RestController
@RequestMapping("/api/colors")
@RequiredArgsConstructor
public class ColorController {

    private final ColorService colorService;

    @GetMapping(version = "1")
    public ResponseEntity<ApiResponse<List<ColorResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(colorService.getAll()));
    }

    @GetMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<ColorResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(colorService.getById(id)));
    }

    @PostMapping(version = "1")
    public ResponseEntity<ApiResponse<ColorResponse>> create(@RequestBody @Valid CreateColorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(colorService.create(request)));
    }

    @PutMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<ColorResponse>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateColorRequest request) {
        return ResponseEntity.ok(ApiResponse.success(colorService.update(id, request)));
    }

    @DeleteMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        colorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
