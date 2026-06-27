package vn.conganh.commercial.feature.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.brand.dto.BrandResponse;
import vn.conganh.commercial.feature.brand.dto.CreateBrandRequest;
import vn.conganh.commercial.feature.brand.dto.UpdateBrandRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Brand - BrandServiceImpl")
class BrandServiceImplTest {

    @Mock
    private BrandRepository brandRepository;

    private BrandServiceImpl brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandServiceImpl(brandRepository);
    }

    @Nested
    @DisplayName("Create brand")
    class CreateBrand {

        @Test
        @DisplayName("create - tạo brand thành công khi slug chưa tồn tại")
        void create_validRequest_returnsBrandResponse() {
            // Arrange
            CreateBrandRequest request = new CreateBrandRequest("Nike", "nike", "desc", null);
            when(brandRepository.existsBySlug("nike")).thenReturn(false);
            when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> {
                Brand brand = invocation.getArgument(0);
                ReflectionTestUtils.setField(brand, "id", 1L);
                return brand;
            });

            // Act
            BrandResponse response = brandService.create(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Nike");
            assertThat(response.slug()).isEqualTo("nike");
            assertThat(response.status()).isEqualTo("ACTIVE");
            verify(brandRepository).save(any(Brand.class));
        }

        @Test
        @DisplayName("create - ném InvalidRequestException khi slug đã tồn tại")
        void create_duplicateSlug_throwsInvalidRequestException() {
            // Arrange
            CreateBrandRequest request = new CreateBrandRequest("Nike", "nike", null, "ACTIVE");
            when(brandRepository.existsBySlug("nike")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> brandService.create(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Brand slug already exists");
            verify(brandRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read brand")
    class ReadBrand {

        @Test
        @DisplayName("getAll - trả về danh sách brand chưa bị xóa")
        void getAll_existingBrands_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(brandRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(brand(1L, "Nike", "nike")), pageable, 1));

            // Act
            ResultPaginationDTO responses = brandService.getAll(null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("slug").containsExactly("nike");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getById - ném ResourceNotFoundException khi không tìm thấy brand")
        void getById_missingBrand_throwsResourceNotFoundException() {
            // Arrange
            when(brandRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> brandService.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update brand")
    class UpdateBrand {

        @Test
        @DisplayName("update - cập nhật brand thành công khi id tồn tại")
        void update_existingBrand_returnsUpdatedResponse() {
            // Arrange
            Brand brand = brand(1L, "Old", "old");
            UpdateBrandRequest request = new UpdateBrandRequest("New", "new desc", "INACTIVE");
            when(brandRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(brand));
            when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            BrandResponse response = brandService.update(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("New");
            assertThat(response.description()).isEqualTo("new desc");
            assertThat(response.status()).isEqualTo("INACTIVE");
        }
    }

    @Nested
    @DisplayName("Delete brand")
    class DeleteBrand {

        @Test
        @DisplayName("delete - xóa mềm brand khi id tồn tại")
        void delete_existingBrand_savesDeletedAt() {
            // Arrange
            Brand brand = brand(1L, "Nike", "nike");
            when(brandRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(brand));

            // Act
            brandService.delete(1L);

            // Assert
            assertThat(brand.getDeletedAt()).isNotNull();
            verify(brandRepository).save(brand);
        }
    }

    private Brand brand(Long id, String name, String slug) {
        Brand brand = new Brand();
        ReflectionTestUtils.setField(brand, "id", id);
        brand.setName(name);
        brand.setSlug(slug);
        brand.setStatus("ACTIVE");
        return brand;
    }
}
