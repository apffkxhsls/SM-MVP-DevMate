package com.devmate.application.service;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import com.devmate.application.dto.ApplicationView;
import com.devmate.application.exception.ApplicationConflictException;
import com.devmate.application.repository.ApplicationRepository;
import com.devmate.matching.MatchResult;
import com.devmate.matching.MatchStatus;
import com.devmate.matching.service.MatchingService;
import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import com.devmate.profile.Profile;
import com.devmate.profile.repository.ProfileRepository;
import com.devmate.project.Project;
import com.devmate.project.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Validated
@Transactional(readOnly = true)
public class ApplicationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ApplicationRepository applicationRepository;
    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final MatchingService matchingService;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ProjectRepository projectRepository,
            MemberRepository memberRepository,
            ProfileRepository profileRepository,
            MatchingService matchingService
    ) {
        this.applicationRepository = applicationRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.profileRepository = profileRepository;
        this.matchingService = matchingService;
    }

    /**
     * 로그인한 회원의 프로젝트 지원을 처리한다.
     * applicantId는 요청 폼이 아닌 인증 정보에서 가져온다.
     */
    @Transactional
    public Long apply(
            @NotNull @Positive Long projectId,
            @NotNull @Positive Long applicantId
    ) {
        // 같은 모집글에 대한 지원·수락·거절은 이 잠금을 먼저 얻는다.
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() ->
                        new EntityNotFoundException("모집글을 찾을 수 없습니다.")
                );

        if (project.getAuthor().getId().equals(applicantId)) {
            throw new ApplicationConflictException(
                    "본인이 작성한 모집글에는 지원할 수 없습니다."
            );
        }

        // 잠금을 기다리는 동안 마감됐을 수 있으므로 획득 후 확인한다.
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);

        if (!project.isRecruiting(now)) {
            throw new ApplicationConflictException(
                    "모집이 마감된 프로젝트입니다."
            );
        }

        if (applicationRepository.existsByProject_IdAndApplicant_Id(
                projectId, applicantId
        )) {
            throw new ApplicationConflictException(
                    "이미 지원한 모집글입니다."
            );
        }

        Member applicant = memberRepository.findById(applicantId)
                .orElseThrow(() ->
                        new EntityNotFoundException("회원을 찾을 수 없습니다.")
                );

        Profile profile = profileRepository.findByMember_Id(applicantId)
                .orElse(null);

        MatchResult match = matchingService.calculate(profile, project);

        if (match.status() != MatchStatus.PASS) {
            throw new ApplicationConflictException(
                    String.join(" ", match.reasons())
            );
        }

        ProjectApplication application =
                new ProjectApplication(project, applicant);

        return applicationRepository.saveAndFlush(application).getId();
    }

    /**
     * 지원자를 수락하고 모집을 마감한다.
     * 나머지 대기 지원은 함께 거절한다.
     */
    @Transactional
    public void accept(
            @NotNull @Positive Long applicationId,
            @NotNull @Positive Long authorId
    ) {
        Project project = lockProjectForApplication(applicationId);

        validateAuthor(project, authorId);

        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);

        if (!project.isRecruiting(now)) {
            throw new ApplicationConflictException(
                    "모집이 마감되어 지원자를 수락할 수 없습니다."
            );
        }

        ProjectApplication application = findApplication(applicationId);
        validatePending(application);

        application.accept(now);
        project.close();

        applicationRepository.findByProject_IdAndStatus(
                project.getId(),
                ApplicationStatus.PENDING
        ).forEach(pending -> {
            // 조회 전 flush 여부와 관계없이 수락한 지원은 제외한다.
            if (!pending.getId().equals(applicationId)) {
                pending.reject(now);
            }
        });
    }

    /**
     * 대기 중인 지원을 거절한다.
     * 모집 기한이 지나도 남아 있는 대기 지원은 거절할 수 있다.
     */
    @Transactional
    public void reject(
            @NotNull @Positive Long applicationId,
            @NotNull @Positive Long authorId
    ) {
        Project project = lockProjectForApplication(applicationId);

        validateAuthor(project, authorId);

        ProjectApplication application = findApplication(applicationId);
        validatePending(application);

        application.reject(LocalDateTime.now(KOREA_ZONE));
    }

    /**
     * 로그인한 회원 본인의 지원 목록을 조회한다.
     * applicantId는 인증 정보에서 가져온다.
     */
    public Page<ApplicationView> getMyApplications(
            @NotNull @Positive Long applicantId,
            @Min(0) int page
    ) {
        if (!memberRepository.existsById(applicantId)) {
            throw new EntityNotFoundException("회원을 찾을 수 없습니다.");
        }

        return applicationRepository.findByApplicant_Id(
                applicantId,
                applicationPageRequest(page)
        ).map(ApplicationView::from);
    }

    /**
     * 모집글 작성자가 해당 모집글의 지원자 목록을 조회한다.
     * authorId는 인증 정보에서 가져온다.
     */
    public Page<ApplicationView> getProjectApplications(
            @NotNull @Positive Long projectId,
            @NotNull @Positive Long authorId,
            @Min(0) int page
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new EntityNotFoundException("모집글을 찾을 수 없습니다.")
                );

        validateAuthor(project, authorId);

        return applicationRepository.findByProject_Id(
                projectId,
                applicationPageRequest(page)
        ).map(ApplicationView::from);
    }

    /**
     * 지원에 연결된 모집글 ID를 조회한다.
     * 해당 모집글 작성자만 조회할 수 있다.
     */
    public Long getProjectIdForAuthor(
            @NotNull @Positive Long applicationId,
            @NotNull @Positive Long authorId
    ) {
        Long projectId = applicationRepository
                .findProjectIdByApplicationId(applicationId)
                .orElseThrow(() ->
                        new EntityNotFoundException("지원 내역을 찾을 수 없습니다.")
                );

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new EntityNotFoundException("모집글을 찾을 수 없습니다.")
                );

        validateAuthor(project, authorId);

        return projectId;
    }

    private PageRequest applicationPageRequest(int page) {
        return PageRequest.of(
                page,
                10,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }

    /** 모집글을 잠근 뒤 지원 엔티티를 읽도록 순서를 통일한다. */
    private Project lockProjectForApplication(Long applicationId) {
        Long projectId = applicationRepository
                .findProjectIdByApplicationId(applicationId)
                .orElseThrow(() ->
                        new EntityNotFoundException("지원 내역을 찾을 수 없습니다.")
                );

        return projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() ->
                        new EntityNotFoundException("모집글을 찾을 수 없습니다.")
                );
    }

    private ProjectApplication findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() ->
                        new EntityNotFoundException("지원 내역을 찾을 수 없습니다.")
                );
    }

    private void validateAuthor(Project project, Long authorId) {
        if (!project.getAuthor().getId().equals(authorId)) {
            throw new AccessDeniedException(
                    "모집글 작성자만 지원 내역을 조회하거나 처리할 수 있습니다."
            );
        }
    }

    private void validatePending(ProjectApplication application) {
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ApplicationConflictException(
                    "이미 처리된 지원입니다."
            );
        }
    }
}
