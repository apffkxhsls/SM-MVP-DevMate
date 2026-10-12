package com.devmate.member.security;

import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class LoginIntegrationTest {

    private static final String EMAIL = "login@example.com";
    private static final String PASSWORD = "TestPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = new Member(
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "로그인테스트회원"
        );

        memberId = memberRepository.saveAndFlush(member).getId();
    }

    @Test
    @DisplayName("정상 로그인 시 회원 ID를 세션에 보관하고 해시는 제거한다")
    void loginSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"))
                .andExpect(authenticated().withUsername(EMAIL))
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session).isNotNull();

        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );

        assertThat(context).isNotNull();
        assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOf(MemberPrincipal.class);

        MemberPrincipal principal =
                (MemberPrincipal) context.getAuthentication().getPrincipal();

        assertThat(principal.getMemberId()).isEqualTo(memberId);
        assertThat(principal.getPassword()).isNull();

        // 로그인 상태가 다음 요청에도 유지되는지 확인
        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    @DisplayName("이메일의 대소문자와 앞뒤 공백에 관계없이 로그인한다")
    void normalizedEmailLogin() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "  LOGIN@Example.COM  ")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects"))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인할 수 없다")
    void wrongPassword() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", EMAIL)
                        .param("password", "WrongPass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("미등록 이메일도 동일한 로그인 실패 경로로 이동한다")
    void unknownEmail() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "unknown@example.com")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("비로그인 사용자는 보호된 페이지에 접근할 수 없다")
    void anonymousAccess() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 로그인 요청을 보내면 거부한다")
    void loginWithoutCsrf() throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("로그아웃하면 세션이 무효화되고 보호된 페이지에 접근할 수 없다")
    void logoutSuccess() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(authenticated())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        assertThat(session).isNotNull();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();

        // 무효화된 세션 없이 다시 접근
        mockMvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());
    }
}