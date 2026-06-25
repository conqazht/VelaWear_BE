package vn.conganh.commercial.feature.useraddress;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;

@RestController
@RequestMapping("/api/user-addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @GetMapping(version = "1")
    public ResponseEntity<ApiResponse<List<UserAddressResponse>>> getUserAddresses(
            @RequestParam(required = false) Long userId) {
        if (userId != null) {
            return ResponseEntity.ok(ApiResponse.success(userAddressService.getUserAddressesByUserId(userId)));
        }
        return ResponseEntity.ok(ApiResponse.success(userAddressService.getAllUserAddresses()));
    }

    @GetMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<UserAddressResponse>> getUserAddress(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userAddressService.getUserAddressById(id)));
    }

    @PostMapping(version = "1")
    public ResponseEntity<ApiResponse<UserAddressResponse>> createUserAddress(
            @RequestBody @Valid CreateUserAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userAddressService.createUserAddress(request)));
    }

    @PutMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<UserAddressResponse>> updateUserAddress(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userAddressService.updateUserAddress(id, request)));
    }

    @DeleteMapping(path = "/{id}", version = "1")
    public ResponseEntity<ApiResponse<Void>> deleteUserAddress(@PathVariable Long id) {
        userAddressService.deleteUserAddress(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
