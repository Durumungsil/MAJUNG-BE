package durumungsil.majung.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository("adventureGroupMemberRepository")
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    long countByGroupId(Long groupId);
}
