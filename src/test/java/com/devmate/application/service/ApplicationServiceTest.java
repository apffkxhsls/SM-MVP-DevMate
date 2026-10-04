package com.devmate.application.service;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import com.devmate.application.exception.ApplicationConflictException;
import com.devmate.application.repository.ApplicationRepository;
import com.devmate.common.enums.*;
import com.devmate.matching.service.MatchingService;
import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import com.devmate.profile.Profile;
import com.devmate.profile.repository.ProfileRepository;
import com.devmate.project.Project;
import com.devmate.project.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-service-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ApplicationService.class, MatchingService.class})
class ApplicationServiceTest {

    @Autowired
    private ApplicationService service;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private MemberRepository members;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private EntityManager entityManager;

    private Member author;
    private Member applicant;
    private Member other;
    private Project project;

    @BeforeEach
    void setUp() {
        author = saveMember("author@example.com", "작성자");
        applicant = saveMember("applicant@example.com", "지원자");
        other = saveMember("other@example.com", "다른 회원");

        Profile profile = new Profile(applicant);
        profile.update(
                Role.BACKEND,
                Set.of(Skill.JAVA),
                10,
                Set.of(19, 20),
                ProjectGoal.CONTEST,
                null,
                null,
                null
        );
        profiles.saveAndFlush(profile);

        project = saveProject(now().plusDays(1));
    }

