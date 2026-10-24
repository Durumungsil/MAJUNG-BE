package durumungsil.majung.domain.auth.repository;

import durumungsil.majung.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 삭제된 행 수를 돌려준다 - 같은 토큰으로 동시에 재발급 요청이 와도 한 번만 성공시키기 위해 사용 */
    @Modifying
    @Query("delete from RefreshToken r where r.id = :id")
    int revoke(@Param("id") Long id);

    /** 로그아웃 - 본인 토큰만 지운다 */
    @Modifying
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash and r.user.id = :userId")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("userId") Long userId);

    /** 로그인할 때 만료된 토큰 정리 */
    @Modifying
    @Query("delete from RefreshToken r where r.user.id = :userId and r.expiresAt <= :now")
    int deleteExpired(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
