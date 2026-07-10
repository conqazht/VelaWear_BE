package vn.conganh.commercial.feature.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Product - ProductServiceImpl")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductTranslationRepository productTranslationRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryTranslationRepository categoryTranslationRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productRepository,
                productTranslationRepository,
                productImageRepository,
                categoryRepository,
                categoryTranslationRepository,
                productVariantRepository);
        lenient().when(productVariantRepository.findRepresentativePricesByProductIds(any())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Create product")
    class CreateProduct {

        @Test
        @DisplayName("createProduct - tạo product thành công khi slug chưa tồn tại")
        void createProduct_validRequest_returnsProductResponse() {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(1L, 2L, "Sneaker", "sneaker", "desc", "ACTIVE");
            when(productRepository.existsBySlug("sneaker")).thenReturn(false);
            when(productTranslationRepository.existsByLocaleCodeAndSlug("vi", "sneaker")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                ReflectionTestUtils.setField(product, "id", 1L);
                return product;
            });
            when(productTranslationRepository.findByProductIdAndLocaleCode(1L, "vi")).thenReturn(Optional.empty());
            when(productTranslationRepository.save(any(ProductTranslation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ProductResponse response = productService.createProduct(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.slug()).isEqualTo("sneaker");
            assertThat(response.categoryId()).isEqualTo(1L);
            assertThat(response.brandId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("createProduct - không gọi save khi slug đã tồn tại")
        void createProduct_duplicateSlug_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(1L, 2L, "Sneaker", "sneaker", null, "ACTIVE");
            when(productRepository.existsBySlug("sneaker")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("createProduct - không gọi save khi slug bản dịch vi đã tồn tại")
        void createProduct_duplicateVietnameseTranslationSlug_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateProductRequest request = new CreateProductRequest(1L, 2L, "Sneaker", "sneaker", null, "ACTIVE");
            when(productRepository.existsBySlug("sneaker")).thenReturn(false);
            when(productTranslationRepository.existsByLocaleCodeAndSlug("vi", "sneaker")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update product")
    class UpdateProduct {

        @Test
        @DisplayName("updateProduct - cập nhật product thành công khi id tồn tại")
        void updateProduct_existingProduct_returnsUpdatedResponse() {
            // Arrange
            Product product = product(1L, "Old", "old");
            UpdateProductRequest request = new UpdateProductRequest(3L, 4L, "New", "new desc", "INACTIVE");
            when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(productTranslationRepository.findByProductIdAndLocaleCode(1L, "vi")).thenReturn(Optional.empty());
            when(productTranslationRepository.save(any(ProductTranslation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ProductResponse response = productService.updateProduct(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("New");
            assertThat(response.slug()).isEqualTo("old");
            assertThat(response.status()).isEqualTo("INACTIVE");
        }
    }

    @Nested
    @DisplayName("Read localized product")
    class ReadLocalizedProduct {

        @Test
        @DisplayName("getProductById - fallback về bản dịch vi khi locale yêu cầu bị thiếu")
        void getProductById_missingRequestedTranslation_fallsBackToVietnamese() {
            // Arrange
            Product product = product(1L, "Core name", "core-slug");
            ProductTranslation vi = productTranslation(1L, "vi", "Tên tiếng Việt", "ten-tieng-viet");
            when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
            when(productTranslationRepository.findByProductIdAndLocaleCode(1L, "en")).thenReturn(Optional.empty());
            when(productTranslationRepository.findByProductIdAndLocaleCode(1L, "vi")).thenReturn(Optional.of(vi));

            // Act
            ProductResponse response = productService.getProductById(1L, "en");

            // Assert
            assertThat(response.name()).isEqualTo("Tên tiếng Việt");
            assertThat(response.slug()).isEqualTo("ten-tieng-viet");
        }

        @Test
        @DisplayName("getProductBySlug - tìm theo slug bản dịch")
        void getProductBySlug_localizedSlug_returnsLocalizedResponse() {
            // Arrange
            Product product = product(1L, "Core name", "core-slug");
            ProductTranslation vi = productTranslation(1L, "vi", "Tên tiếng Việt", "ten-tieng-viet");
            when(productTranslationRepository.findByLocaleCodeAndSlug("vi", "ten-tieng-viet"))
                    .thenReturn(Optional.of(vi));
            when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
            when(productTranslationRepository.findByProductIdAndLocaleCode(1L, "vi")).thenReturn(Optional.of(vi));

            // Act
            ProductResponse response = productService.getProductBySlug("ten-tieng-viet", "vi");

            // Assert
            assertThat(response.name()).isEqualTo("Tên tiếng Việt");
            assertThat(response.slug()).isEqualTo("ten-tieng-viet");
        }
    }

    @Nested
    @DisplayName("Delete product")
    class DeleteProduct {

        @Test
        @DisplayName("deleteProduct - xóa mềm product và chuyển status INACTIVE")
        void deleteProduct_existingProduct_setsInactiveAndDeletedAt() {
            // Arrange
            Product product = product(1L, "Sneaker", "sneaker");
            when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));

            // Act
            productService.deleteProduct(1L);

            // Assert
            assertThat(product.getStatus()).isEqualTo("INACTIVE");
            assertThat(product.getDeletedAt()).isNotNull();
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("getProductById - ném ResourceNotFoundException khi không tìm thấy product")
        void getProductById_missingProduct_throwsResourceNotFoundException() {
            // Arrange
            when(productRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.getProductById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private Product product(Long id, String name, String slug) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setCategoryId(1L);
        product.setBrandId(2L);
        product.setName(name);
        product.setSlug(slug);
        product.setStatus("ACTIVE");
        return product;
    }

    private ProductTranslation productTranslation(Long productId, String localeCode, String name, String slug) {
        ProductTranslation translation = new ProductTranslation();
        translation.setProductId(productId);
        translation.setLocaleCode(localeCode);
        translation.setName(name);
        translation.setSlug(slug);
        translation.setShortDescription("Mô tả ngắn");
        translation.setDescription("Mô tả đầy đủ");
        translation.setSeoTitle(name);
        translation.setSeoDescription("SEO");
        return translation;
    }
}
