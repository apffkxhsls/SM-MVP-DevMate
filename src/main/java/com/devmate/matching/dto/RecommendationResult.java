package com.devmate.matching.dto;

import com.devmate.matching.MatchStatus;
import org.springframework.data.domain.Page;

public record RecommendationResult(
        Page<RecommendationCard> projectPage,  // 현재 페이지 카드 목록
        MatchStatus selectedStatus,  // 선택한 PASS, FAIL, UNKNOWN 탭
        boolean profileRequired  // 프로필 작성 안내 표시 여부
) {
}
