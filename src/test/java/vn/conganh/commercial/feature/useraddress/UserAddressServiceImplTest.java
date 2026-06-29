package vn.conganh.commercial.feature.useraddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UpdateUserAddressRequest;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressResponse;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module UserAddress - UserAddressServiceImpl")
class UserAddressServiceImplTest {

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private UserRepository userRepository;

    private UserAddressServiceImpl userAddressService;

    @BeforeEach
    void setUp() {
        userAddressService = new UserAddressServiceImpl(userAddressRepository, userRepository);
    }

    @Nested
    @DisplayName("Create user address")
    class CreateUserAddress {

        @Test
        @DisplayName("createUserAddress - tạo địa chỉ thành công khi user tồn tại")
        void createUserAddress_validRequest_returnsUserAddressResponse() {
            // Arrange
            User user = user(1L);
            CreateUserAddressRequest request = createRequest(false);
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
            when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> {
                UserAddress address = invocation.getArgument(0);
                ReflectionTestUtils.setField(address, "id", 10L);
                return address;
            });

            // Act
            UserAddressResponse response = userAddressService.createUserAddress(request);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.receiverName()).isEqualTo("Nguyen Van A");
            assertThat(response.isDefault()).isFalse();
        }

        @Test
        @DisplayName("createUserAddress - unset địa chỉ default cũ khi tạo default mới")
        void createUserAddress_defaultRequest_unsetsCurrentDefaultBeforeSave() {
            // Arrange
            User user = user(1L);
            UserAddress currentDefault = address(9L, user, true);
            CreateUserAddressRequest request = createRequest(true);
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
            when(userAddressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(currentDefault));
            when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserAddressResponse response = userAddressService.createUserAddress(request);

            // Assert
            assertThat(currentDefault.isDefault()).isFalse();
            assertThat(response.isDefault()).isTrue();
            verify(userAddressRepository).save(currentDefault);
            verify(userAddressRepository).save(argThat(UserAddress::isDefault));
        }

        @Test
        @DisplayName("createUserAddress - không gọi save khi user không tồn tại")
        void createUserAddress_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreateUserAddressRequest request = createRequest(false);
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userAddressService.createUserAddress(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(userAddressRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read user address")
    class ReadUserAddress {

        @Test
        @DisplayName("getAllUserAddresses - trả về danh sách địa chỉ")
        void getAllUserAddresses_existingAddresses_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(userAddressRepository.findAll(ArgumentMatchers.<Specification<UserAddress>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(address(10L, user(1L), false)), pageable, 1));

            // Act
            ResultPaginationDTO responses = userAddressService.getAllUserAddresses(null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("userId").containsExactly(1L);
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getUserAddressesByUserId - trả về địa chỉ theo user")
        void getUserAddressesByUserId_existingAddresses_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(userAddressRepository.findAll(ArgumentMatchers.<Specification<UserAddress>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(address(10L, user(1L), true)), pageable, 1));

            // Act
            ResultPaginationDTO responses = userAddressService.getUserAddressesByUserId(1L, null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("default").containsExactly(true);
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getUserAddressById - ném ResourceNotFoundException khi không tìm thấy địa chỉ")
        void getUserAddressById_missingAddress_throwsResourceNotFoundException() {
            // Arrange
            when(userAddressRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userAddressService.getUserAddressById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update user address")
    class UpdateUserAddress {

        @Test
        @DisplayName("updateUserAddress - cập nhật địa chỉ thành công")
        void updateUserAddress_existingAddress_returnsUpdatedResponse() {
            // Arrange
            UserAddress address = address(10L, user(1L), false);
            UpdateUserAddressRequest request = updateRequest(false);
            when(userAddressRepository.findById(10L)).thenReturn(Optional.of(address));
            when(userAddressRepository.save(address)).thenReturn(address);

            // Act
            UserAddressResponse response = userAddressService.updateUserAddress(10L, request);

            // Assert
            assertThat(response.receiverName()).isEqualTo("Tran Thi B");
            assertThat(response.phone()).isEqualTo("0987654321");
        }

        @Test
        @DisplayName("updateUserAddress - unset default cũ khi chuyển địa chỉ hiện tại thành default")
        void updateUserAddress_makeDefault_unsetsCurrentDefault() {
            // Arrange
            User user = user(1L);
            UserAddress address = address(10L, user, false);
            UserAddress currentDefault = address(9L, user, true);
            UpdateUserAddressRequest request = updateRequest(true);
            when(userAddressRepository.findById(10L)).thenReturn(Optional.of(address));
            when(userAddressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(currentDefault));
            when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserAddressResponse response = userAddressService.updateUserAddress(10L, request);

            // Assert
            assertThat(currentDefault.isDefault()).isFalse();
            assertThat(response.isDefault()).isTrue();
            verify(userAddressRepository).save(currentDefault);
        }
    }

    @Nested
    @DisplayName("Delete user address")
    class DeleteUserAddress {

        @Test
        @DisplayName("deleteUserAddress - xóa địa chỉ khi id tồn tại")
        void deleteUserAddress_existingAddress_deletesAddress() {
            // Arrange
            UserAddress address = address(10L, user(1L), false);
            when(userAddressRepository.findById(10L)).thenReturn(Optional.of(address));

            // Act
            userAddressService.deleteUserAddress(10L);

            // Assert
            verify(userAddressRepository).delete(address);
        }
    }

    private CreateUserAddressRequest createRequest(boolean isDefault) {
        return new CreateUserAddressRequest(
                1L,
                "Nguyen Van A",
                "0123456789",
                "Ho Chi Minh",
                "District 1",
                "Ben Nghe",
                "123 Le Loi",
                isDefault);
    }

    private UpdateUserAddressRequest updateRequest(boolean isDefault) {
        return new UpdateUserAddressRequest(
                "Tran Thi B",
                "0987654321",
                "Ha Noi",
                "Cau Giay",
                "Dich Vong",
                "456 Xuan Thuy",
                isDefault);
    }

    private UserAddress address(Long id, User user, boolean isDefault) {
        UserAddress address = new UserAddress();
        ReflectionTestUtils.setField(address, "id", id);
        address.setUser(user);
        address.setReceiverName("Nguyen Van A");
        address.setPhone("0123456789");
        address.setProvince("Ho Chi Minh");
        address.setDistrict("District 1");
        address.setWard("Ben Nghe");
        address.setAddressDetail("123 Le Loi");
        address.setDefault(isDefault);
        return address;
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("User " + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.MALE);
        return user;
    }
}
