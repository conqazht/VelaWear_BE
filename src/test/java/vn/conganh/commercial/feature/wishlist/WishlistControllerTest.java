package vn.conganh.commercial.feature.wishlist;

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
import vn.conganh.commercial.feature.brand.Brand;
import vn.conganh.commercial.feature.brand.BrandRepository;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Wishlist - WishlistController")
class WishlistControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /wishlists - 201: tạo wishlist thành công khi user và product hợp lệ")
        void createWishlist_validRequest_returnsCreatedWishlist() throws Exception {
            // Arrange
            User user = userRepository.save(user("wishlist.user@velawear.local"));
            Product product = productRepository.save(product("Wishlist Product", "wishlist-product-post"));
            CreateWishlistRequest request = new CreateWishlistRequest(user.getId(), product.getId());

            // Act & Assert
            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.productId").value(product.getId()));

            assertThat(wishlistRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue();
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /wishlists - 400: từ chối khi wishlist đã tồn tại")
        void createWishlist_duplicateWishlist_returnsBadRequestAndDoesNotCreateNewWishlist() throws Exception {
            // Arrange
            User user = userRepository.save(user("wishlist.duplicate@velawear.local"));
            Product product = productRepository.save(product("Wishlist Duplicate Product", "wishlist-duplicate-product"));
            Wishlist wishlist = new Wishlist();
            wishlist.setUser(user);
            wishlist.setProduct(product);
            wishlistRepository.save(wishlist);
            CreateWishlistRequest request = new CreateWishlistRequest(user.getId(), product.getId());
            long countBefore = wishlistRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(wishlistRepository.count()).isEqualTo(countBefore);
        }
    }

    private Product product(String name, String slug) {
        Category category = categoryRepository.save(category(name + " Category", slug + "-category"));
        Brand brand = brandRepository.save(brand(name + " Brand", slug + "-brand"));
        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setBrandId(brand.getId());
        product.setName(name);
        product.setSlug(slug);
        product.setStatus("ACTIVE");
        return product;
    }

    private Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setSortOrder(1);
        category.setStatus("ACTIVE");
        return category;
    }

    private Brand brand(String name, String slug) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        brand.setStatus("ACTIVE");
        return brand;
    }

    private User user(String email) {
        User user = new User();
        user.setFullName("Wishlist Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$alreadyencoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
