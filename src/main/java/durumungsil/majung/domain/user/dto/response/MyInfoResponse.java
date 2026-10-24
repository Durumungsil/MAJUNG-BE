package durumungsil.majung.domain.user.dto.response;

import durumungsil.majung.domain.user.entity.User;

import java.time.LocalDateTime;

public record MyInfoResponse(
        Long userId,
        String email,
        String userName,
        LocalDateTime createdAt
) {
    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(user.getId(), user.getEmail(), user.getUserName(), user.getCreatedAt());
    }
}
