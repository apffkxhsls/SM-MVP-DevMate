package com.devmate.profile;

import com.devmate.member.security.MemberPrincipal;
import com.devmate.profile.dto.ApplicantProfileView;
import com.devmate.profile.service.ApplicantProfileService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicantProfileController {

    private final ApplicantProfileService applicantProfileService;

    public ApplicantProfileController(
            ApplicantProfileService applicantProfileService
    ) {
        this.applicantProfileService = applicantProfileService;
    }

    /**
     * 본인 모집글에 지원한 회원의 프로필을 JSON으로 조회한다.
     */
    @GetMapping(
            value = "/members/{memberId}/profile",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApplicantProfileView getApplicantProfile(
            @PathVariable("memberId") Long memberId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return applicantProfileService.getApplicantProfile(
                memberId,
                principal == null ? null : principal.getMemberId()
        );
    }
}
