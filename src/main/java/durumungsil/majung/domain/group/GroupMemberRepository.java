package durumungsil.majung.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    long countByGroupId(Long groupId);
}
