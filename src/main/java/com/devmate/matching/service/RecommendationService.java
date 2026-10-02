package com.devmate.matching.service;

import com.devmate.common.enums.ProjectStatus;
import com.devmate.matching.MatchResult;
import com.devmate.matching.MatchStatus;
import com.devmate.matching.dto.RecommendationCard;
import com.devmate.matching.dto.RecommendationResult;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int PAGE_SIZE = 10;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final MatchingService matchingService;

    public RecommendationService(
            MemberRepository memberRepository,
            ProfileRepository profileRepository,
            ProjectRepository projectRepository,
            MatchingService matchingService
    ) {
        this.memberRepository = memberRepository;
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.matchingService = matchingService;
    }

    public RecommendationResult getRecommendations(
            @NotNull @Positive Long memberId,
            @NotNull MatchStatus status,
            @Min(0) int page
    ) {
        if (!memberRepository.existsById(memberId)) {
            throw new EntityNotFoundException("회원을 찾을 수 없습니다.");
        }

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE);

        Profile profile = profileRepository.findByMember_Id(memberId)
                .orElse(null);

        if (profile == null) {
            return new RecommendationResult(
                    Page.<RecommendationCard>empty(pageable),
                    status,
                    true
            );
        }

        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);

        List<Project> candidates =
                projectRepository.findRecommendationCandidates(
                        memberId,
                        ProjectStatus.OPEN,
                        now
                );

        List<RecommendationCard> sortedCards = candidates.stream()
                .map(project -> {
                    MatchResult match =
                            matchingService.calculate(profile, project);

                    return RecommendationCard.from(project, match);
                })
                .filter(card -> card.match().status() == status)
                .sorted(comparator(status))
                .toList();

        // offset은 long이므로 범위를 확인한 뒤 int로 변환한다.
        long offset = pageable.getOffset();
        int total = sortedCards.size();

        List<RecommendationCard> content;

        if (offset >= total) {
            content = List.of();
        } else {
            int start = (int) offset;
            int end = (int) Math.min(offset + PAGE_SIZE, (long) total);
            content = sortedCards.subList(start, end);
        }

        Page<RecommendationCard> projectPage = new PageImpl<>(
                content,
                pageable,
                total
        );

        return new RecommendationResult(
                projectPage,
                status,
                false
        );
    }

    private Comparator<RecommendationCard> comparator(MatchStatus status) {
        Comparator<RecommendationCard> latestFirst =
                Comparator.comparing(
                        RecommendationCard::createdAt,
                        Comparator.reverseOrder()
                ).thenComparing(
                        RecommendationCard::projectId,
                        Comparator.reverseOrder()
                );

        if (status == MatchStatus.PASS) {
            return Comparator
                    .comparingInt(
                            (RecommendationCard card) -> card.match().score()
                    )
                    .reversed()
                    .thenComparing(latestFirst);
        }

        return latestFirst;
    }
}
