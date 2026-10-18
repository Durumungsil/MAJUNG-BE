package durumungsil.majung.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    // 그룹
    INVALID_GROUP_CREATE(HttpStatus.BAD_REQUEST, "그룹명, 지역, 인원 제한 또는 여행 기간이 올바르지 않습니다."),
    INVALID_GROUP_SETTING(HttpStatus.BAD_REQUEST, "여행 기간 또는 인원 제한이 올바르지 않습니다."),
    NEW_OWNER_NOT_MEMBER(HttpStatus.BAD_REQUEST, "대표자로 지정할 사용자는 그룹 멤버여야 합니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 참여하지 않은 그룹입니다."),
    NOT_OWNER_UPDATE_SETTING(HttpStatus.FORBIDDEN, "그룹 대표자만 그룹 설정을 수정할 수 있습니다."),
    NOT_OWNER_VIEW_INVITE_CODE(HttpStatus.FORBIDDEN, "그룹 코드는 방장만 확인할 수 있습니다."),
    NOT_OWNER_SELECT_MODE(HttpStatus.FORBIDDEN, "그룹 대표자만 모드를 확정할 수 있습니다."),
    NOT_OWNER_CREATE_TRIP(HttpStatus.FORBIDDEN, "그룹 대표자만 여행을 만들 수 있습니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "그룹 대표자는 대표자 변경 후 탈퇴할 수 있습니다."),

    // 초대
    INVALID_INVITE_CODE_FORMAT(HttpStatus.BAD_REQUEST, "초대 코드 형식이 올바르지 않습니다."),
    INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 만료된 초대 코드입니다."),
    GROUP_FULL(HttpStatus.FORBIDDEN, "그룹 인원이 가득 찼습니다."),
    ALREADY_JOINED_OR_FULL(HttpStatus.CONFLICT, "이미 참여한 그룹이거나 그룹 정원이 가득 찼습니다."),
    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "초대 코드 생성에 실패했습니다."),

    // 여행 회차
    INVALID_TRIP(HttpStatus.BAD_REQUEST, "지역 또는 여행 기간이 올바르지 않습니다."),
    UNSUPPORTED_MODE(HttpStatus.BAD_REQUEST, "지원하지 않는 모드입니다."),
    ACTIVE_TRIP_EXISTS(HttpStatus.CONFLICT, "아직 끝나지 않은 여행이 있습니다."),
    NO_ACTIVE_TRIP(HttpStatus.CONFLICT, "진행 중인 여행이 없습니다."),

    // 취향
    INVALID_PREFERENCE(HttpStatus.BAD_REQUEST, "알 수 없는 카테고리이거나 예산 범위가 올바르지 않습니다."),
    PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 입력한 취향 정보가 없습니다."),
    NO_PREFERENCE_TO_SUMMARIZE(HttpStatus.CONFLICT, "취향을 입력한 멤버가 없어 통합할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
