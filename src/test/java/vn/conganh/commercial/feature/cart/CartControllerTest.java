package vn.conganh.commercial.feature.cart;

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
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Cart - CartController")
class CartControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /carts - 201: tạo cart thành công khi user chưa có cart")
        void createCart_validRequest_returnsCreatedCart() throws Exception {
            // Arrange
            User user = userRepository.save(user("cart.user@velawear.local"));
            CreateCartRequest request = new CreateCartRequest(user.getId());

            // Act & Assert
            mockMvc.perform(post("/api/v1/carts")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.userEmail", is("cart.user@velawear.local")));

            assertThat(cartRepository.existsByUserId(user.getId())).isTrue();
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /carts - 400: từ chối khi user đã có cart")
        void createCart_existingCart_returnsBadRequestAndDoesNotCreateNewCart() throws Exception {
            // Arrange
            User user = userRepository.save(user("cart.duplicate@velawear.local"));
            Cart cart = new Cart();
            cart.setUser(user);
            cartRepository.save(cart);
            CreateCartRequest request = new CreateCartRequest(user.getId());
            long countBefore = cartRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/carts")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(cartRepository.count()).isEqualTo(countBefore);
        }
    }

    private User user(String email) {
        User user = new User();
        user.setFullName("Cart Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$alreadyencoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
