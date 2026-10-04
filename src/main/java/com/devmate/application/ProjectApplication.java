package com.devmate.application;

import com.devmate.member.Member;
import com.devmate.project.Project;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(
        name = "project_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_application_project_applicant",
                columnNames = {"project_id", "applicant_id"}
        )
)
public class ProjectApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private Member applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime decidedAt;

    protected ProjectApplication() {
    }

    public ProjectApplication(Project project, Member applicant) {
        this.project = Objects.requireNonNull(project, "모집글이 필요합니다.");
        this.applicant = Objects.requireNonNull(applicant, "지원자가 필요합니다.");
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void accept(LocalDateTime now) {
        decide(ApplicationStatus.ACCEPTED, now);
    }

    public void reject(LocalDateTime now) {
        decide(ApplicationStatus.REJECTED, now);
    }

    private void decide(ApplicationStatus nextStatus, LocalDateTime now) {
        Objects.requireNonNull(now, "처리 시각이 필요합니다.");

        if (status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 지원입니다.");
        }

        this.status = nextStatus;
        this.decidedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Member getApplicant() {
        return applicant;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}
