package vn.conganh.commercial.feature.useraddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.useraddress.dto.CreateUserAddressRequest;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module UserAddress - UserAddressController")
class UserAddressControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /user-addresses - 201: tạo địa chỉ thành công khi dữ liệu hợp lệ")
        void createUserAddress_validRequest_returnsCreatedAddress() throws Exception {
            // Arrange
            User user = userRepository.save(user("address.user@velawear.local"));
            CreateUserAddressRequest request = validRequest(user.getId(), true);

            // Act & Assert
            mockMvc.perform(post("/api/v1/user-addresses")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.receiverName", is("Nguyen Van A")))
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            assertThat(userAddressRepository.findByUserId(user.getId())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /user-addresses - 400: từ chối khi receiverName để trống")
        void createUserAddress_blankReceiverName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            User user = userRepository.save(user("address.blank@velawear.local"));
            CreateUserAddressRequest request = new CreateUserAddressRequest(
                    user.getId(),
                    "",
                    "0123456789",
                    "Ho Chi Minh",
                    "District 1",
                    "Ben Nghe",
                    "123 Le Loi",
                    false);

            // Act & Assert
            mockMvc.perform(post("/api/v1/user-addresses")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(userAddressRepository.findByUserId(user.getId())).isEmpty();
        }
    }

    private CreateUserAddressRequest validRequest(Long userId, boolean isDefault) {
        return new CreateUserAddressRequest(
                userId,
                "Nguyen Van A",
                "0123456789",
                "Ho Chi Minh",
                "District 1",
                "Ben Nghe",
                "123 Le Loi",
                isDefault);
    }

    private User user(String email) {
        User user = new User();
        user.setFullName("Address Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$alreadyencoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
