package vn.conganh.commercial.feature.useraddress;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;

public interface UserAddressService {

    ResultPaginationDTO getAllUserAddresses(Pageable pageable);

    ResultPaginationDTO getUserAddressesByUserId(Long userId, Pageable pageable);

    UserAddressResponse getUserAddressById(Long id);

    UserAddressResponse createUserAddress(CreateUserAddressRequest request);

    UserAddressResponse updateUserAddress(Long id, UpdateUserAddressRequest request);

    void deleteUserAddress(Long id);
}
