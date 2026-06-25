package vn.conganh.commercial.feature.useraddress;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserAddressResponse> getAllUserAddresses() {
        return userAddressRepository.findAll().stream()
                .map(UserAddressResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAddressResponse> getUserAddressesByUserId(Long userId) {
        return userAddressRepository.findByUserId(userId).stream()
                .map(UserAddressResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddressResponse getUserAddressById(Long id) {
        return UserAddressResponse.fromEntity(findUserAddress(id));
    }

    @Override
    @Transactional
    public UserAddressResponse createUserAddress(CreateUserAddressRequest request) {
        User user = findUser(request.userId());

        UserAddress userAddress = new UserAddress();
        userAddress.setUser(user);
        userAddress.setReceiverName(request.receiverName());
        userAddress.setPhone(request.phone());
        userAddress.setProvince(request.province());
        userAddress.setDistrict(request.district());
        userAddress.setWard(request.ward());
        userAddress.setAddressDetail(request.addressDetail());

        if (request.isDefault()) {
            unsetCurrentDefault(user.getId());
            userAddress.setDefault(true);
        }

        return UserAddressResponse.fromEntity(userAddressRepository.save(userAddress));
    }

    @Override
    @Transactional
    public UserAddressResponse updateUserAddress(Long id, UpdateUserAddressRequest request) {
        UserAddress userAddress = findUserAddress(id);
        userAddress.setReceiverName(request.receiverName());
        userAddress.setPhone(request.phone());
        userAddress.setProvince(request.province());
        userAddress.setDistrict(request.district());
        userAddress.setWard(request.ward());
        userAddress.setAddressDetail(request.addressDetail());

        if (request.isDefault() && !userAddress.isDefault()) {
            unsetCurrentDefault(userAddress.getUser().getId());
            userAddress.setDefault(true);
        } else if (!request.isDefault() && userAddress.isDefault()) {
            userAddress.setDefault(false);
        }

        return UserAddressResponse.fromEntity(userAddressRepository.save(userAddress));
    }

    @Override
    @Transactional
    public void deleteUserAddress(Long id) {
        UserAddress userAddress = findUserAddress(id);
        userAddressRepository.delete(userAddress);
    }

    private UserAddress findUserAddress(Long id) {
        return userAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserAddress", "id", id));
    }

    private User findUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private void unsetCurrentDefault(Long userId) {
        userAddressRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(address -> {
                    address.setDefault(false);
                    userAddressRepository.save(address);
                });
    }
}
