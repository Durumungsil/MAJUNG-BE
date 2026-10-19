package durumungsil.majung.repository;

import durumungsil.majung.domain.group.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /** 내가 속한 그룹 목록 (그룹 최신순) */
    @Query("""
            select gm from GroupMember gm
            join fetch gm.group g
            where gm.user.id = :userId
            order by g.createdAt desc, g.id desc
            """)
    List<GroupMember> findAllByUserIdWithGroup(@Param("userId") Long userId);

    /** 그룹 멤버 목록 (참여 순) */
    @Query("""
            select gm from GroupMember gm
            join fetch gm.user
            where gm.group.id = :groupId
            order by gm.joinedAt asc, gm.id asc
            """)
    List<GroupMember> findAllByGroupIdWithUser(@Param("groupId") Long groupId);
}
