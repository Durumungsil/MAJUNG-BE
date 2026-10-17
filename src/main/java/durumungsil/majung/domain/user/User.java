package durumungsil.majung.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 유저 - 구글 로그인 / 프로필 조회
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 구글 idToken 의 sub 클레임. 구글 계정마다 고유하고 바뀌지 않으므로 로그인 시 유저 식별 키로 사용 */
    @Column(name = "google_sub", nullable = false, unique = true, updatable = false)
    private String googleSub;

    /** 구글 idToken 의 email 클레임. 식별 용도가 아니라 참고용(이메일은 바뀔 수 있음) */
    private String email;

    @Column(name = "user_name")
    private String userName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private User(String googleSub, String email, String userName) {
        this.googleSub = googleSub;
        this.email = email;
        this.userName = userName;
    }
}
