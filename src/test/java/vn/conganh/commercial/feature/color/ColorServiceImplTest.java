package vn.conganh.commercial.feature.color;

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
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.color.dto.ColorResponse;
import vn.conganh.commercial.feature.color.dto.CreateColorRequest;
import vn.conganh.commercial.feature.color.dto.UpdateColorRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Color - ColorServiceImpl")
class ColorServiceImplTest {

    @Mock
    private ColorRepository colorRepository;

    private ColorServiceImpl colorService;

    @BeforeEach
    void setUp() {
        colorService = new ColorServiceImpl(colorRepository);
    }

    @Nested
    @DisplayName("Create color")
    class CreateColor {

        @Test
        @DisplayName("create - tạo color thành công khi name chưa tồn tại")
        void create_validRequest_returnsColorResponse() {
            // Arrange
            CreateColorRequest request = new CreateColorRequest("Black", "#000000", 1);
            when(colorRepository.existsByName("Black")).thenReturn(false);
            when(colorRepository.save(any(Color.class))).thenAnswer(invocation -> {
                Color color = invocation.getArgument(0);
                ReflectionTestUtils.setField(color, "id", 1L);
                return color;
            });

            // Act
            ColorResponse response = colorService.create(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Black");
            assertThat(response.hexCode()).isEqualTo("#000000");
        }

        @Test
        @DisplayName("create - không gọi save khi name đã tồn tại")
        void create_duplicateName_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateColorRequest request = new CreateColorRequest("Black", "#000000", 1);
            when(colorRepository.existsByName("Black")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> colorService.create(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(colorRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read color")
    class ReadColor {

        @Test
        @DisplayName("getAll - trả về danh sách color")
        void getAll_existingColors_returnsResponses() {
            // Arrange
            when(colorRepository.findAll()).thenReturn(List.of(color(1L, "Black")));

            // Act
            List<ColorResponse> responses = colorService.getAll();

            // Assert
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).name()).isEqualTo("Black");
        }

        @Test
        @DisplayName("getById - ném ResourceNotFoundException khi không tìm thấy color")
        void getById_missingColor_throwsResourceNotFoundException() {
            // Arrange
            when(colorRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> colorService.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update color")
    class UpdateColor {

        @Test
        @DisplayName("update - cập nhật color thành công khi id tồn tại")
        void update_existingColor_returnsUpdatedResponse() {
            // Arrange
            Color color = color(1L, "Black");
            UpdateColorRequest request = new UpdateColorRequest("White", "#ffffff", 2);
            when(colorRepository.findById(1L)).thenReturn(Optional.of(color));
            when(colorRepository.existsByName("White")).thenReturn(false);
            when(colorRepository.save(any(Color.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ColorResponse response = colorService.update(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("White");
            assertThat(response.hexCode()).isEqualTo("#ffffff");
        }
    }

    private Color color(Long id, String name) {
        Color color = new Color();
        ReflectionTestUtils.setField(color, "id", id);
        color.setName(name);
        color.setHexCode("#000000");
        color.setSortOrder(1);
        return color;
    }
}
