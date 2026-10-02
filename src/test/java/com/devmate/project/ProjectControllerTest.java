package com.devmate.project;

import com.devmate.matching.MatchStatus;
import com.devmate.matching.dto.RecommendationCard;
import com.devmate.matching.dto.RecommendationResult;
import com.devmate.matching.service.RecommendationService;
import com.devmate.member.security.MemberPrincipal;
import com.devmate.project.dto.ProjectView;
import com.devmate.project.service.ProjectService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectControllerTest {

    private MockMvc mockMvc;
    private ProjectService projectService;
    private LocalValidatorFactoryBean validator;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        recommendationService = mock(RecommendationService.class);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        MemberPrincipal principal = new MemberPrincipal(
                1L, "member@example.com", null
        );

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        AbstractView emptyView = new AbstractView() {
            @Override
            protected void renderMergedOutputModel(
                    Map<String, Object> model,
                    HttpServletRequest request,
                    HttpServletResponse response
            ) {
                // 실제 HTML 렌더링은 생략한다.
            }
        };

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProjectController(projectService, recommendationService))
                .setValidator(validator)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setViewResolvers((viewName, locale) -> {
                    if (viewName.startsWith("redirect:")) {
                        return new RedirectView(
                                viewName.substring("redirect:".length())
                        );
                    }
                    return emptyView;
                })
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    @DisplayName("기본 요청은 로그인 회원의 PASS 첫 페이지를 조회한다")
    void defaultPage() throws Exception {
        Page<RecommendationCard> page = Page.empty();

        when(recommendationService.getRecommendations(
                1L, MatchStatus.PASS, 0
        )).thenReturn(
                new RecommendationResult(page, MatchStatus.PASS, false)
        );

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(view().name("project/list"))
                .andExpect(model().attribute("projectPage", page))
                .andExpect(model().attribute("selectedStatus", MatchStatus.PASS))
                .andExpect(model().attribute("profileRequired", false));

        verify(recommendationService)
                .getRecommendations(1L, MatchStatus.PASS, 0);
        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("요청한 상태와 페이지 번호로 추천 목록을 조회한다")
    void requestedPage() throws Exception {
        Page<RecommendationCard> page = Page.empty();

        when(recommendationService.getRecommendations(
                1L, MatchStatus.FAIL, 2
        )).thenReturn(
                new RecommendationResult(page, MatchStatus.FAIL, false)
        );

        mockMvc.perform(get("/projects")
                        .param("status", "FAIL")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("projectPage", page))
                .andExpect(model().attribute("selectedStatus", MatchStatus.FAIL));

        verify(recommendationService)
                .getRecommendations(1L, MatchStatus.FAIL, 2);
    }

    @Test
    @DisplayName("전체 목록과 내 목록의 잘못된 페이지 번호는 400으로 처리한다")
    void invalidPage() throws Exception {
        for (String url : new String[]{"/projects", "/my/projects"}) {
            for (String page : new String[]{"-1", "abc", "1.5"}) {
                mockMvc.perform(get(url).param("page", page))
                        .andExpect(status().isBadRequest());
            }
        }

        verifyNoInteractions(projectService, recommendationService);
    }

    @Test
    @DisplayName("작성 화면에 빈 모집글 폼을 전달한다")
    void createPage() throws Exception {
        mockMvc.perform(get("/projects/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("project/form"))
                .andExpect(model().attributeExists("projectForm"));

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("요청의 작성자 ID 대신 로그인한 회원 ID로 등록한다")
    void createProject() throws Exception {
        when(projectService.create(eq(1L), any(ProjectForm.class)))
                .thenReturn(10L);

        mockMvc.perform(validRequest().param("authorId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/10"))
                .andExpect(flash().attribute(
                        "successMessage", "모집글이 등록되었습니다."
                ));

        ArgumentCaptor<ProjectForm> captor =
                ArgumentCaptor.forClass(ProjectForm.class);

        verify(projectService).create(eq(1L), captor.capture());

        assertThat(captor.getValue().getTitle()).isEqualTo("백엔드 팀원 모집");
        assertThat(captor.getValue().getAvailableSlots())
                .containsExactlyInAnyOrder(19, 20, 21, 22);
    }

    @Test
    @DisplayName("시간 조건 오류 시 입력값을 유지하고 등록하지 않는다")
    void invalidHours() throws Exception {
        MockHttpServletRequestBuilder request = validRequest();
        request.param("minimumCommonHours", "5");

        // 기존 값에 추가하지 않고 요청 값을 교체한다.
        request.params(
                new org.springframework.util.LinkedMultiValueMap<>() {{
                    put("minimumCommonHours", java.util.List.of("5"));
                }}
        );

        // 중복 파라미터를 피하기 위해 아래 요청에서 값을 명시한다.
        var result = mockMvc.perform(post("/projects")
                        .param("title", "백엔드 팀원 모집")
                        .param("description", "함께 서비스를 개발합니다.")
                        .param("role", "BACKEND")
                        .param("requiredWeeklyHours", "8")
                        .param("minimumCommonHours", "5")
                        .param("desiredCommonHours", "4")
                        .param("availableSlots", "19", "20", "21", "22")
                        .param("goal", "PORTFOLIO")
                        .param("meetingType", "ONLINE")
                        .param("deadline", futureDeadline()))
                .andExpect(status().isOk())
                .andExpect(view().name("project/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "projectForm", "hoursOrdered"
                ))
                .andReturn();

        ProjectForm form = (ProjectForm) result.getModelAndView()
                .getModel().get("projectForm");

        assertThat(form.getTitle()).isEqualTo("백엔드 팀원 모집");
        assertThat(form.getMinimumCommonHours()).isEqualTo(5);
        assertThat(form.getAvailableSlots())
                .containsExactlyInAnyOrder(19, 20, 21, 22);

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 저장하지 않는다")
    void missingFields() throws Exception {
        mockMvc.perform(post("/projects"))
                .andExpect(status().isOk())
                .andExpect(view().name("project/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "projectForm",
                        "title",
                        "description",
                        "role",
                        "requiredWeeklyHours",
                        "minimumCommonHours",
                        "desiredCommonHours",
                        "availableSlots",
                        "goal",
                        "meetingType",
                        "deadline"
                ));

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("상세 DTO를 상세 화면에 전달한다")
    void projectDetail() throws Exception {
        ProjectView project = mock(ProjectView.class);
        when(projectService.getProject(10L)).thenReturn(project);

        mockMvc.perform(get("/projects/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("project/detail"))
                .andExpect(model().attribute("project", project));

        verify(projectService).getProject(10L);
    }

    @Test
    @DisplayName("없는 모집글은 404로 처리한다")
    void missingProject() throws Exception {
        when(projectService.getProject(10L))
                .thenThrow(new EntityNotFoundException());

        mockMvc.perform(get("/projects/10"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("잘못된 모집글 ID는 400으로 처리한다")
    void invalidProjectId() throws Exception {
        for (String id : new String[]{"0", "-1", "abc"}) {
            mockMvc.perform(get("/projects/" + id))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("내 모집글은 로그인한 회원 ID로 조회한다")
    void myProjects() throws Exception {
        Page<ProjectView> result = Page.empty();
        when(projectService.getMyProjects(1L, 0)).thenReturn(result);

        mockMvc.perform(get("/my/projects").param("memberId", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("project/my-list"))
                .andExpect(model().attribute("projectPage", result));

        verify(projectService).getMyProjects(1L, 0);
    }

    @Test
    @DisplayName("프로필이 없으면 빈 목록과 프로필 작성 안내를 전달한다")
    void profileRequired() throws Exception {
        Page<RecommendationCard> page = Page.empty();

        when(recommendationService.getRecommendations(
                1L, MatchStatus.PASS, 0
        )).thenReturn(
                new RecommendationResult(page, MatchStatus.PASS, true)
        );

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(view().name("project/list"))
                .andExpect(model().attribute("projectPage", page))
                .andExpect(model().attribute("profileRequired", true));
    }

    @Test
    @DisplayName("UNKNOWN 탭을 조회할 수 있다")
    void unknownStatus() throws Exception {
        Page<RecommendationCard> page = Page.empty();

        when(recommendationService.getRecommendations(
                1L, MatchStatus.UNKNOWN, 0
        )).thenReturn(
                new RecommendationResult(page, MatchStatus.UNKNOWN, false)
        );

        mockMvc.perform(get("/projects")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus", MatchStatus.UNKNOWN))
                .andExpect(model().attribute("projectPage", page));

        verify(recommendationService)
                .getRecommendations(1L, MatchStatus.UNKNOWN, 0);
    }

    @Test
    @DisplayName("정의되지 않은 추천 상태는 400으로 처리한다")
    void invalidRecommendationStatus() throws Exception {
        mockMvc.perform(get("/projects")
                        .param("status", "HELLO"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService, recommendationService);
    }

    @Test
    @DisplayName("다른 회원 ID를 보내도 로그인한 회원 기준으로 추천한다")
    void recommendationUsesAuthenticatedMember() throws Exception {
        when(recommendationService.getRecommendations(
                1L, MatchStatus.PASS, 0
        )).thenReturn(
                new RecommendationResult(Page.empty(), MatchStatus.PASS, false)
        );

        mockMvc.perform(get("/projects")
                        .param("memberId", "999"))
                .andExpect(status().isOk());

        verify(recommendationService)
                .getRecommendations(1L, MatchStatus.PASS, 0);
        verifyNoMoreInteractions(recommendationService);
        verifyNoInteractions(projectService);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post("/projects")
                .param("title", "백엔드 팀원 모집")
                .param("description", "함께 서비스를 개발합니다.")
                .param("role", "BACKEND")
                .param("requiredWeeklyHours", "8")
                .param("minimumCommonHours", "2")
                .param("desiredCommonHours", "4")
                .param("availableSlots", "19", "20", "21", "22")
                .param("goal", "PORTFOLIO")
                .param("meetingType", "ONLINE")
                .param("deadline", futureDeadline());
    }

    private String futureDeadline() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .plusDays(7)
                .withNano(0)
                .toString();
    }
}
