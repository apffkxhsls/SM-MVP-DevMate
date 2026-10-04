package com.devmate.application.service;

import com.devmate.application.ProjectApplication;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
}
