package com.devmate.matching.dto;

import com.devmate.common.enums.MeetingType;
import com.devmate.common.enums.Role;
import com.devmate.common.enums.Skill;
import com.devmate.matching.MatchResult;
import com.devmate.project.Project;

import java.time.LocalDateTime;
import java.util.Set;

public record RecommendationCard(
        Long projectId,
        String title,
        Role role,
        Set<Skill> requiredSkills,
        Set<Skill> preferredSkills,
        MeetingType meetingType,
        String region,
        LocalDateTime deadline,
        LocalDateTime createdAt,
        MatchResult match
) {
    public RecommendationCard {
        requiredSkills = Set.copyOf(requiredSkills);
        preferredSkills = Set.copyOf(preferredSkills);
    }

    public static RecommendationCard from(
            Project project,
            MatchResult match
    ) {
        return new RecommendationCard(
                project.getId(),
                project.getTitle(),
                project.getRole(),
                project.getRequiredSkills(),
                project.getPreferredSkills(),
                project.getMeetingType(),
                project.getRegion(),
                project.getDeadline(),
                project.getCreatedAt(),
                match
        );
    }
}
