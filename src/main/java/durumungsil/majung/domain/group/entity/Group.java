package durumungsil.majung.domain.group.entity;

import durumungsil.majung.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 그룹 = 함께 여행하는 멤버 구성. 여행마다 달라지는 값(지역·모드·기간 등)은 Trip 에 둔다.
 */
@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

    /** 초대 코드 유효 기간 */
    public static final Duration INVITE_CODE_VALID_DURATION = Duration.ofDays(1);

    /** 그룹 인원 제한 범위 */
    public static final int MIN_MEMBERS = 2;
    public static final int MAX_MEMBERS = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "member_count")
    private Integer memberCount;

    @Column(name = "invite_code", unique = true)
    private String inviteCode;

    /** 초대 코드 만료 시각. 발급 시점부터 하루 동안 유효 */
    @Column(name = "invite_code_expires_at")
    private LocalDateTime inviteCodeExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Group(String groupName, Integer maxMembers, Integer memberCount, String inviteCode, User owner) {
        this.groupName = groupName;
        this.maxMembers = maxMembers;
        this.memberCount = memberCount;
        if (inviteCode != null) {
            issueInviteCode(inviteCode, LocalDateTime.now());
        }
        this.owner = owner;
    }

    /** 초대 코드를 (재)발급하고 만료 시각을 지금부터 하루 뒤로 설정 */
    public void issueInviteCode(String inviteCode, LocalDateTime now) {
        this.inviteCode = inviteCode;
        this.inviteCodeExpiresAt = now.plus(INVITE_CODE_VALID_DURATION);
    }

    public boolean isInviteCodeExpired(LocalDateTime now) {
        return inviteCodeExpiresAt == null || !now.isBefore(inviteCodeExpiresAt);
    }

    /**
     * 초대 코드 조회 시 호출. 코드가 만료됐으면 새 코드로 재발급한다.
     * @param codeGenerator 새 초대 코드를 만드는 함수 (중복 검사는 호출하는 쪽 책임)
     * @return 재발급했으면 true
     */
    public boolean reissueInviteCodeIfExpired(Supplier<String> codeGenerator, LocalDateTime now) {
        if (!isInviteCodeExpired(now)) {
            return false;
        }
        issueInviteCode(codeGenerator.get(), now);
        return true;
    }

    /** 초대 코드가 이 그룹의 코드와 같고 아직 유효한지 */
    public boolean isValidInviteCode(String inviteCode, LocalDateTime now) {
        return this.inviteCode != null && this.inviteCode.equals(inviteCode) && !isInviteCodeExpired(now);
    }

    public boolean isFull() {
        return memberCount != null && maxMembers != null && memberCount >= maxMembers;
    }

    /** 멤버 참여 시 인원 증가. 멤버 행(GroupMember) 저장은 서비스에서 함께 한다 */
    public void increaseMemberCount() {
        if (isFull()) {
            throw new IllegalStateException("그룹 인원이 가득 찼습니다.");
        }
        this.memberCount = memberCount + 1;
    }

    /** 멤버 탈퇴 시 인원 감소 */
    public void decreaseMemberCount() {
        if (memberCount == null || memberCount <= 0) {
            throw new IllegalStateException("그룹 인원이 올바르지 않습니다.");
        }
        this.memberCount = memberCount - 1;
    }

    public void changeGroupName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("그룹명은 비어 있을 수 없습니다.");
        }
        this.groupName = groupName;
    }

    /** 인원 제한 변경. 2~16명이고 현재 인원보다 작을 수 없다 */
    public void changeMaxMembers(int maxMembers) {
        if (!isValidMaxMembers(maxMembers) || (memberCount != null && maxMembers < memberCount)) {
            throw new IllegalArgumentException("여행 기간 또는 인원 제한이 올바르지 않습니다.");
        }
        this.maxMembers = maxMembers;
    }

    public static boolean isValidMaxMembers(Integer maxMembers) {
        return maxMembers != null && maxMembers >= MIN_MEMBERS && maxMembers <= MAX_MEMBERS;
    }

    public boolean isOwner(Long userId) {
        return owner != null && owner.getId().equals(userId);
    }

    /** 방장 변경. 새 방장이 이 그룹의 멤버인지는 호출하는 쪽(서비스)에서 확인한다 */
    public void changeOwner(User newOwner) {
        if (newOwner == null) {
            throw new IllegalArgumentException("새 방장은 필수입니다.");
        }
        this.owner = newOwner;
    }
}
