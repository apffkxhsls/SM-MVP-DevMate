package com.devmate.profile.service;

import com.devmate.application.repository.ApplicationRepository;
import com.devmate.profile.Profile;
import com.devmate.profile.dto.ApplicantProfileView;
import com.devmate.profile.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ApplicantProfileService {

    private final ProfileRepository profileRepository;
    private final ApplicationRepository applicationRepository;

    public ApplicantProfileService(
            ProfileRepository profileRepository,
            ApplicationRepository applicationRepository
    ) {
        this.profileRepository = profileRepository;
        this.applicationRepository = applicationRepository;
    }

    public ApplicantProfileView getApplicantProfile(
            Long applicantId,
            Long viewerId
    ) {
        if (viewerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        if (applicantId == null || applicantId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 회원 ID가 필요합니다."
            );
        }

        boolean allowed =
                applicationRepository.existsByApplicant_IdAndProject_Author_Id(
                        applicantId,
                        viewerId
                );

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "지원자 프로필을 조회할 권한이 없습니다."
            );
        }

        Profile profile = profileRepository.findByMember_Id(applicantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "지원자의 프로필을 찾을 수 없습니다."
                ));

        return ApplicantProfileView.from(profile);
    }
}
