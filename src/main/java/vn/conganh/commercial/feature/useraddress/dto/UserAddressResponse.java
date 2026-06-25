package vn.conganh.commercial.feature.useraddress.dto;

import vn.conganh.commercial.feature.useraddress.UserAddress;

public record UserAddressResponse(
        Long id,
        Long userId,
        String receiverName,
        String phone,
        String province,
        String district,
        String ward,
        String addressDetail,
        boolean isDefault
) {

    public static UserAddressResponse fromEntity(UserAddress userAddress) {
        return new UserAddressResponse(
                userAddress.getId(),
                userAddress.getUser().getId(),
                userAddress.getReceiverName(),
                userAddress.getPhone(),
                userAddress.getProvince(),
                userAddress.getDistrict(),
                userAddress.getWard(),
                userAddress.getAddressDetail(),
                userAddress.isDefault());
    }
}
