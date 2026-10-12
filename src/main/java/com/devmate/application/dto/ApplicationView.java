package com.devmate.application.dto;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;

import java.time.LocalDateTime;

public record ApplicationView(
        Long applicationId,
        Long projectId,
        String projectTitle,
        Long applicantId,
        String applicantName,
        ApplicationStatus status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {

    public static ApplicationView from(ProjectApplication application) {
        return new ApplicationView(
                application.getId(),
                application.getProject().getId(),
                application.getProject().getTitle(),
                application.getApplicant().getId(),
                application.getApplicant().getName(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getDecidedAt()
        );
    }
}
