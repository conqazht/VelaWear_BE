package vn.conganh.commercial.feature.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.cart.dto.CartResponse;
import vn.conganh.commercial.feature.cart.dto.CreateCartRequest;
import vn.conganh.commercial.feature.cart.dto.ReplaceCartItemsRequest;
import vn.conganh.commercial.feature.product.ProductImageRepository;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;
import vn.conganh.commercial.feature.catalog.i18n.CatalogContentLocalizationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Cart - CartServiceImpl")
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private VariantPricingService variantPricingService;

    @Mock
    private CatalogContentLocalizationService localizationService;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(
                cartRepository,
                cartItemRepository,
                userRepository,
                productVariantRepository,
                productImageRepository,
                variantPricingService,
                localizationService);
    }

    @Test
    @DisplayName("replaceMyCartItems - khóa cùng cart row với checkout")
    void replaceMyCartItems_usesCheckoutCartLock() {
        User user = user(1L);
        Cart cart = cart(10L, user);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(10L)).thenReturn(List.of());
        when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of())).thenReturn(List.of());

        cartService.replaceMyCartItems("test@example.com", new ReplaceCartItemsRequest(List.of()));

        verify(cartRepository).findWithLockByUserId(1L);
    }

    @Nested
    @DisplayName("Create cart")
    class CreateCart {

        @Test
        @DisplayName("createCart - tạo cart thành công khi user chưa có cart")
        void createCart_validRequest_returnsCartResponse() {
            // Arrange
            User user = user(1L);
            CreateCartRequest request = new CreateCartRequest(1L);
            when(cartRepository.existsByUserId(1L)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
                Cart cart = invocation.getArgument(0);
                ReflectionTestUtils.setField(cart, "id", 10L);
                return cart;
            });

            // Act
            CartResponse response = cartService.createCart(request);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.userEmail()).isEqualTo("user1@example.com");
            verify(cartRepository).save(argThat(cart -> cart.getUser().equals(user)));
        }

        @Test
        @DisplayName("createCart - không gọi save khi user đã có cart")
        void createCart_existingCart_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateCartRequest request = new CreateCartRequest(1L);
            when(cartRepository.existsByUserId(1L)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> cartService.createCart(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Cart already exists");
            verify(userRepository, never()).findById(any());
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("createCart - không gọi save khi user không tồn tại")
        void createCart_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
            // Arrange
            CreateCartRequest request = new CreateCartRequest(99L);
            when(cartRepository.existsByUserId(99L)).thenReturn(false);
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.createCart(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(cartRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read cart")
    class ReadCart {

        @Test
        @DisplayName("getAllCarts - trả về danh sách cart")
        void getAllCarts_existingCarts_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(cartRepository.findAll(ArgumentMatchers.<Specification<Cart>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(cart(10L, user(1L))), pageable, 1));

            // Act
            ResultPaginationDTO responses = cartService.getAllCarts(null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("userId").containsExactly(1L);
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getCartById - ném ResourceNotFoundException khi không tìm thấy cart")
        void getCartById_missingCart_throwsResourceNotFoundException() {
            // Arrange
            when(cartRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> cartService.getCartById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getCartByUserId - trả về cart theo user id")
        void getCartByUserId_existingCart_returnsCartResponse() {
            // Arrange
            when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart(10L, user(1L))));

            // Act
            CartResponse response = cartService.getCartByUserId(1L);

            // Assert
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("getCartById - localize product và pricing campaign theo locale")
        void getCartById_englishLocale_returnsLocalizedContent() {
            User user = user(1L);
            Cart cart = cart(10L, user);
            CartItem item = new CartItem();
            ReflectionTestUtils.setField(item, "id", 20L);
            item.setCart(cart);
            item.setVariantId(30L);
            item.setQuantity(1);
            Product product = new Product();
            ReflectionTestUtils.setField(product, "id", 40L);
            product.setName("Tên Việt");
            product.setSlug("ten-viet");
            ProductVariant variant = new ProductVariant();
            ReflectionTestUtils.setField(variant, "id", 30L);
            variant.setProduct(product);
            variant.setSku("SKU");
            variant.setPrice(BigDecimal.valueOf(100));
            variant.setStockQuantity(2);
            SaleCampaign campaign = new SaleCampaign();
            ReflectionTestUtils.setField(campaign, "id", 50L);
            campaign.setName("Khuyến mãi");
            SaleCampaignItem campaignItem = new SaleCampaignItem();
            campaignItem.setCampaign(campaign);
            campaignItem.setVariant(variant);
            VariantPricing pricing = new VariantPricing(
                    30L, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                    PriceSource.STANDARD_SALE, campaignItem, null, null, 2);
            when(cartRepository.findById(10L)).thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartId(10L)).thenReturn(List.of(item));
            when(productVariantRepository.findAllByIdInAndDeletedAtIsNull(List.of(30L)))
                    .thenReturn(List.of(variant));
            when(variantPricingService.resolve(any(), any(), eq(1L))).thenReturn(Map.of(30L, pricing));
            when(localizationService.localizeProducts(any(), eq("en"))).thenReturn(Map.of(
                    40L,
                    new CatalogContentLocalizationService.LocalizedProduct("English product", "english-product")));
            when(localizationService.localizeCampaignNames(any(), eq("en")))
                    .thenReturn(Map.of(50L, "English sale"));
            when(productImageRepository.findByProductIdIn(List.of(40L))).thenReturn(List.of());

            CartResponse response = cartService.getCartById(10L, "en");

            assertThat(response.items()).singleElement().satisfies(responseItem -> {
                assertThat(responseItem.productName()).isEqualTo("English product");
                assertThat(responseItem.productSlug()).isEqualTo("english-product");
                assertThat(responseItem.pricing().campaignName()).isEqualTo("English sale");
            });
        }
    }

    @Nested
    @DisplayName("Delete cart")
    class DeleteCart {

        @Test
        @DisplayName("deleteCart - xóa cart khi id tồn tại")
        void deleteCart_existingCart_deletesCart() {
            // Arrange
            Cart cart = cart(10L, user(1L));
            when(cartRepository.findWithLockById(10L)).thenReturn(Optional.of(cart));

            // Act
            cartService.deleteCart(10L);

            // Assert
            verify(cartRepository).delete(cart);
        }
    }

    private Cart cart(Long id, User user) {
        Cart cart = new Cart();
        ReflectionTestUtils.setField(cart, "id", id);
        cart.setUser(user);
        return cart;
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
