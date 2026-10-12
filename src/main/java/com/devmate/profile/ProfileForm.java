package com.devmate.profile;

import com.devmate.common.enums.ProjectGoal;
import com.devmate.common.enums.Role;
import com.devmate.common.enums.Skill;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.HashSet;
import java.util.Set;

public class ProfileForm {

    @NotNull(message = "희망 역할을 선택해주세요.")
    private Role role;

    @NotEmpty(message = "보유 기술 스택을 1개 이상 선택해주세요.")
    private Set<Skill> skills = new HashSet<>();

    @NotNull(message = "주당 투자 가능 시간을 입력해주세요.")
    @Min(value = 1, message = "주당 투자 가능 시간은 1시간 이상이어야 합니다.")
    @Max(value = 168, message = "주당 투자 가능 시간은 168시간 이하여야 합니다.")
    private Integer weeklyHours;

    @NotEmpty(message = "활동 가능 시간대를 1개 이상 선택해주세요.")
    private Set<
            @NotNull(message = "시간대 값이 누락되었습니다.")
            @Min(value = 0, message = "유효하지 않은 시간대입니다.")
            @Max(value = 167, message = "유효하지 않은 시간대입니다.")
                    Integer> availableSlots = new HashSet<>();

    @NotNull(message = "프로젝트 목표를 선택해주세요.")
    private ProjectGoal goal;

    @Size(max = 100, message = "지역은 100자 이하로 입력해주세요.")
    private String region;

    @Size(max = 2000, message = "포트폴리오 링크는 2000자 이하로 입력해주세요.")
    @URL(
            regexp = "(?i)^https?://[^\\s]+$",
            message = "http:// 또는 https://로 시작하는 올바른 주소를 입력해주세요."
    )
    private String portfolioUrl;

    @Size(max = 1000, message = "자기소개는 1000자 이하로 입력해주세요.")
    private String introduction;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Set<Skill> getSkills() {
        return skills;
    }

    public void setSkills(Set<Skill> skills) {
        this.skills = skills == null
                ? new HashSet<>()
                : new HashSet<>(skills);
    }

    public Integer getWeeklyHours() {
        return weeklyHours;
    }

    public void setWeeklyHours(Integer weeklyHours) {
        this.weeklyHours = weeklyHours;
    }

    public Set<Integer> getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(Set<Integer> availableSlots) {
        this.availableSlots = availableSlots == null
                ? new HashSet<>()
                : new HashSet<>(availableSlots);
    }

    public ProjectGoal getGoal() {
        return goal;
    }

    public void setGoal(ProjectGoal goal) {
        this.goal = goal;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = normalizeOptionalText(region);
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = normalizeOptionalText(portfolioUrl);
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = normalizeOptionalText(introduction);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