    @Test
    void 조건을_충족하면_대기_상태로_저장한다() {
        Long id = service.apply(project.getId(), applicant.getId());

        flushAndClear();

        ProjectApplication saved = applications.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(saved.getApplicant().getId()).isEqualTo(applicant.getId());
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getDecidedAt()).isNull();
    }

    @Test
    void 본인_모집글에는_지원할_수_없다() {
        assertThatThrownBy(() ->
                service.apply(project.getId(), author.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        assertThat(applications.count()).isZero();
    }

    @Test
    void 거절된_지원도_중복_지원할_수_없다() {
        ProjectApplication existing = saveApplication(applicant);
        existing.reject(now());
        flushAndClear();

        assertThatThrownBy(() ->
                service.apply(project.getId(), applicant.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void 마감된_모집글에는_지원할_수_없다() {
        project.close();
        flushAndClear();

        assertThatThrownBy(() ->
                service.apply(project.getId(), applicant.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        assertThat(applications.count()).isZero();
    }

    @Test
    void 기한이_지난_모집글에는_지원할_수_없다() {
        Project expired = saveProject(now().minusDays(1));

        assertThatThrownBy(() ->
                service.apply(expired.getId(), applicant.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        assertThat(applications.count()).isZero();
    }

    @Test
    void 프로필이_없으면_지원할_수_없다() {
        assertThatThrownBy(() ->
                service.apply(project.getId(), other.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        assertThat(applications.count()).isZero();
    }

    @Test
    void 필수_조건이_불일치하면_지원할_수_없다() {
        Profile profile = profiles.findByMember_Id(applicant.getId())
                .orElseThrow();

        profile.update(
                Role.FRONTEND,
                Set.of(Skill.JAVA),
                10,
                Set.of(19, 20),
                ProjectGoal.CONTEST,
                null,
                null,
                null
        );
        flushAndClear();

        assertThatThrownBy(() ->
                service.apply(project.getId(), applicant.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        assertThat(applications.count()).isZero();
    }

    @Test
    void 수락하면_모집을_마감하고_나머지_대기_지원을_거절한다() {
        Long selectedId = saveApplication(applicant).getId();
        Long remainingId = saveApplication(other).getId();
        flushAndClear();

        service.accept(selectedId, author.getId());
        flushAndClear();

        ProjectApplication selected = applications.findById(selectedId)
                .orElseThrow();
        ProjectApplication remaining = applications.findById(remainingId)
                .orElseThrow();

        assertThat(selected.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(remaining.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(selected.getDecidedAt()).isNotNull();
        assertThat(remaining.getDecidedAt()).isEqualTo(selected.getDecidedAt());
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.CLOSED);
    }

    @Test
    void 작성자가_아니면_수락할_수_없다() {
        Long id = saveApplication(applicant).getId();
        flushAndClear();

        assertThatThrownBy(() ->
                service.accept(id, other.getId())
        ).isInstanceOf(AccessDeniedException.class);

        flushAndClear();
        assertThat(applications.findById(id).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.OPEN);
    }

    @Test
    void 거절하면_해당_지원만_변경한다() {
        Long rejectedId = saveApplication(applicant).getId();
        Long remainingId = saveApplication(other).getId();
        flushAndClear();

        service.reject(rejectedId, author.getId());
        flushAndClear();

        assertThat(applications.findById(rejectedId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
        assertThat(applications.findById(rejectedId).orElseThrow().getDecidedAt())
                .isNotNull();
        assertThat(applications.findById(remainingId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
        assertThat(projects.findById(project.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.OPEN);
    }

    @Test
    void 마감된_모집글의_대기_지원도_거절할_수_있다() {
        Long id = saveApplication(applicant).getId();
        project.close();
        flushAndClear();

        service.reject(id, author.getId());
        flushAndClear();

        assertThat(applications.findById(id).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void 수락된_지원을_거절로_바꿀_수_없다() {
        ProjectApplication application = saveApplication(applicant);
        application.accept(now());
        project.close();
        Long id = application.getId();
        flushAndClear();

        assertThatThrownBy(() ->
                service.reject(id, author.getId())
        ).isInstanceOf(ApplicationConflictException.class);

        flushAndClear();
        assertThat(applications.findById(id).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.ACCEPTED);
    }

    @Test
    void 내_지원_목록에는_본인_지원만_나온다() {
        Long mine = saveApplication(applicant).getId();
        saveApplication(other);
        flushAndClear();

        var result = service.getMyApplications(applicant.getId(), 0);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().applicationId())
                .isEqualTo(mine);
        assertThat(result.getContent().getFirst().projectTitle())
                .isEqualTo("백엔드 모집");
    }

    @Test
    void 작성자는_지원자를_조회할_수_있다() {
        saveApplication(applicant);
        saveApplication(other);
        flushAndClear();

        var result = service.getProjectApplications(
                project.getId(), author.getId(), 0
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(view -> view.applicantId())
                .containsExactlyInAnyOrder(applicant.getId(), other.getId());
    }

    @Test
    void 작성자가_아니면_지원자_목록을_조회할_수_없다() {
        assertThatThrownBy(() ->
                service.getProjectApplications(
                        project.getId(), other.getId(), 0
                )
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 작성자만_지원의_모집글_ID를_조회할_수_있다() {
        Long id = saveApplication(applicant).getId();

        assertThat(service.getProjectIdForAuthor(id, author.getId()))
                .isEqualTo(project.getId());

        assertThatThrownBy(() ->
                service.getProjectIdForAuthor(id, other.getId())
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 없는_지원은_처리할_수_없다() {
        assertThatThrownBy(() ->
                service.reject(Long.MAX_VALUE, author.getId())
        ).isInstanceOf(EntityNotFoundException.class);
    }

    private Member saveMember(String email, String name) {
        return members.saveAndFlush(new Member(email, "test-hash", name));
    }

    private Project saveProject(LocalDateTime deadline) {
        return projects.saveAndFlush(new Project(
                author,
                "백엔드 모집",
                "함께 개발할 팀원을 모집합니다.",
                Role.BACKEND,
                Set.of(Skill.JAVA),
                Set.of(),
                8,
                1,
                2,
                Set.of(19, 20),
                ProjectGoal.CONTEST,
                MeetingType.ONLINE,
                null,
                deadline
        ));
    }

    private ProjectApplication saveApplication(Member member) {
        return applications.saveAndFlush(
                new ProjectApplication(project, member)
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
