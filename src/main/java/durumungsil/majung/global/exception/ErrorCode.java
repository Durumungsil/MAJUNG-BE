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
    PLACES_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "장소 검색 API가 설정되지 않았습니다."),
    PLACES_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 검색 서버와 통신하지 못했습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해 주세요."),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "구글 인증 정보가 유효하지 않습니다."),
    GOOGLE_AUTH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "구글 인증 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

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
    INVALID_PREFERENCE(HttpStatus.BAD_REQUEST, "알 수 없는 카테고리이거나 알레르기·예산 값이 올바르지 않습니다."),
    PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "아직 입력한 취향 정보가 없습니다."),
    NO_PREFERENCE_TO_SUMMARIZE(HttpStatus.CONFLICT, "취향을 입력한 멤버가 없어 통합할 수 없습니다."),

    // 익스트림모드
    NOT_EXTREME_MODE(HttpStatus.CONFLICT, "익스트림모드로 확정된 여행이 아닙니다."),
    MINIMUM_CONSTRAINTS_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹 취향 정보가 아직 없습니다."),
    NO_ROULETTE_DESTINATION(HttpStatus.CONFLICT, "조건에 맞는 여행지가 없어 추첨할 수 없습니다."),
    NO_ROULETTE_RESTAURANT(HttpStatus.CONFLICT, "조건에 맞는 음식점이 없어 추첨할 수 없습니다."),
    ROULETTE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "룰렛 결과를 찾을 수 없습니다."),
    INVALID_ROULETTE_REGION(HttpStatus.BAD_REQUEST, "요청 지역이 현재 여행 지역과 일치하지 않습니다."),

    // 스토리모드 - 여행지
    NOT_STORY_MODE(HttpStatus.CONFLICT, "스토리모드로 확정된 여행이 아닙니다."),
    PREFERENCE_NOT_MERGED(HttpStatus.CONFLICT, "그룹 취향이 통합되지 않아 추천할 수 없습니다."),
    INVALID_RECOMMENDATION_QUERY(HttpStatus.BAD_REQUEST, "추천 조건(개수, 카테고리, 정렬, 반경)이 올바르지 않습니다."),
    BASE_DESTINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "기준 여행지를 찾을 수 없습니다."),
    INVALID_DESTINATION_SELECTION(HttpStatus.BAD_REQUEST, "방문 순서가 중복되거나 여행 기간을 벗어난 장소가 포함되었습니다."),
    DESTINATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 여행지가 포함되었습니다."),

    // 스토리모드 - 타임라인
    INVALID_TIMELINE_REQUEST(HttpStatus.BAD_REQUEST, "여행 기간, 교통수단 또는 하루 일정 시간이 올바르지 않습니다."),
    NO_DESTINATION_FOR_TIMELINE(HttpStatus.BAD_REQUEST, "타임라인을 만들 여행지가 없습니다."),
    TIMELINE_UNSCHEDULABLE(HttpStatus.CONFLICT, "선택한 장소의 영업 시간 또는 이동 시간 때문에 일정을 생성할 수 없습니다."),
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 타임라인을 찾을 수 없습니다."),
    ITINERARY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "타임라인 항목을 찾을 수 없습니다."),
    INVALID_ITINERARY_ITEM_SCHEDULE(HttpStatus.BAD_REQUEST, "종료 시간이 시작 시간보다 빠르거나 다른 일정과 겹칩니다."),
    MOVE_ITEM_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "이동 항목은 직접 수정할 수 없습니다."),

    // 모험모드
    ADVENTURE_CONDITION_UNSATISFIED(HttpStatus.CONFLICT, "그룹 최소 조건을 만족하는 커리큘럼을 생성할 수 없습니다."),
    ADVENTURE_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 날짜의 커리큘럼이 이미 있습니다."),
    ADVENTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "커리큘럼을 찾을 수 없습니다."),
    ADVENTURE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."),
    ADVENTURE_ACTIVE_TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 중인 여행 회차를 찾을 수 없습니다."),
    MULTIPLE_ACTIVE_TRIPS(HttpStatus.CONFLICT, "진행 중인 여행 회차가 여러 개입니다."),
    NOT_ADVENTURE_MODE(HttpStatus.CONFLICT, "모험모드 여행이 아닙니다."),
    CURRENT_MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "진행할 수 있는 현재 미션이 없습니다."),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다."),
    MISSION_NOT_TODAY(HttpStatus.CONFLICT, "오늘 진행할 수 있는 미션이 아닙니다."),
    MISSION_NOT_STARTED(HttpStatus.CONFLICT, "시작하지 않은 미션입니다."),
    MISSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 미션입니다."),
    MISSION_LOCKED(HttpStatus.CONFLICT, "아직 진행할 수 없는 미션입니다.");

    private final HttpStatus status;
    private final String message;
}
