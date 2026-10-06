package com.devmate.project;

import com.devmate.common.enums.*;
import com.devmate.member.Member;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private Member author;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ElementCollection
    @CollectionTable(
            name = "project_required_skills",
            joinColumns = @JoinColumn(name = "project_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"project_id", "skill"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false, length = 30)
    private Set<Skill> requiredSkills = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "project_preferred_skills",
            joinColumns = @JoinColumn(name = "project_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"project_id", "skill"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false, length = 30)
    private Set<Skill> preferredSkills = new HashSet<>();

    @Column(nullable = false)
    private Integer requiredWeeklyHours;

    @Column(nullable = false)
    private Integer minimumCommonHours;

    @Column(nullable = false)
    private Integer desiredCommonHours;

    @ElementCollection
    @CollectionTable(
            name = "project_available_slots",
            joinColumns = @JoinColumn(name = "project_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"project_id", "slot"}
            )
    )
    @Column(name = "slot", nullable = false)
    private Set<Integer> availableSlots = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingType meetingType;

    @Column(length = 100)
    private String region;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Project() {
    }

    /** 검증된 입력값으로 모집글을 생성한다. */
    public Project(
            Member author,
            String title,
            String description,
            Role role,
            Set<Skill> requiredSkills,
            Set<Skill> preferredSkills,
            Integer requiredWeeklyHours,
            Integer minimumCommonHours,
            Integer desiredCommonHours,
            Set<Integer> availableSlots,
            ProjectGoal goal,
            MeetingType meetingType,
            String region,
            LocalDateTime deadline
    ) {
        this.author = author;
        this.title = title.strip();
        this.description = description.strip();
        this.role = role;
        this.requiredSkills = new HashSet<>(requiredSkills);
        this.preferredSkills = new HashSet<>(preferredSkills);
        this.requiredWeeklyHours = requiredWeeklyHours;
        this.minimumCommonHours = minimumCommonHours;
        this.desiredCommonHours = desiredCommonHours;
        this.availableSlots = new HashSet<>(availableSlots);
        this.goal = goal;
        this.meetingType = meetingType;

        // 비대면 모집글에는 지역을 저장하지 않는다.
        this.region = meetingType == MeetingType.OFFLINE
                ? region.strip()
                : null;

        this.deadline = deadline;
    }

    /** 지원자를 수락한 모집글을 마감한다. */
    public void close() {
        this.status = ProjectStatus.CLOSED;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    /** 마감 시각과 같거나 이후면 지원할 수 없다. */
    public boolean isRecruiting(LocalDateTime now) {
        return status == ProjectStatus.OPEN && deadline.isAfter(now);
    }

    public Set<Skill> getRequiredSkills() {
        return Collections.unmodifiableSet(requiredSkills);
    }

    public Set<Skill> getPreferredSkills() {
        return Collections.unmodifiableSet(preferredSkills);
    }

    public Set<Integer> getAvailableSlots() {
        return Collections.unmodifiableSet(availableSlots);
    }

    public Long getId() {
        return id;
    }

    public Member getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Role getRole() {
        return role;
    }

    public Integer getRequiredWeeklyHours() {
        return requiredWeeklyHours;
    }

    public Integer getMinimumCommonHours() {
        return minimumCommonHours;
    }

    public Integer getDesiredCommonHours() {
        return desiredCommonHours;
    }

    public ProjectGoal getGoal() {
        return goal;
    }

    public MeetingType getMeetingType() {
        return meetingType;
    }

    public String getRegion() {
        return region;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
