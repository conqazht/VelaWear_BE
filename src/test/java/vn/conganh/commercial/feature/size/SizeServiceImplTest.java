package vn.conganh.commercial.feature.size;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Size - SizeServiceImpl")
class SizeServiceImplTest {

    @Mock
    private SizeRepository sizeRepository;

    private SizeServiceImpl sizeService;

    @BeforeEach
    void setUp() {
        sizeService = new SizeServiceImpl(sizeRepository);
    }

    @Nested
    @DisplayName("Create size")
    class CreateSize {

        @Test
        @DisplayName("create - tạo size thành công khi name chưa tồn tại")
        void create_validRequest_returnsSizeResponse() {
            // Arrange
            CreateSizeRequest request = new CreateSizeRequest("XL", 1);
            when(sizeRepository.existsByName("XL")).thenReturn(false);
            when(sizeRepository.save(any(Size.class))).thenAnswer(invocation -> {
                Size size = invocation.getArgument(0);
                ReflectionTestUtils.setField(size, "id", 1L);
                return size;
            });

            // Act
            SizeResponse response = sizeService.create(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("XL");
        }

        @Test
        @DisplayName("create - không gọi save khi name đã tồn tại")
        void create_duplicateName_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateSizeRequest request = new CreateSizeRequest("XL", 1);
            when(sizeRepository.existsByName("XL")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> sizeService.create(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(sizeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read size")
    class ReadSize {

        @Test
        @DisplayName("getAll - trả về danh sách size")
        void getAll_existingSizes_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(sizeRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(size(1L, "XL")), pageable, 1));

            // Act
            ResultPaginationDTO responses = sizeService.getAll(pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("name").containsExactly("XL");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getById - ném ResourceNotFoundException khi không tìm thấy size")
        void getById_missingSize_throwsResourceNotFoundException() {
            // Arrange
            when(sizeRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> sizeService.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update size")
    class UpdateSize {

        @Test
        @DisplayName("update - cập nhật size thành công khi id tồn tại")
        void update_existingSize_returnsUpdatedResponse() {
            // Arrange
            Size size = size(1L, "L");
            UpdateSizeRequest request = new UpdateSizeRequest("XL", 2);
            when(sizeRepository.findById(1L)).thenReturn(Optional.of(size));
            when(sizeRepository.existsByName("XL")).thenReturn(false);
            when(sizeRepository.save(any(Size.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            SizeResponse response = sizeService.update(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("XL");
            assertThat(response.sortOrder()).isEqualTo(2);
        }
    }

    private Size size(Long id, String name) {
        Size size = new Size();
        ReflectionTestUtils.setField(size, "id", id);
        size.setName(name);
        size.setSortOrder(1);
        return size;
    }
}
