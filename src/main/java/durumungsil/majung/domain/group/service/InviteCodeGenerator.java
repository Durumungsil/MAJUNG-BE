package durumungsil.majung.domain.group.service;

import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.domain.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/** 6자리 영문 대소문자+숫자 초대 코드 생성 (예: B2K9QF, Dk9Vf0) */
@Component
@RequiredArgsConstructor
public class InviteCodeGenerator {

    public static final int CODE_LENGTH = 6;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{" + CODE_LENGTH + "}$");
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final GroupRepository groupRepository;

    /** 다른 그룹과 겹치지 않는 코드를 만든다 */
    public String generateUnique() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = generate();
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    public static boolean isValidFormat(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    private String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
