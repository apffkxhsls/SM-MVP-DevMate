package com.devmate.application;

import com.devmate.application.dto.ApplicationView;
import com.devmate.application.exception.ApplicationConflictException;
import com.devmate.application.service.ApplicationService;
import com.devmate.member.security.MemberPrincipal;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationControllerTest {

    private ApplicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ApplicationService.class);

        MemberPrincipal principal =
                new MemberPrincipal(1L, "member@example.com", null);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );

        AbstractView emptyView = new AbstractView() {
            @Override
            protected void renderMergedOutputModel(
                    Map<String, Object> model,
                    HttpServletRequest request,
                    HttpServletResponse response
            ) {
                // 컨트롤러 테스트에서는 실제 HTML을 렌더링하지 않는다.
            }
        };

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationController(service))
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
    }

    @Test
    void 지원자는_요청값이_아닌_인증정보를_사용한다() throws Exception {
        mockMvc.perform(post("/projects/10/applications")
                        .param("applicantId", "999"))
                .andExpect(redirectedUrl("/my/applications"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(service).apply(10L, 1L);
    }

    @Test
    void 지원_실패는_상세화면으로_돌아간다() throws Exception {
        when(service.apply(10L, 1L))
                .thenThrow(new ApplicationConflictException("이미 지원했습니다."));

        mockMvc.perform(post("/projects/10/applications"))
                .andExpect(redirectedUrl("/projects/10"))
                .andExpect(flash().attribute(
                        "errorMessage", "이미 지원했습니다."
                ));
    }

    @Test
    void 내_지원_목록의_기본_페이지는_0이다() throws Exception {
        Page<ApplicationView> result = Page.empty();
        when(service.getMyApplications(1L, 0)).thenReturn(result);

        mockMvc.perform(get("/my/applications"))
                .andExpect(status().isOk())
                .andExpect(view().name("application/my-list"))
                .andExpect(model().attribute("applicationPage", result));

        verify(service).getMyApplications(1L, 0);
    }

    @Test
    void 지원자_목록에_모집글_ID와_페이지를_전달한다() throws Exception {
        Page<ApplicationView> result = Page.empty();
        when(service.getProjectApplications(10L, 1L, 2)).thenReturn(result);

        mockMvc.perform(get("/projects/10/applications").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("application/applicants"))
                .andExpect(model().attribute("projectId", 10L))
                .andExpect(model().attribute("applicationPage", result));
    }

    @Test
    void 수락_성공시_지원자_목록으로_이동한다() throws Exception {
        when(service.getProjectIdForAuthor(20L, 1L)).thenReturn(10L);

        mockMvc.perform(post("/applications/20/accept"))
                .andExpect(redirectedUrl("/projects/10/applications"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(service).accept(20L, 1L);
    }

    @Test
    void 거절_성공시_지원자_목록으로_이동한다() throws Exception {
        when(service.getProjectIdForAuthor(20L, 1L)).thenReturn(10L);

        mockMvc.perform(post("/applications/20/reject"))
                .andExpect(redirectedUrl("/projects/10/applications"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(service).reject(20L, 1L);
    }

    @Test
    void 수락_실패시_실패_메시지를_전달한다() throws Exception {
        when(service.getProjectIdForAuthor(20L, 1L)).thenReturn(10L);
        doThrow(new ApplicationConflictException("모집이 마감되었습니다."))
                .when(service).accept(20L, 1L);

        mockMvc.perform(post("/applications/20/accept"))
                .andExpect(redirectedUrl("/projects/10/applications"))
                .andExpect(flash().attribute(
                        "errorMessage", "모집이 마감되었습니다."
                ))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void 거절_실패시_실패_메시지를_전달한다() throws Exception {
        when(service.getProjectIdForAuthor(20L, 1L)).thenReturn(10L);
        doThrow(new ApplicationConflictException("이미 처리된 지원입니다."))
                .when(service).reject(20L, 1L);

        mockMvc.perform(post("/applications/20/reject"))
                .andExpect(redirectedUrl("/projects/10/applications"))
                .andExpect(flash().attribute(
                        "errorMessage", "이미 처리된 지원입니다."
                ));
    }

    @Test
    void 권한이_없으면_403이고_수락을_호출하지_않는다() throws Exception {
        when(service.getProjectIdForAuthor(20L, 1L))
                .thenThrow(new AccessDeniedException("권한 없음"));

        mockMvc.perform(post("/applications/20/accept"))
                .andExpect(status().isForbidden());

        verify(service, never()).accept(anyLong(), anyLong());
    }

    @Test
    void 없는_모집글은_404이다() throws Exception {
        when(service.apply(10L, 1L))
                .thenThrow(new EntityNotFoundException("모집글 없음"));

        mockMvc.perform(post("/projects/10/applications"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_ID와_페이지는_400이다() throws Exception {
        mockMvc.perform(post("/projects/0/applications"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/applications/-1/accept"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/applications/abc/reject"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/my/applications").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/projects/10/applications").param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
