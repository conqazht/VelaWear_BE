package vn.conganh.commercial.feature.useraddress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserAddressRequest(

        @NotBlank(message = "Receiver name is required")
        @Size(max = 150, message = "Receiver name must be at most 150 characters")
        String receiverName,

        @NotBlank(message = "Phone is required")
        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone,

        @NotBlank(message = "Province is required")
        @Size(max = 100, message = "Province must be at most 100 characters")
        String province,

        @NotBlank(message = "District is required")
        @Size(max = 100, message = "District must be at most 100 characters")
        String district,

        @NotBlank(message = "Ward is required")
        @Size(max = 100, message = "Ward must be at most 100 characters")
        String ward,

        @NotBlank(message = "Address detail is required")
        @Size(max = 255, message = "Address detail must be at most 255 characters")
        String addressDetail,

        boolean isDefault
) {
}
