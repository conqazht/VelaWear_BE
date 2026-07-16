package vn.conganh.commercial.feature.useraddress;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.CreateMyUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressFilterRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;

@RestController
@RequestMapping("/api/v1/user-addresses")
@RequiredArgsConstructor
@Tag(name = "User Addresses", description = "User shipping address management endpoints")
public class UserAddressController {

    private final UserAddressService userAddressService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getUserAddresses(
            @ParameterObject UserAddressFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userAddressService.getAllUserAddresses(filter, pageable)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> getUserAddress(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userAddressService.getUserAddressById(id)));
    }

    @GetMapping(path = "/me")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getMyUserAddresses(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                userAddressService.getMyUserAddresses(jwt.getSubject(), pageable)));
    }

    @GetMapping(path = "/me/{id}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> getMyUserAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                userAddressService.getMyUserAddressById(jwt.getSubject(), id)));
    }

    @PostMapping(path = "/me")
    public ResponseEntity<ApiResponse<UserAddressResponse>> createMyUserAddress(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateMyUserAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userAddressService.createMyUserAddress(jwt.getSubject(), request)));
    }

    @PutMapping(path = "/me/{id}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> updateMyUserAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userAddressService.updateMyUserAddress(jwt.getSubject(), id, request)));
    }

    @DeleteMapping(path = "/me/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMyUserAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        userAddressService.deleteMyUserAddress(jwt.getSubject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserAddressResponse>> createUserAddress(
            @RequestBody @Valid CreateUserAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userAddressService.createUserAddress(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<UserAddressResponse>> updateUserAddress(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userAddressService.updateUserAddress(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUserAddress(@PathVariable Long id) {
        userAddressService.deleteUserAddress(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
