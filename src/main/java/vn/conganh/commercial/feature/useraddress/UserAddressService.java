package vn.conganh.commercial.feature.useraddress;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.CreateMyUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressFilterRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;

public interface UserAddressService {

    ResultPaginationDTO getAllUserAddresses(UserAddressFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getUserAddressesByUserId(Long userId, UserAddressFilterRequest filter, Pageable pageable);

    UserAddressResponse getUserAddressById(Long id);

    ResultPaginationDTO getMyUserAddresses(String email, Pageable pageable);

    UserAddressResponse getMyUserAddressById(String email, Long id);

    UserAddressResponse createMyUserAddress(String email, CreateMyUserAddressRequest request);

    UserAddressResponse updateMyUserAddress(String email, Long id, UpdateUserAddressRequest request);

    void deleteMyUserAddress(String email, Long id);

    UserAddressResponse createUserAddress(CreateUserAddressRequest request);

    UserAddressResponse updateUserAddress(Long id, UpdateUserAddressRequest request);

    void deleteUserAddress(Long id);
}
