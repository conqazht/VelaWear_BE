package vn.conganh.commercial.feature.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Product - ProductServiceImpl")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository);
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
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                ReflectionTestUtils.setField(product, "id", 1L);
                return product;
            });

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

            // Act
            ProductResponse response = productService.updateProduct(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("New");
            assertThat(response.slug()).isEqualTo("old");
            assertThat(response.status()).isEqualTo("INACTIVE");
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
}
