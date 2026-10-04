package com.devmate.application.service;

import com.devmate.application.ApplicationStatus;
import com.devmate.application.ProjectApplication;
import com.devmate.application.exception.ApplicationConflictException;
import com.devmate.application.repository.ApplicationRepository;
import com.devmate.common.enums.*;
import com.devmate.member.Member;
import com.devmate.member.repository.MemberRepository;
import com.devmate.project.Project;
import com.devmate.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-concurrency-test;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApplicationConcurrencyTest {

    @Autowired
    private ApplicationService service;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private MemberRepository members;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 동시에_두_지원을_수락해도_한_명만_수락된다() throws Exception {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        Fixture fixture = transaction.execute(status -> {
            Member author = members.save(
                    new Member("concurrent-author@example.com", "hash", "작성자")
            );
            Member first = members.save(
                    new Member("concurrent-first@example.com", "hash", "지원자1")
            );
            Member second = members.save(
                    new Member("concurrent-second@example.com", "hash", "지원자2")
            );

            Project project = projects.save(new Project(
                    author,
                    "동시 수락 테스트",
                    "동시 수락 테스트용 모집글",
                    Role.BACKEND,
                    Set.of(Skill.JAVA),
                    Set.of(),
                    8,
                    1,
                    2,
                    Set.of(19, 20),
                    ProjectGoal.CONTEST,
                    MeetingType.ONLINE,
                    null,
                    LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1)
            ));

            ProjectApplication firstApplication = applications.save(
                    new ProjectApplication(project, first)
            );
            ProjectApplication secondApplication = applications.save(
                    new ProjectApplication(project, second)
            );

            applications.flush();

            return new Fixture(
                    author.getId(),
                    first.getId(),
                    second.getId(),
                    project.getId(),
                    firstApplication.getId(),
                    secondApplication.getId()
            );
        });

        assertThat(fixture).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() ->
                    acceptWhenReady(
                            fixture.firstApplicationId(),
                            fixture.authorId(),
                            ready,
                            start
                    )
            );

            Future<Boolean> second = executor.submit(() ->
                    acceptWhenReady(
                            fixture.secondApplicationId(),
                            fixture.authorId(),
                            ready,
                            start
                    )
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean firstSucceeded = first.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstSucceeded, secondSucceeded))
                    .containsExactlyInAnyOrder(true, false);

            transaction.executeWithoutResult(status -> {
                List<ProjectApplication> saved = applications.findAllById(
                        List.of(
                                fixture.firstApplicationId(),
                                fixture.secondApplicationId()
                        )
                );

                assertThat(saved)
                        .extracting(ProjectApplication::getStatus)
                        .containsExactlyInAnyOrder(
                                ApplicationStatus.ACCEPTED,
                                ApplicationStatus.REJECTED
                        );

                assertThat(saved)
                        .allSatisfy(application ->
                                assertThat(application.getDecidedAt()).isNotNull()
                        );

                assertThat(projects.findById(fixture.projectId())
                        .orElseThrow().getStatus())
                        .isEqualTo(ProjectStatus.CLOSED);
            });
        } finally {
            start.countDown();
            executor.shutdownNow();

            boolean terminated = executor.awaitTermination(
                    30, TimeUnit.SECONDS
            );

            if (terminated) {
                transaction.executeWithoutResult(status -> {
                    applications.deleteAllById(List.of(
                            fixture.firstApplicationId(),
                            fixture.secondApplicationId()
                    ));
                    applications.flush();

                    projects.deleteById(fixture.projectId());
                    projects.flush();

                    members.deleteAllById(List.of(
                            fixture.authorId(),
                            fixture.firstMemberId(),
                            fixture.secondMemberId()
                    ));
                    members.flush();
                });
            }

            assertThat(terminated)
                    .as("동시 요청 스레드가 종료되어야 한다")
                    .isTrue();
        }
    }

    private boolean acceptWhenReady(
            Long applicationId,
            Long authorId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 실행 대기 시간 초과");
        }

        try {
            service.accept(applicationId, authorId);
            return true;
        } catch (ApplicationConflictException exception) {
            return false;
        }
    }

    private record Fixture(
            Long authorId,
            Long firstMemberId,
            Long secondMemberId,
            Long projectId,
            Long firstApplicationId,
            Long secondApplicationId
    ) {
    }
}
