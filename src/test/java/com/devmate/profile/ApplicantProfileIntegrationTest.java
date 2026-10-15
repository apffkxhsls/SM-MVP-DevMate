package com.devmate.profile;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import com.devmate.common.enums.MeetingType;
import com.devmate.common.enums.ProjectGoal;
import com.devmate.common.enums.Role;
import com.devmate.common.enums.Skill;
import com.devmate.member.Member;
import com.devmate.member.security.MemberPrincipal;
import com.devmate.project.Project;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:applicant-profile-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ApplicantProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private Member author;
    private Member applicant;
    private Member stranger;
    private Project project;

    @BeforeEach
    void setUp() {
        author = saveMember("author@example.com", "작성자");
        applicant = saveMember("applicant@example.com", "지원자");
        stranger = saveMember("stranger@example.com", "다른 회원");

        project = saveProject(author);
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("지원 상태와 관계없이 작성자는 지원자의 프로필을 조회한다")
    void authorCanReadApplicantProfile(ApplicationStatus applicationStatus)
            throws Exception {
        saveProfile(applicant);
        saveApplication(project, applicant, applicationStatus);

        Long applicantId = applicant.getId();
        MemberPrincipal viewer = principal(author);
        flushAndClear();

        mockMvc.perform(profileRequest(applicantId.toString())
                        .with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                // 응답 필드는 정확히 3개만 허용한다.
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.name").value("지원자"))
                .andExpect(jsonPath("$.skills", hasSize(2)))
                .andExpect(jsonPath("$.skills[0]").value("JAVA"))
                .andExpect(jsonPath("$.skills[1]").value("SPRING_BOOT"))
                .andExpect(jsonPath("$.availableSlots", hasSize(4)))
                .andExpect(jsonPath("$.availableSlots[0]").value(19))
                .andExpect(jsonPath("$.availableSlots[1]").value(20))
                .andExpect(jsonPath("$.availableSlots[2]").value(43))
                .andExpect(jsonPath("$.availableSlots[3]").value(44))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("다른 모집글 작성자는 지원자의 프로필을 조회할 수 없다")
    void unrelatedAuthorCannotReadProfile() throws Exception {
        saveProfile(applicant);
        saveApplication(project, applicant, ApplicationStatus.PENDING);
        saveProject(stranger);

        Long applicantId = applicant.getId();
        MemberPrincipal viewer = principal(stranger);
        flushAndClear();

        mockMvc.perform(profileRequest(applicantId.toString())
                        .with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("지원 이력이 없으면 프로필이 있어도 조회할 수 없다")
    void memberWithoutApplicationIsForbidden() throws Exception {
        saveProfile(applicant);

        Long applicantId = applicant.getId();
        MemberPrincipal viewer = principal(author);
        flushAndClear();

        mockMvc.perform(profileRequest(applicantId.toString())
                        .with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 회원도 조회 권한이 없으면 403을 반환한다")
    void unknownMemberIsForbidden() throws Exception {
        mockMvc.perform(profileRequest(Long.toString(Long.MAX_VALUE))
                        .with(user(principal(author))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("조회 권한이 있지만 프로필이 없으면 404를 반환한다")
    void missingProfileReturnsNotFound() throws Exception {
        saveApplication(project, applicant, ApplicationStatus.PENDING);

        Long applicantId = applicant.getId();
        MemberPrincipal viewer = principal(author);
        flushAndClear();

        mockMvc.perform(profileRequest(applicantId.toString())
                        .with(user(viewer)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    @DisplayName("잘못된 회원 ID는 400을 반환한다")
    void invalidMemberIdReturnsBadRequest(String memberId) throws Exception {
        mockMvc.perform(profileRequest(memberId)
                        .with(user(principal(author))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html"})
    @DisplayName("미로그인 API 요청은 로그인 화면 이동 없이 401을 반환한다")
    void anonymousApiRequestReturnsUnauthorized(String accept)
            throws Exception {
        mockMvc.perform(profileRequest(applicant.getId().toString())
                        .accept(MediaType.parseMediaType(accept)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @DisplayName("기존 화면은 미로그인 상태에서 로그인 화면으로 이동한다")
    void anonymousPageRequestStillRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/projects")
                        .servletPath("/projects")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private MockHttpServletRequestBuilder profileRequest(String memberId) {
        String path = "/members/" + memberId + "/profile";

        // 실제 서버처럼 servletPath를 지정해 보안 경로 조건을 검증한다.
        return get(path)
                .servletPath(path)
                .accept(MediaType.APPLICATION_JSON);
    }

    private MemberPrincipal principal(Member member) {
        return new MemberPrincipal(
                member.getId(),
                member.getEmail(),
                null
        );
    }

    private Member saveMember(String email, String name) {
        // 로그인 자체가 아닌 로그인 이후의 조회 권한을 테스트한다.
        Member member = new Member(email, "test-password-hash", name);
        entityManager.persist(member);
        return member;
    }

    private Project saveProject(Member owner) {
        Project savedProject = new Project(
                owner,
                "백엔드 팀원 모집",
                "지원자 프로필 조회 테스트",
                Role.BACKEND,
                Set.of(Skill.JAVA),
                Set.of(Skill.SPRING_BOOT),
                8,
                2,
                4,
                Set.of(19, 20, 43, 44),
                ProjectGoal.PORTFOLIO,
                MeetingType.ONLINE,
                null,
                LocalDateTime.now().plusDays(7)
        );

        entityManager.persist(savedProject);
        return savedProject;
    }

    private void saveProfile(Member member) {
        Profile profile = new Profile(member);
        profile.update(
                Role.BACKEND,
                Set.of(Skill.SPRING_BOOT, Skill.JAVA),
                8,
                Set.of(44, 19, 43, 20),
                ProjectGoal.PORTFOLIO,
                null,
                null,
                "자기소개"
        );

        entityManager.persist(profile);
    }

    private void saveApplication(
            Project targetProject,
            Member member,
            ApplicationStatus status
    ) {
        ProjectApplication application =
                new ProjectApplication(targetProject, member);

        if (status == ApplicationStatus.ACCEPTED) {
            application.accept(LocalDateTime.now());
            targetProject.close();
        } else if (status == ApplicationStatus.REJECTED) {
            application.reject(LocalDateTime.now());
        }

        entityManager.persist(application);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
