package com.devmate.profile;

import com.devmate.common.enums.ProjectGoal;
import com.devmate.common.enums.Role;
import com.devmate.member.security.MemberPrincipal;
import com.devmate.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profile-controller-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    private MemberPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new MemberPrincipal(
                1L,
                "member@example.com",
                null
        );
    }

    @Test
    @DisplayName("로그인한 회원의 프로필을 조회한다")
    void getMyProfile() throws Exception {
        ProfileForm saved = new ProfileForm();
        saved.setRole(Role.BACKEND);
        saved.setWeeklyHours(10);
        saved.setAvailableSlots(Set.of(19, 20));
        saved.setGoal(ProjectGoal.PORTFOLIO);

        when(profileService.getProfile(1L)).thenReturn(saved);

        mockMvc.perform(get("/profile").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/form"))
                .andExpect(model().attribute("profileForm", saved));

        verify(profileService).getProfile(1L);
    }

    @Test
    @DisplayName("요청에 다른 회원 ID가 있어도 로그인한 회원의 프로필을 저장한다")
    void saveMyProfile() throws Exception {
        when(profileService.saveProfile(eq(1L), any(ProfileForm.class)))
                .thenReturn(10L);

        mockMvc.perform(validRequest()
                        .param("memberId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute(
                        "successMessage", "프로필이 저장되었습니다."
                ));

        ArgumentCaptor<ProfileForm> captor =
                ArgumentCaptor.forClass(ProfileForm.class);

        verify(profileService).saveProfile(eq(1L), captor.capture());

        ProfileForm submitted = captor.getValue();
        assertThat(submitted.getRole()).isEqualTo(Role.BACKEND);
        assertThat(submitted.getWeeklyHours()).isEqualTo(10);
        assertThat(submitted.getAvailableSlots())
                .containsExactlyInAnyOrder(19, 20);
        assertThat(submitted.getGoal()).isEqualTo(ProjectGoal.PORTFOLIO);
    }

    @Test
    @DisplayName("입력 검증에 실패하면 입력값을 유지하고 저장하지 않는다")
    void invalidInput() throws Exception {
        var result = mockMvc.perform(post("/profile")
                        .with(user(principal))
                        .with(csrf())
                        .param("role", "BACKEND")
                        .param("weeklyHours", "0")
                        .param("availableSlots", "19", "20")
                        .param("goal", "PORTFOLIO")
                        .param("introduction", "입력한 자기소개"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "profileForm", "weeklyHours"
                ))
                .andReturn();

        ProfileForm form = (ProfileForm) result.getModelAndView()
                .getModel().get("profileForm");

        assertThat(form.getWeeklyHours()).isZero();
        assertThat(form.getRole()).isEqualTo(Role.BACKEND);
        assertThat(form.getAvailableSlots())
                .containsExactlyInAnyOrder(19, 20);
        assertThat(form.getIntroduction()).isEqualTo("입력한 자기소개");

        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("잘못된 enum과 소수 시간은 입력 오류로 처리한다")
    void invalidBinding() throws Exception {
        mockMvc.perform(post("/profile")
                        .with(user(principal))
                        .with(csrf())
                        .param("role", "HELLO")
                        .param("skills", "UNKNOWN_SKILL")
                        .param("weeklyHours", "1.5")
                        .param("availableSlots", "19")
                        .param("goal", "PORTFOLIO"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "profileForm", "role", "skills", "weeklyHours"
                ));

        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("비로그인 사용자의 프로필 조회와 저장을 차단한다")
    void anonymousAccess() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // CSRF는 통과시켜 인증 여부 때문에 차단되는지 확인한다.
        mockMvc.perform(post("/profile").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("로그인했어도 CSRF 토큰 없이 저장할 수 없다")
    void missingCsrf() throws Exception {
        mockMvc.perform(post("/profile")
                        .with(user(principal))
                        .param("role", "BACKEND")
                        .param("weeklyHours", "10")
                        .param("availableSlots", "19")
                        .param("goal", "PORTFOLIO"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(profileService);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post("/profile")
                .with(user(principal))
                .with(csrf())
                .param("role", "BACKEND")
                .param("weeklyHours", "10")
                .param("availableSlots", "19", "20")
                .param("skills", "JAVA")
                .param("goal", "PORTFOLIO");
    }
}