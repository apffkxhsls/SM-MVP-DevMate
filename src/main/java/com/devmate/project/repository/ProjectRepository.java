package com.devmate.project.repository;

import com.devmate.common.enums.ProjectStatus;
import com.devmate.project.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 특정 회원이 작성한 모집글을 페이지 단위로 조회한다.
     */
    Page<Project> findByAuthor_Id(Long authorId, Pageable pageable);

    /**
     * 모집 중이며 마감 전인 다른 회원의 모집글을 조회한다.
     */
    @Query("""
            select p
            from Project p
            where p.status = :status
              and p.deadline > :now
              and p.author.id <> :memberId
            """)
    List<Project> findRecommendationCandidates(
            @Param("memberId") Long memberId,
            @Param("status") ProjectStatus status,
            @Param("now") LocalDateTime now
    );
}