package com.devmate.project.service;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import com.devmate.application.repository.ApplicationRepository;
import com.devmate.common.enums.*;
import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import com.devmate.project.ProjectForm;
import com.devmate.project.dto.ProjectView;
import com.devmate.project.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:project-service-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        ProjectService.class,
        ProjectServiceTest.ValidationConfig.class
})
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EntityManager entityManager;

    @TestConfiguration(proxyBeanMethods = false)
    static class ValidationConfig {

        @Bean
        static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }
    }

    @Test
    @DisplayName("모집글과 기술·시간대를 저장하고 상세 DTO로 조회한다")
    void createAndReadProject() {
        Long authorId = createMember("author@example.com");
        ProjectForm form = validForm();

        Long projectId = projectService.create(authorId, form);
        entityManager.clear();

        ProjectView result = projectService.getProject(projectId);

        // 조회 DTO의 컬렉션은 영속성 컨텍스트 밖에서도 읽을 수 있어야 한다.
        entityManager.clear();

        assertThat(result.id()).isEqualTo(projectId);
        assertThat(result.authorId()).isEqualTo(authorId);
        assertThat(result.authorName()).isEqualTo("테스트회원");
        assertThat(result.title()).isEqualTo("백엔드 팀원 모집");
        assertThat(result.description()).isEqualTo("함께 서비스를 개발합니다.");
        assertThat(result.role()).isEqualTo(Role.BACKEND);
        assertThat(result.requiredSkills()).containsExactly(Skill.JAVA);
        assertThat(result.preferredSkills()).containsExactly(Skill.SPRING_BOOT);
        assertThat(result.requiredWeeklyHours()).isEqualTo(8);
        assertThat(result.minimumCommonHours()).isEqualTo(2);
        assertThat(result.desiredCommonHours()).isEqualTo(4);
        assertThat(result.availableSlots())
                .containsExactlyInAnyOrder(19, 20, 21, 22);
        assertThat(result.goal()).isEqualTo(ProjectGoal.PORTFOLIO);
        assertThat(result.meetingType()).isEqualTo(MeetingType.ONLINE);
        assertThat(result.region()).isNull();
        assertThat(result.deadline()).isEqualTo(form.getDeadline());
        assertThat(result.status()).isEqualTo(ProjectStatus.OPEN);
        assertThat(result.recruiting()).isTrue();
        assertThat(result.createdAt()).isNotNull();
        assertThat(projectRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("대면 모집글은 지역의 앞뒤 공백을 제거하여 저장한다")
    void offlineRegion() {
        Long authorId = createMember("offline@example.com");
        ProjectForm form = validForm();
        form.setMeetingType(MeetingType.OFFLINE);
        form.setRegion("  서울특별시 용산구  ");

        Long id = projectService.create(authorId, form);
        entityManager.clear();

        assertThat(projectService.getProject(id).region())
                .isEqualTo("서울특별시 용산구");
    }

    @Test
    @DisplayName("비대면 모집글은 지역이 전달돼도 저장하지 않는다")
    void onlineRegionIgnored() {
        Long authorId = createMember("online@example.com");
        ProjectForm form = validForm();
        form.setRegion("서울특별시 용산구");

        Long id = projectService.create(authorId, form);
        entityManager.clear();

        assertThat(projectService.getProject(id).region()).isNull();
    }

    @Test
    @DisplayName("목록은 최신순으로 10개씩 조회한다")
    void listProjectsWithPagination() {
        Long authorId = createMember("list@example.com");
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            ProjectForm form = validForm();
            form.setTitle("모집글 " + i);
            ids.add(projectService.create(authorId, form));
        }

        // 생성 시각이 같은 경우에도 ID 역순으로 정렬되는지 확인한다.
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        entityManager.createQuery(
                        "update Project p set p.createdAt = :createdAt"
                )
                .setParameter("createdAt", sameCreatedAt)
                .executeUpdate();
        entityManager.clear();

        Collections.reverse(ids);

        var firstPage = projectService.getProjects(0);
        var secondPage = projectService.getProjects(1);

        assertThat(firstPage.getTotalElements()).isEqualTo(11L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(ProjectView::id)
                .containsExactlyElementsOf(ids.subList(0, 10));
        assertThat(secondPage.getContent())
                .extracting(ProjectView::id)
                .containsExactly(ids.get(10));
    }

    @Test
    @DisplayName("내 모집글 조회에는 다른 회원의 글이 포함되지 않는다")
    void myProjectsOnly() {
        Long firstId = createMember("first@example.com");
        Long secondId = createMember("second@example.com");

        Long ownProjectId = projectService.create(firstId, validForm());
        projectService.create(secondId, validForm());
        entityManager.clear();

        var result = projectService.getMyProjects(firstId, 0);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent())
                .extracting(ProjectView::id)
                .containsExactly(ownProjectId);
    }

    @Test
    @DisplayName("등록한 모집글이 없으면 빈 목록을 반환한다")
    void emptyMyProjects() {
        Long memberId = createMember("empty@example.com");

        assertThat(projectService.getMyProjects(memberId, 0).getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("유효하지 않은 모집 조건이면 저장하지 않는다")
    void rejectInvalidForm() {
        Long authorId = createMember("invalid@example.com");
        ProjectForm form = validForm();
        form.setMinimumCommonHours(5);
        form.setDesiredCommonHours(4);

        assertThatThrownBy(() -> projectService.create(authorId, form))
                .isInstanceOf(ConstraintViolationException.class);

        assertThat(projectRepository.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 회원은 모집글을 등록할 수 없다")
    void rejectMissingAuthor() {
        // 양수 ID로 조회 실패를 검증한다.
        Long missingId = Long.MAX_VALUE;

        assertThatThrownBy(() ->
                projectService.create(missingId, validForm())
        ).isInstanceOf(EntityNotFoundException.class);

        assertThat(projectRepository.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 모집글 조회는 예외를 반환한다")
    void missingProject() {
        assertThatThrownBy(() ->
                projectService.getProject(Long.MAX_VALUE)
        ).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("음수 페이지 번호는 거부한다")
    void negativePage() {
        assertThatThrownBy(() -> projectService.getProjects(-1))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private Long createMember(String email) {
        return memberRepository.saveAndFlush(
                new Member(email, "test-hash", "테스트회원")
        ).getId();
    }

    @Test
    @DisplayName("직접 마감하면 대기 지원만 거절한다")
    void closeRejectsPendingApplications() {
        Long authorId = createMember("close-author@example.com");
        Long projectId = projectService.create(authorId, validForm());

        Long firstId = createApplication(projectId, "pending1@example.com");
        Long secondId = createApplication(projectId, "pending2@example.com");
        Long rejectedId = createApplication(projectId, "rejected@example.com");

        LocalDateTime previousDecision = LocalDateTime.of(2026, 1, 1, 12, 0);
        applicationRepository.findById(rejectedId).orElseThrow()
                .reject(previousDecision);

        entityManager.flush();
        entityManager.clear();

        assertThat(projectService.close(projectId, authorId)).isTrue();

        entityManager.flush();
        entityManager.clear();

        assertThat(projectRepository.findById(projectId).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.CLOSED);

        ProjectApplication first = applicationRepository.findById(firstId)
                .orElseThrow();
        ProjectApplication second = applicationRepository.findById(secondId)
                .orElseThrow();
        ProjectApplication rejected = applicationRepository.findById(rejectedId)
                .orElseThrow();

        assertThat(first.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(second.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(first.getDecidedAt()).isNotNull();
        assertThat(second.getDecidedAt()).isEqualTo(first.getDecidedAt());

        // 기존 거절 내역의 처리 시각은 덮어쓰지 않는다.
        assertThat(rejected.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(rejected.getDecidedAt()).isEqualTo(previousDecision);
    }

    @Test
    @DisplayName("지원자가 없어도 직접 마감할 수 있다")
    void closeWithoutApplicants() {
        Long authorId = createMember("empty-close@example.com");
        Long projectId = projectService.create(authorId, validForm());

        assertThat(projectService.close(projectId, authorId)).isTrue();

        entityManager.flush();
        entityManager.clear();

        assertThat(projectService.getProject(projectId).status())
                .isEqualTo(ProjectStatus.CLOSED);
    }

    @Test
    @DisplayName("기한이 지났어도 OPEN이면 직접 마감한다")
    void closeAfterDeadline() {
        Long authorId = createMember("expired-close@example.com");
        Long projectId = projectService.create(authorId, validForm());

        // 등록 후 시간이 지나 지원 기한이 경과한 상황을 만든다.
        entityManager.createQuery(
                        "update Project p set p.deadline = :deadline where p.id = :id"
                )
                .setParameter(
                        "deadline",
                        LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1)
                )
                .setParameter("id", projectId)
                .executeUpdate();
        entityManager.clear();

        assertThat(projectService.close(projectId, authorId)).isTrue();

        entityManager.flush();
        entityManager.clear();

        assertThat(projectService.getProject(projectId).status())
                .isEqualTo(ProjectStatus.CLOSED);
    }

    @Test
    @DisplayName("이미 마감된 모집글은 수락 내역을 변경하지 않는다")
    void alreadyClosedPreservesAcceptedApplication() {
        Long authorId = createMember("closed-author@example.com");
        Long projectId = projectService.create(authorId, validForm());
        Long applicationId = createApplication(
                projectId, "accepted@example.com"
        );

        LocalDateTime previousDecision = LocalDateTime.of(2026, 1, 1, 12, 0);
        applicationRepository.findById(applicationId).orElseThrow()
                .accept(previousDecision);
        projectRepository.findById(projectId).orElseThrow().close();

        entityManager.flush();
        entityManager.clear();

        assertThat(projectService.close(projectId, authorId)).isFalse();

        entityManager.flush();
        entityManager.clear();

        ProjectApplication saved = applicationRepository.findById(applicationId)
                .orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(saved.getDecidedAt()).isEqualTo(previousDecision);
    }

    @Test
    @DisplayName("다른 회원은 직접 마감할 수 없다")
    void rejectCloseByNonAuthor() {
        Long authorId = createMember("owner@example.com");
        Long otherId = createMember("not-owner@example.com");
        Long projectId = projectService.create(authorId, validForm());
        Long applicationId = createApplication(
                projectId, "waiting@example.com"
        );

        entityManager.clear();

        assertThatThrownBy(() -> projectService.close(projectId, otherId))
                .isInstanceOf(AccessDeniedException.class);

        entityManager.flush();
        entityManager.clear();

        assertThat(projectService.getProject(projectId).status())
                .isEqualTo(ProjectStatus.OPEN);
        assertThat(applicationRepository.findById(applicationId)
                .orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("없는 모집글은 마감할 수 없다")
    void rejectCloseForMissingProject() {
        Long authorId = createMember("missing-close@example.com");

        assertThatThrownBy(() ->
                projectService.close(Long.MAX_VALUE, authorId)
        ).isInstanceOf(EntityNotFoundException.class);
    }

    private Long createApplication(Long projectId, String email) {
        Long applicantId = createMember(email);

        return applicationRepository.saveAndFlush(
                new ProjectApplication(
                        projectRepository.findById(projectId).orElseThrow(),
                        memberRepository.findById(applicantId).orElseThrow()
                )
        ).getId();
    }

    private ProjectForm validForm() {
        ProjectForm form = new ProjectForm();
        form.setTitle("  백엔드 팀원 모집  ");
        form.setDescription("  함께 서비스를 개발합니다.  ");
        form.setRole(Role.BACKEND);
        form.setRequiredSkills(Set.of(Skill.JAVA));
        form.setPreferredSkills(Set.of(Skill.SPRING_BOOT));
        form.setRequiredWeeklyHours(8);
        form.setMinimumCommonHours(2);
        form.setDesiredCommonHours(4);
        form.setAvailableSlots(Set.of(19, 20, 21, 22));
        form.setGoal(ProjectGoal.PORTFOLIO);
        form.setMeetingType(MeetingType.ONLINE);
        form.setDeadline(
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                        .plusDays(7)
                        .withNano(0)
        );
        return form;
    }
}
