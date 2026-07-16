package vn.conganh.commercial.feature.useraddress;

import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.CreateMyUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressFilterRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;
import vn.conganh.commercial.util.FilterSpecifications;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllUserAddresses(UserAddressFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(userAddressRepository.findAll(Specification.where(UserAddressSpecification.build(filter)), pageable)
                .map(UserAddressResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getUserAddressesByUserId(Long userId, UserAddressFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("userId", userId, filter == null ? null : filter.userId());
        UserAddressFilterRequest scopedFilter = filter == null
                ? new UserAddressFilterRequest(userId, null, null, null, null, null)
                : filter.withUserId(userId);

        return ResultPaginationDTO.fromPage(userAddressRepository.findAll(Specification.where(UserAddressSpecification.build(scopedFilter)), pageable)
                .map(UserAddressResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddressResponse getUserAddressById(Long id) {
        return UserAddressResponse.fromEntity(findUserAddress(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyUserAddresses(String email, Pageable pageable) {
        User user = findUser(email);
        return ResultPaginationDTO.fromPage(userAddressRepository.findByUserId(user.getId(), pageable)
                .map(UserAddressResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddressResponse getMyUserAddressById(String email, Long id) {
        User user = findUser(email);
        return UserAddressResponse.fromEntity(findUserAddress(id, user.getId()));
    }

    @Override
    @Transactional
    public UserAddressResponse createMyUserAddress(String email, CreateMyUserAddressRequest request) {
        User user = findUser(email);

        UserAddress userAddress = new UserAddress();
        userAddress.setUser(user);
        userAddress.setReceiverName(request.receiverName());
        userAddress.setPhone(request.phone());
        userAddress.setProvince(request.province());
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
    public UserAddressResponse updateMyUserAddress(String email, Long id, UpdateUserAddressRequest request) {
        User user = findUser(email);
        UserAddress userAddress = findUserAddress(id, user.getId());
        updateFields(userAddress, request);
        return UserAddressResponse.fromEntity(userAddressRepository.save(userAddress));
    }

    @Override
    @Transactional
    public void deleteMyUserAddress(String email, Long id) {
        User user = findUser(email);
        userAddressRepository.delete(findUserAddress(id, user.getId()));
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
        updateFields(userAddress, request);
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

    private UserAddress findUserAddress(Long id, Long userId) {
        return userAddressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAddress", "id", id));
    }

    private User findUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private User findUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private void updateFields(UserAddress userAddress, UpdateUserAddressRequest request) {
        userAddress.setReceiverName(request.receiverName());
        userAddress.setPhone(request.phone());
        userAddress.setProvince(request.province());
        userAddress.setWard(request.ward());
        userAddress.setAddressDetail(request.addressDetail());

        if (request.isDefault() && !userAddress.isDefault()) {
            unsetCurrentDefault(userAddress.getUser().getId());
            userAddress.setDefault(true);
        } else if (!request.isDefault() && userAddress.isDefault()) {
            userAddress.setDefault(false);
        }
    }

    private void unsetCurrentDefault(Long userId) {
        userAddressRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(address -> {
                    address.setDefault(false);
                    userAddressRepository.save(address);
                    // The partial unique index permits only one default per user.
                    // Flush the demotion before an IDENTITY insert or promotion can write true.
                    userAddressRepository.flush();
                });
    }
}
