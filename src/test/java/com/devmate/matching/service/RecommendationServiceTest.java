package com.devmate.matching.service;

import com.devmate.common.enums.*;
import com.devmate.matching.MatchStatus;
import com.devmate.matching.dto.RecommendationCard;
import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import com.devmate.profile.Profile;
import com.devmate.profile.repository.ProfileRepository;
import com.devmate.project.Project;
import com.devmate.project.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recommendation-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        RecommendationService.class,
        MatchingService.class
})
class RecommendationServiceTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EntityManager entityManager;

    private Member viewer;
    private Member author;

    @BeforeEach
    void setUp() {
        viewer = memberRepository.saveAndFlush(
                new Member("viewer@example.com", "test-hash", "조회회원")
        );

        author = memberRepository.saveAndFlush(
                new Member("author@example.com", "test-hash", "작성자")
        );
    }

    @Test
    @DisplayName("프로필이 없으면 모든 탭에서 빈 목록과 작성 안내를 반환한다")
    void missingProfile() {
        saveProject(author, "모집글", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future());

        for (MatchStatus status : MatchStatus.values()) {
            var result = recommendationService.getRecommendations(
                    viewer.getId(), status, 0
            );

            assertThat(result.profileRequired()).isTrue();
            assertThat(result.selectedStatus()).isEqualTo(status);
            assertThat(result.projectPage().getContent()).isEmpty();
        }
    }

    @Test
    @DisplayName("본인 글·마감된 글·기한이 지난 글은 후보에서 제외한다")
    void excludeIneligibleProjects() {
        saveProfile();

        Project included = saveProject(
                author, "추천 대상", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future()
        );

        saveProject(
                viewer, "본인 글", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future()
        );

        saveProject(
                author, "기한 지난 글", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE,
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1)
        );

        Project closed = saveProject(
                author, "직접 마감한 글", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future()
        );

        // 마감 기능은 후속 구현이므로 테스트에서 DB 상태를 설정한다.
        entityManager.createQuery(
                        "update Project p set p.status = :status where p.id = :id"
                )
                .setParameter("status", ProjectStatus.CLOSED)
                .setParameter("id", closed.getId())
                .executeUpdate();

        Long viewerId = viewer.getId();
        Long includedId = included.getId();
        entityManager.clear();

        var result = recommendationService.getRecommendations(
                viewerId, MatchStatus.PASS, 0
        );

        assertThat(result.projectPage().getTotalElements()).isEqualTo(1L);
        assertThat(result.projectPage().getContent())
                .extracting(RecommendationCard::projectId)
                .containsExactly(includedId);
    }

    @Test
    @DisplayName("PASS·FAIL·UNKNOWN을 구분하고 각 상태에 맞는 목록을 반환한다")
    void filterByStatus() {
        saveProfile();

        Project pass = saveProject(
                author, "조건 충족", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future()
        );

        Project fail = saveProject(
                author, "역할 불일치", Role.ANDROID,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future()
        );

        // 프로필에 지역이 없으므로 대면 프로젝트는 UNKNOWN
        Project unknown = saveProject(
                author, "지역 확인 필요", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.OFFLINE, future()
        );

        var passResult = recommendationService.getRecommendations(
                viewer.getId(), MatchStatus.PASS, 0
        );
        var failResult = recommendationService.getRecommendations(
                viewer.getId(), MatchStatus.FAIL, 0
        );
        var unknownResult = recommendationService.getRecommendations(
                viewer.getId(), MatchStatus.UNKNOWN, 0
        );

        assertThat(passResult.profileRequired()).isFalse();
        assertThat(passResult.projectPage().getContent())
                .extracting(RecommendationCard::projectId)
                .containsExactly(pass.getId());
        assertThat(passResult.projectPage().getContent().getFirst()
                .match().score()).isEqualTo(100);

        assertThat(failResult.projectPage().getContent())
                .extracting(RecommendationCard::projectId)
                .containsExactly(fail.getId());
        assertThat(failResult.projectPage().getContent().getFirst()
                .match().score()).isNull();

        assertThat(unknownResult.projectPage().getContent())
                .extracting(RecommendationCard::projectId)
                .containsExactly(unknown.getId());
        assertThat(unknownResult.projectPage().getContent().getFirst()
                .match().score()).isNull();
    }

    @Test
    @DisplayName("오래된 고득점 글도 첫 페이지에 나오도록 전체 후보를 먼저 정렬한다")
    void sortBeforePagination() {
        saveProfile();

        Project highScore = saveProject(
                author, "오래된 100점 글", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future()
        );

        List<Long> lowScoreIds = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            Project project = saveProject(
                    author, "60점 글 " + i, Role.BACKEND,
                    Skill.KOTLIN, MeetingType.ONLINE, future()
            );
            lowScoreIds.add(project.getId());
        }

        // 조회 순서를 시간 흐름에 의존하지 않도록 명시적으로 설정한다.
        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 2, 12, 0);

        entityManager.createQuery(
                        "update Project p set p.createdAt = :time"
                )
                .setParameter("time", sameTime)
                .executeUpdate();

        entityManager.createQuery(
                        "update Project p set p.createdAt = :time where p.id = :id"
                )
                .setParameter("time", sameTime.minusDays(1))
                .setParameter("id", highScore.getId())
                .executeUpdate();

        Long highScoreId = highScore.getId();
        Long viewerId = viewer.getId();
        entityManager.clear();

        Collections.reverse(lowScoreIds);
        List<Long> expected = new ArrayList<>();
        expected.add(highScoreId);
        expected.addAll(lowScoreIds);

        var first = recommendationService.getRecommendations(
                viewerId, MatchStatus.PASS, 0
        ).projectPage();

        var second = recommendationService.getRecommendations(
                viewerId, MatchStatus.PASS, 1
        ).projectPage();

        assertThat(first.getTotalElements()).isEqualTo(12L);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent())
                .extracting(RecommendationCard::projectId)
                .containsExactlyElementsOf(expected.subList(0, 10));
        assertThat(second.getContent())
                .extracting(RecommendationCard::projectId)
                .containsExactlyElementsOf(expected.subList(10, 12));
    }

    @Test
    @DisplayName("FAIL과 UNKNOWN 목록도 생성 일시와 ID 내림차순으로 정렬한다")
    void nonPassOrdering() {
        saveProfile();

        for (MatchStatus status : new MatchStatus[]{
                MatchStatus.FAIL, MatchStatus.UNKNOWN
        }) {
            Role role = status == MatchStatus.FAIL
                    ? Role.ANDROID : Role.BACKEND;
            MeetingType meetingType = status == MatchStatus.UNKNOWN
                    ? MeetingType.OFFLINE : MeetingType.ONLINE;

            Project first = saveProject(
                    author, "첫 번째", role,
                    Skill.SPRING_BOOT, meetingType, future()
            );
            Project second = saveProject(
                    author, "두 번째", role,
                    Skill.SPRING_BOOT, meetingType, future()
            );
            Project third = saveProject(
                    author, "세 번째", role,
                    Skill.SPRING_BOOT, meetingType, future()
            );

            LocalDateTime newer = LocalDateTime.of(2026, 1, 2, 12, 0);

            entityManager.createQuery(
                            "update Project p set p.createdAt = :time "
                                    + "where p.id in :ids"
                    )
                    .setParameter("time", newer)
                    .setParameter("ids", List.of(first.getId(), second.getId()))
                    .executeUpdate();

            entityManager.createQuery(
                            "update Project p set p.createdAt = :time where p.id = :id"
                    )
                    .setParameter("time", newer.minusDays(1))
                    .setParameter("id", third.getId())
                    .executeUpdate();

            List<Long> expected =
                    List.of(second.getId(), first.getId(), third.getId());
            Long viewerId = viewer.getId();
            entityManager.clear();

            var result = recommendationService.getRecommendations(
                    viewerId, status, 0
            );

            assertThat(result.projectPage().getContent())
                    .extracting(RecommendationCard::projectId)
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    @DisplayName("범위를 넘는 페이지는 전체 결과 수를 유지하고 빈 목록을 반환한다")
    void pageBeyondEnd() {
        saveProfile();
        saveProject(author, "모집글", Role.BACKEND,
                Skill.SPRING_BOOT, MeetingType.ONLINE, future());

        var result = recommendationService.getRecommendations(
                viewer.getId(), MatchStatus.PASS, Integer.MAX_VALUE
        );

        assertThat(result.profileRequired()).isFalse();
        assertThat(result.projectPage().getContent()).isEmpty();
        assertThat(result.projectPage().getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 회원은 추천 목록을 조회할 수 없다")
    void missingMember() {
        assertThatThrownBy(() ->
                recommendationService.getRecommendations(
                        Long.MAX_VALUE, MatchStatus.PASS, 0
                )
        ).isInstanceOf(EntityNotFoundException.class);
    }

    private void saveProfile() {
        Profile profile = new Profile(viewer);
        profile.update(
                Role.BACKEND,
                Set.of(Skill.JAVA, Skill.SPRING_BOOT),
                8,
                Set.of(19, 20, 21, 22),
                ProjectGoal.PORTFOLIO,
                null,
                null,
                null
        );

        profileRepository.saveAndFlush(profile);
    }

    private Project saveProject(
            Member projectAuthor,
            String title,
            Role role,
            Skill preferredSkill,
            MeetingType meetingType,
            LocalDateTime deadline
    ) {
        Project project = new Project(
                projectAuthor,
                title,
                "테스트용 모집글입니다.",
                role,
                Set.of(Skill.JAVA),
                Set.of(preferredSkill),
                8,
                2,
                4,
                Set.of(19, 20, 21, 22),
                ProjectGoal.PORTFOLIO,
                meetingType,
                meetingType == MeetingType.OFFLINE
                        ? "서울특별시 용산구" : null,
                deadline
        );

        return projectRepository.saveAndFlush(project);
    }

    private LocalDateTime future() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .plusDays(7)
                .withNano(0);
    }
}
