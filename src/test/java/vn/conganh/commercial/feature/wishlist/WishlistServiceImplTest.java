package vn.conganh.commercial.feature.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.wishlist.dto.CreateWishlistRequest;
import vn.conganh.commercial.feature.wishlist.dto.WishlistResponse;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Wishlist - WishlistServiceImpl")
class WishlistServiceImplTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    private WishlistServiceImpl wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistServiceImpl(wishlistRepository, userRepository, productRepository);
    }

    @Nested
    @DisplayName("Create wishlist")
    class CreateWishlist {

        @Test
        @DisplayName("createWishlist - tạo wishlist thành công khi user và product hợp lệ")
        void createWishlist_validRequest_returnsWishlistResponse() {
            // Arrange
            User user = user(1L);
            Product product = product(2L);
            CreateWishlistRequest request = new CreateWishlistRequest(1L, 2L);
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(2L)).thenReturn(Optional.of(product));
            when(wishlistRepository.existsByUserIdAndProductId(1L, 2L)).thenReturn(false);
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(invocation -> {
                Wishlist wishlist = invocation.getArgument(0);
                ReflectionTestUtils.setField(wishlist, "id", 10L);
                return wishlist;
            });

            // Act
            WishlistResponse response = wishlistService.createWishlist(request);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.productId()).isEqualTo(2L);
            verify(wishlistRepository).save(argThat(wishlist ->
                    wishlist.getUser().equals(user) && wishlist.getProduct().equals(product)));
        }

        @Test
        @DisplayName("createWishlist - không gọi save khi wishlist đã tồn tại")
        void createWishlist_duplicateWishlist_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateWishlistRequest request = new CreateWishlistRequest(1L, 2L);
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user(1L)));
            when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L)));
            when(wishlistRepository.existsByUserIdAndProductId(1L, 2L)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> wishlistService.createWishlist(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already exists");
            verify(wishlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("createWishlist - không gọi save khi user không tồn tại")
        void createWishlist_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreateWishlistRequest request = new CreateWishlistRequest(99L, 2L);
            when(userRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> wishlistService.createWishlist(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(productRepository, never()).findById(any());
            verify(wishlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("createWishlist - không gọi save khi product không tồn tại")
        void createWishlist_missingProduct_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreateWishlistRequest request = new CreateWishlistRequest(1L, 99L);
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user(1L)));
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> wishlistService.createWishlist(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(wishlistRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read wishlist")
    class ReadWishlist {

        @Test
        @DisplayName("getAllWishlists - trả về danh sách wishlist")
        void getAllWishlists_existingWishlists_returnsResponses() {
            // Arrange
            when(wishlistRepository.findAll()).thenReturn(List.of(wishlist(10L, user(1L), product(2L))));

            // Act
            List<WishlistResponse> responses = wishlistService.getAllWishlists();

            // Assert
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).productId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("getWishlistsByUserId - trả về wishlist theo user")
        void getWishlistsByUserId_existingWishlists_returnsResponses() {
            // Arrange
            when(wishlistRepository.findByUserId(1L)).thenReturn(List.of(wishlist(10L, user(1L), product(2L))));

            // Act
            List<WishlistResponse> responses = wishlistService.getWishlistsByUserId(1L);

            // Assert
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getWishlistById - ném ResourceNotFoundException khi không tìm thấy wishlist")
        void getWishlistById_missingWishlist_throwsResourceNotFoundException() {
            // Arrange
            when(wishlistRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> wishlistService.getWishlistById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete wishlist")
    class DeleteWishlist {

        @Test
        @DisplayName("deleteWishlist - xóa wishlist khi id tồn tại")
        void deleteWishlist_existingWishlist_deletesWishlist() {
            // Arrange
            Wishlist wishlist = wishlist(10L, user(1L), product(2L));
            when(wishlistRepository.findById(10L)).thenReturn(Optional.of(wishlist));

            // Act
            wishlistService.deleteWishlist(10L);

            // Assert
            verify(wishlistRepository).delete(wishlist);
        }
    }

    private Wishlist wishlist(Long id, User user, Product product) {
        Wishlist wishlist = new Wishlist();
        ReflectionTestUtils.setField(wishlist, "id", id);
        wishlist.setUser(user);
        wishlist.setProduct(product);
        return wishlist;
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

    private Product product(Long id) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setCategoryId(1L);
        product.setBrandId(1L);
        product.setName("Product " + id);
        product.setSlug("product-" + id);
        product.setStatus("ACTIVE");
        return product;
    }
}
