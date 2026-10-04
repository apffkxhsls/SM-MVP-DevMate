package com.devmate.application.repository;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository
        extends JpaRepository<ProjectApplication, Long> {

    /** 동일 회원이 이미 지원했는지 확인한다. */
    boolean existsByProject_IdAndApplicant_Id(
            Long projectId,
            Long applicantId
    );

    /** 본인의 지원 목록을 조회한다. */
    Page<ProjectApplication> findByApplicant_Id(
            Long applicantId,
            Pageable pageable
    );

    /** 특정 모집글의 지원 목록을 조회한다. */
    Page<ProjectApplication> findByProject_Id(
            Long projectId,
            Pageable pageable
    );

    /** 특정 모집글의 대기 중인 지원 목록을 조회한다. */
    List<ProjectApplication> findByProject_IdAndStatus(
            Long projectId,
            ApplicationStatus status
    );

    /** 지원 엔티티를 읽기 전에 잠글 모집글 ID를 확인한다. */
    @Query("""
            select a.project.id
            from ProjectApplication a
            where a.id = :applicationId
            """)
    Optional<Long> findProjectIdByApplicationId(
            @Param("applicationId") Long applicationId
    );
}