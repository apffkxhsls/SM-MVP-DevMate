package com.devmate.project.service;

import com.devmate.application.repository.ApplicationRepository;
import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import com.devmate.project.Project;
import com.devmate.project.ProjectForm;
import com.devmate.project.dto.ProjectView;
import com.devmate.project.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Validated
@Transactional(readOnly = true)
public class ProjectService {

    private static final int PAGE_SIZE = 10;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final ApplicationRepository applicationRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            MemberRepository memberRepository,
            ApplicationRepository applicationRepository
    ) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * 로그인한 회원을 작성자로 모집글을 등록한다.
     * authorId는 요청 폼이 아닌 인증 정보에서 가져와야 한다.
     */
    @Transactional
    public Long create(
            @NotNull @Positive Long authorId,
            @NotNull @Valid ProjectForm form
    ) {
        Member author = findMember(authorId);

        Project project = new Project(
                author,
                form.getTitle(),
                form.getDescription(),
                form.getRole(),
                form.getRequiredSkills(),
                form.getPreferredSkills(),
                form.getRequiredWeeklyHours(),
                form.getMinimumCommonHours(),
                form.getDesiredCommonHours(),
                form.getAvailableSlots(),
                form.getGoal(),
                form.getMeetingType(),
                form.getRegion(),
                form.getDeadline()
        );

        return projectRepository.saveAndFlush(project).getId();
    }

    /** 전체 모집글을 최신순으로 조회한다. 페이지 번호는 0부터 시작한다. */
    public Page<ProjectView> getProjects(@Min(0) int page) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);

        return projectRepository.findAll(pageRequest(page))
                .map(project -> ProjectView.from(project, now));
    }

    /** 모집글 하나의 상세 정보를 조회한다. */
    public ProjectView getProject(@NotNull @Positive Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new EntityNotFoundException("모집글을 찾을 수 없습니다.")
                );

        return ProjectView.from(
                project,
                LocalDateTime.now(KOREA_ZONE)
        );
    }

    /** 해당 회원이 작성한 모집글만 최신순으로 조회한다. */
    public Page<ProjectView> getMyProjects(
            @NotNull @Positive Long memberId,
            @Min(0) int page
    ) {
        findMember(memberId);
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);

        return projectRepository.findByAuthor_Id(
                        memberId,
                        pageRequest(page)
                )
                .map(project -> ProjectView.from(project, now));
    }

    /**
     * 로그인 회원의 지원 이력 여부를 확인한다.
     * 거절된 지원도 재지원할 수 없으므로 모든 상태를 포함한다.
     */
    public boolean hasApplied(
            @NotNull @Positive Long projectId,
            @NotNull @Positive Long memberId
    ) {
        return applicationRepository.existsByProject_IdAndApplicant_Id(
                projectId,
                memberId
        );
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new EntityNotFoundException("회원을 찾을 수 없습니다.")
                );
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(
                page,
                PAGE_SIZE,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }
}
