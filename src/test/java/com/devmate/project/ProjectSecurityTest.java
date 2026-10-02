package com.devmate.project;

import com.devmate.matching.service.RecommendationService;
import com.devmate.member.security.MemberPrincipal;
import com.devmate.project.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:project-security-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ProjectSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    @DisplayName("비로그인 사용자는 모집글 관련 화면에 접근할 수 없다")
    void anonymousPageAccess() throws Exception {
        for (String url : new String[]{
                "/projects",
                "/projects/new",
                "/projects/1",
                "/my/projects"
        }) {
            mockMvc.perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        verifyNoInteractions(projectService, recommendationService);
    }

    @Test
    @DisplayName("CSRF 토큰이 있어도 비로그인 사용자는 등록할 수 없다")
    void anonymousCreate() throws Exception {
        mockMvc.perform(post("/projects").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(projectService, recommendationService);
    }

    @Test
    @DisplayName("로그인했어도 CSRF 토큰이 없으면 등록할 수 없다")
    void createWithoutCsrf() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                1L, "member@example.com", null
        );

        mockMvc.perform(post("/projects").with(user(principal)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService, recommendationService);
    }

    @Test
    @DisplayName("로그인했어도 CSRF 토큰이 잘못되면 등록할 수 없다")
    void createWithInvalidCsrf() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                1L, "member@example.com", null
        );

        mockMvc.perform(post("/projects")
                        .with(user(principal))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(projectService, recommendationService);
    }

    @Test
    @DisplayName("비로그인 사용자는 모든 추천 탭에 접근할 수 없다")
    void anonymousRecommendationTabs() throws Exception {
        for (String matchStatus : new String[]{"PASS", "FAIL", "UNKNOWN"}) {
            mockMvc.perform(get("/projects")
                            .param("status", matchStatus))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        verifyNoInteractions(projectService, recommendationService);
    }
}