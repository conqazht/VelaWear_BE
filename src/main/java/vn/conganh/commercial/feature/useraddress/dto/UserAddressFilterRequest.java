package vn.conganh.commercial.feature.useraddress.dto;

public record UserAddressFilterRequest(
        Long userId,
        String receiverName,
        String phone,
        String province,
        String ward,
        Boolean isDefault
) {
    public UserAddressFilterRequest withUserId(Long userId) {
        return new UserAddressFilterRequest(
                userId,
                receiverName,
                phone,
                province,
                ward,
                isDefault);
    }
}
