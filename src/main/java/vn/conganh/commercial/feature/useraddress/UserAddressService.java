package vn.conganh.commercial.feature.useraddress;

import java.util.List;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;

public interface UserAddressService {

    List<UserAddressResponse> getAllUserAddresses();

    List<UserAddressResponse> getUserAddressesByUserId(Long userId);

    UserAddressResponse getUserAddressById(Long id);

    UserAddressResponse createUserAddress(CreateUserAddressRequest request);

    UserAddressResponse updateUserAddress(Long id, UpdateUserAddressRequest request);

    void deleteUserAddress(Long id);
}
