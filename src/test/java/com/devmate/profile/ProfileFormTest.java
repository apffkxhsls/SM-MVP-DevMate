package com.devmate.profile;

import com.devmate.common.enums.ProjectGoal;
import com.devmate.common.enums.Role;
import com.devmate.common.enums.Skill;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("필수 항목이 올바르면 선택 항목 없이도 검증을 통과한다")
    void validProfile() {
        ProfileForm form = validForm();

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    @DisplayName("필수 항목이 누락되면 검증에 실패한다")
    void missingRequiredFields() {
        ProfileForm form = new ProfileForm();

        assertThat(validator.validate(form))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("role", "weeklyHours", "availableSlots", "goal");
    }

    @Test
    @DisplayName("주당 투자 시간은 1시간과 168시간을 허용한다")
    void validWeeklyHoursBoundaries() {
        ProfileForm form = validForm();

        for (int hours : new int[]{1, 168}) {
            form.setWeeklyHours(hours);

            assertThat(validator.validate(form)).isEmpty();
        }
    }

    @Test
    @DisplayName("주당 투자 시간이 범위를 벗어나면 거부한다")
    void invalidWeeklyHours() {
        ProfileForm form = validForm();

        for (int hours : new int[]{0, 169}) {
            form.setWeeklyHours(hours);

            assertThat(validator.validate(form))
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("weeklyHours");
        }
    }

    @Test
    @DisplayName("시간대는 0과 167을 허용한다")
    void validSlotBoundaries() {
        ProfileForm form = validForm();
        form.setAvailableSlots(Set.of(0, 167));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    @DisplayName("시간대 값이 범위를 벗어나면 거부한다")
    void invalidSlots() {
        ProfileForm form = validForm();

        for (int slot : new int[]{-1, 168}) {
            form.setAvailableSlots(Set.of(slot));

            assertThat(validator.validate(form))
                    .anySatisfy(violation ->
                            assertThat(violation.getPropertyPath().toString())
                                    .startsWith("availableSlots")
                    );
        }
    }

    @Test
    @DisplayName("시간대를 선택하지 않으면 거부한다")
    void emptySlots() {
        ProfileForm form = validForm();
        form.setAvailableSlots(Set.of());

        assertThat(validator.validate(form))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("availableSlots");
    }

    @Test
    @DisplayName("HTTP와 HTTPS 포트폴리오 주소를 허용한다")
    void validPortfolioUrl() {
        ProfileForm form = validForm();

        for (String url : new String[]{
                "http://example.com",
                "https://github.com/example"
        }) {
            form.setPortfolioUrl(url);

            assertThat(validator.validate(form)).isEmpty();
        }
    }

    @Test
    @DisplayName("잘못된 주소나 허용하지 않는 프로토콜은 거부한다")
    void invalidPortfolioUrl() {
        ProfileForm form = validForm();

        for (String url : new String[]{
                "not-a-url",
                "ftp://example.com",
                "javascript:alert(1)",
                "https://example.com/my portfolio"
        }) {
            form.setPortfolioUrl(url);

            assertThat(validator.validate(form))
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("portfolioUrl");
        }
    }

    @Test
    @DisplayName("선택 항목에 공백만 입력하면 null로 정리한다")
    void blankOptionalFields() {
        ProfileForm form = validForm();
        form.setRegion("   ");
        form.setPortfolioUrl("   ");
        form.setIntroduction("   ");

        assertThat(form.getRegion()).isNull();
        assertThat(form.getPortfolioUrl()).isNull();
        assertThat(form.getIntroduction()).isNull();
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    @DisplayName("선택 항목의 앞뒤 공백을 제거한다")
    void trimOptionalFields() {
        ProfileForm form = validForm();
        form.setRegion("  서울특별시 용산구  ");
        form.setPortfolioUrl("  https://example.com  ");
        form.setIntroduction("  백엔드 개발에 관심 있습니다.  ");

        assertThat(form.getRegion()).isEqualTo("서울특별시 용산구");
        assertThat(form.getPortfolioUrl()).isEqualTo("https://example.com");
        assertThat(form.getIntroduction())
                .isEqualTo("백엔드 개발에 관심 있습니다.");
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    @DisplayName("선택 항목이 최대 길이를 초과하면 거부한다")
    void optionalFieldsTooLong() {
        ProfileForm form = validForm();
        form.setRegion("가".repeat(101));
        form.setPortfolioUrl("https://example.com/" + "a".repeat(2000));
        form.setIntroduction("가".repeat(1001));

        assertThat(validator.validate(form))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("region", "portfolioUrl", "introduction");
    }

    @Test
    @DisplayName("기술 스택을 선택하지 않으면 검증에 실패한다")
    void rejectEmptySkills() {
        ProfileForm form = validForm();
        form.setSkills(Set.of());

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("skills");
    }

    private ProfileForm validForm() {
        ProfileForm form = new ProfileForm();
        form.setRole(Role.BACKEND);
        form.setWeeklyHours(10);
        form.setAvailableSlots(Set.of(19, 20, 21));
        form.setSkills(Set.of(Skill.JAVA));
        form.setGoal(ProjectGoal.PORTFOLIO);
        return form;
    }
}