package vn.conganh.commercial.feature.user;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
import vn.conganh.commercial.feature.user.dto.UpdateMyProfileRequest;
import vn.conganh.commercial.feature.user.dto.UpdateUserRequest;
import vn.conganh.commercial.feature.user.dto.UpdateUserRolesRequest;
import vn.conganh.commercial.feature.user.dto.UserFilterRequest;
import vn.conganh.commercial.feature.user.dto.UserResponse;

public interface UserService {

    ResultPaginationDTO getAllUsers(UserFilterRequest filter, Pageable pageable);

    UserResponse getUserById(Long id);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    UserResponse updateMyProfile(String email, UpdateMyProfileRequest request);

    UserResponse updateUserRoles(Long id, UpdateUserRolesRequest request);

    void deleteUser(Long id);
}
