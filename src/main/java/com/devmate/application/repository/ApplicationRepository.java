package com.devmate.application.repository;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
    @EntityGraph(attributePaths = {"project", "applicant"})
    Page<ProjectApplication> findByApplicant_Id(
            Long applicantId,
            Pageable pageable
    );


    /** 특정 모집글의 지원 목록을 조회한다. */
    @EntityGraph(attributePaths = {"project", "applicant"})
    Page<ProjectApplication> findByProject_Id(
            Long projectId,
            Pageable pageable
    );

    /** 특정 모집글의 대기 중인 지원 목록을 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProjectApplication> findByProject_IdAndStatus(
            Long projectId,
            ApplicationStatus status
    );

    /** 모집글 잠금을 얻은 뒤, 변경할 지원을 잠금 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from ProjectApplication a
        where a.id = :applicationId
        """)
    Optional<ProjectApplication> findByIdForUpdate(
            @Param("applicationId") Long applicationId
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

    /** 지원에 연결된 모집글 작성자 ID만 조회한다. */
    @Query("""
        select a.project.author.id
        from ProjectApplication a
        where a.id = :applicationId
        """)
    Optional<Long> findAuthorIdByApplicationId(
            @Param("applicationId") Long applicationId
    );
}