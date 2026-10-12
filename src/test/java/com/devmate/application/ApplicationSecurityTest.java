package com.devmate.application;

import com.devmate.application.service.ApplicationService;
import com.devmate.member.security.MemberPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-security-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ApplicationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService service;

    private static final String[] POST_URLS = {
            "/projects/10/applications",
            "/applications/20/accept",
            "/applications/20/reject"
    };

    @Test
    void 비로그인_사용자는_목록을_조회할_수_없다() throws Exception {
        for (String url : new String[]{
                "/my/applications",
                "/projects/10/applications"
        }) {
            mockMvc.perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void CSRF가_있어도_비로그인_사용자는_처리할_수_없다() throws Exception {
        for (String url : POST_URLS) {
            mockMvc.perform(post(url).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void 로그인해도_CSRF가_없으면_403이다() throws Exception {
        for (String url : POST_URLS) {
            mockMvc.perform(post(url).with(user(principal())))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(service);
    }

    @Test
    void 잘못된_CSRF도_403이다() throws Exception {
        for (String url : POST_URLS) {
            mockMvc.perform(post(url)
                            .with(user(principal()))
                            .with(csrf().useInvalidToken()))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(service);
    }

    @Test
    void 로그인과_CSRF가_있으면_세_요청을_처리한다() throws Exception {
        when(service.getProjectIdForAuthor(20L, 1L)).thenReturn(10L);

        mockMvc.perform(post("/projects/10/applications")
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(redirectedUrl("/my/applications"));

        mockMvc.perform(post("/applications/20/accept")
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(redirectedUrl("/projects/10/applications"));

        mockMvc.perform(post("/applications/20/reject")
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(redirectedUrl("/projects/10/applications"));

        verify(service).apply(10L, 1L);
        verify(service).accept(20L, 1L);
        verify(service).reject(20L, 1L);
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(1L, "member@example.com", null);
    }
}
