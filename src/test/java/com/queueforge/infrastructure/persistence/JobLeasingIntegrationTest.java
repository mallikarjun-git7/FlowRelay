
package com.queueforge.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueforge.TestcontainersConfiguration;
import com.queueforge.domain.Job;
import com.queueforge.domain.JobPriority;
import com.queueforge.domain.JobStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "queueforge.worker.enabled=false",
    "queueforge.recovery.enabled=false",
    "queueforge.cleanup.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class JobLeasingIntegrationTest {

    private static final Duration LEASE_DURATION =
            Duration.ofSeconds(60);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private org.testcontainers.containers.PostgreSQLContainer<?>
            postgresContainer;

    @BeforeEach
    void setUp() throws Exception {

        // Prevent accidental deletion from a local database.
        assertThat(postgresContainer.isRunning())
                .as("Testcontainers PostgreSQL must be running")
                .isTrue();

        String expectedUrl = postgresContainer.getJdbcUrl();

        try (Connection connection = dataSource.getConnection()) {
            String actualUrl = connection.getMetaData().getURL();

            assertThat(actualUrl)
                    .as("Tests must use the Testcontainers JDBC URL")
                    .isEqualTo(expectedUrl);
        }

        // Clean only the verified isolated test database.
        jdbcTemplate.update("DELETE FROM job_events");
        jdbcTemplate.update("DELETE FROM jobs");
    }

    @Test
    void shouldLeaseJobAndSetRunning() throws Exception {
        UUID id = submitPendingJob("queue-a", "email.send");

        Optional<Job> leased = jobRepository.leaseNextAvailable(
                "worker-1", "queue-a", LEASE_DURATION);

        assertThat(leased).isPresent();
        assertThat(leased.get().id()).isEqualTo(id);
        assertThat(leased.get().status())
                .isEqualTo(JobStatus.RUNNING);
        assertThat(leased.get().lockedBy())
                .isEqualTo("worker-1");
        assertThat(leased.get().lockedUntil())
                .isAfter(Instant.now());
        assertThat(leased.get().attempts())
                .isEqualTo(1);
    }

    @Test
    void shouldIncrementAttemptsOnLease() throws Exception {
        UUID id = submitPendingJob(
                "queue-b", "report.generate");

        Optional<Job> first = jobRepository.leaseNextAvailable(
                "w1", "queue-b", LEASE_DURATION);

        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo(id);
        assertThat(first.get().attempts()).isEqualTo(1);

        Optional<Job> second = jobRepository.leaseNextAvailable(
                "w1", "queue-b", LEASE_DURATION);

        assertThat(second).isEmpty();
    }

    @Test
    void shouldLeaseRetryScheduledJobAfterRunAt()
            throws Exception {

        UUID id = submitJob(
                "queue-c",
                "webhook",
                JobStatus.RETRY_SCHEDULED,
                Instant.now().minus(1, ChronoUnit.MINUTES)
        );

        Optional<Job> leased = jobRepository.leaseNextAvailable(
                "w1", "queue-c", LEASE_DURATION);

        assertThat(leased).isPresent();
        assertThat(leased.get().id()).isEqualTo(id);
        assertThat(leased.get().status())
                .isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void shouldNotLeaseFutureJob() throws Exception {
        submitJob(
                "queue-d",
                "future.job",
                JobStatus.PENDING,
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        Optional<Job> leased = jobRepository.leaseNextAvailable(
                "w1", "queue-d", LEASE_DURATION);

        assertThat(leased).isEmpty();
    }

    @Test
    void shouldNotLeaseRunningJob() throws Exception {
        UUID id = submitPendingJob(
                "queue-e", "test.type");

        Optional<Job> firstLease =
                jobRepository.leaseNextAvailable(
                        "w1", "queue-e", LEASE_DURATION);

        assertThat(firstLease).isPresent();
        assertThat(firstLease.get().id()).isEqualTo(id);
        assertThat(firstLease.get().lockedBy())
                .isEqualTo("w1");

        Optional<Job> secondLease =
                jobRepository.leaseNextAvailable(
                        "w2", "queue-e", LEASE_DURATION);

        assertThat(secondLease).isEmpty();
    }

    @Test
    void shouldNotLeaseSameJobToDifferentWorkers()
            throws Exception {

        int jobCount = 20;

        for (int i = 0; i < jobCount; i++) {
            submitPendingJob(
                    "concurrent-queue", "parallel.task");
        }

        AtomicInteger w1Count = new AtomicInteger(0);
        AtomicInteger w2Count = new AtomicInteger(0);

        Set<UUID> leasedIds =
                Collections.synchronizedSet(new HashSet<>());

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch latch = new CountDownLatch(2);

        Runnable worker1 = () -> {
            try {
                for (int i = 0; i < 15; i++) {
                    Optional<Job> job =
                            jobRepository.leaseNextAvailable(
                                    "worker-A",
                                    "concurrent-queue",
                                    LEASE_DURATION);

                    if (job.isPresent()) {
                        UUID jobId = job.get().id();
                        if (leasedIds.add(jobId)) {
                            w1Count.incrementAndGet();
                        }
                    }
                }
            } finally {
                latch.countDown();
            }
        };

        Runnable worker2 = () -> {
            try {
                for (int i = 0; i < 15; i++) {
                    Optional<Job> job =
                            jobRepository.leaseNextAvailable(
                                    "worker-B",
                                    "concurrent-queue",
                                    LEASE_DURATION);

                    if (job.isPresent()) {
                        UUID jobId = job.get().id();
                        if (leasedIds.add(jobId)) {
                            w2Count.incrementAndGet();
                        }
                    }
                }
            } finally {
                latch.countDown();
            }
        };

        try {
            executor.submit(worker1);
            executor.submit(worker2);

            assertThat(latch.await(30, TimeUnit.SECONDS))
                    .isTrue();

            int totalLeased =
                    w1Count.get() + w2Count.get();

            assertThat(totalLeased).isEqualTo(jobCount);
            assertThat(leasedIds).hasSize(jobCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldMarkCompletedClearingLock()
            throws Exception {

        UUID id = submitPendingJob(
                "queue-f", "cleanup.task");

        Optional<Job> leased =
                jobRepository.leaseNextAvailable(
                        "w1", "queue-f", LEASE_DURATION);

        assertThat(leased).isPresent();
        assertThat(leased.get().id()).isEqualTo(id);

        jobRepository.markCompleted(id, "w1");

        Job job = jobRepository.findById(id)
                .orElseThrow();

        assertThat(job.status())
                .isEqualTo(JobStatus.COMPLETED);
        assertThat(job.lockedBy()).isNull();
        assertThat(job.lockedUntil()).isNull();
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void shouldMarkFailedAndScheduleRetry()
            throws Exception {

        UUID id = submitPendingJob(
                "queue-g", "flaky.task");

        Optional<Job> leased =
                jobRepository.leaseNextAvailable(
                        "w1", "queue-g", LEASE_DURATION);

        assertThat(leased).isPresent();
        assertThat(leased.get().id()).isEqualTo(id);

        Instant nextRetry =
                Instant.now().plusSeconds(30);

        jobRepository.markFailed(
                id,
                "w1",
                "Connection timeout",
                nextRetry
        );

        Job job = jobRepository.findById(id)
                .orElseThrow();

        assertThat(job.status())
                .isEqualTo(JobStatus.RETRY_SCHEDULED);
        assertThat(job.lastError())
                .isEqualTo("Connection timeout");
        assertThat(job.lockedBy()).isNull();
        assertThat(job.lockedUntil()).isNull();
    }

    @Test
    void shouldMoveToDeadLetter()
            throws Exception {

        UUID id = submitJob(
                "queue-h",
                "broken.task",
                JobStatus.PENDING,
                readyAt(),
                1
        );

        Optional<Job> leased =
                jobRepository.leaseNextAvailable(
                        "w1", "queue-h", LEASE_DURATION);

        assertThat(leased).isPresent();
        assertThat(leased.get().id()).isEqualTo(id);

        jobRepository.moveToDeadLetter(
                id, "w1", "Fatal error");

        Job job = jobRepository.findById(id)
                .orElseThrow();

        assertThat(job.status())
                .isEqualTo(JobStatus.DEAD_LETTERED);
        assertThat(job.lastError())
                .isEqualTo("Fatal error");
        assertThat(job.deadLetteredAt())
                .isNotNull();
    }

    @Test
    void shouldReleaseExpiredLeases()
            throws Exception {

        UUID id = submitPendingJob(
                "queue-i", "stale.task");

        String sql = """
            UPDATE jobs
            SET status = 'RUNNING',
                locked_by = 'dead-worker',
                locked_until = now() - interval '1 minute',
                updated_at = now()
            WHERE id = ?
            """;

        jdbcTemplate.update(sql, id);

        int recovered =
                jobRepository.releaseExpiredLeases();

        assertThat(recovered).isEqualTo(1);

        Job job = jobRepository.findById(id)
                .orElseThrow();

        assertThat(job.status())
                .isEqualTo(JobStatus.PENDING);
        assertThat(job.lockedBy()).isNull();
        assertThat(job.lastError())
                .contains("expired");
    }

    @Test
    void shouldRespectPriorityOrderOnLease()
            throws Exception {

        Instant runAt = readyAt();

        submitJob(
                "priority-queue",
                "task",
                JobStatus.PENDING,
                runAt,
                3,
                JobPriority.LOW
        );

        submitJob(
                "priority-queue",
                "task",
                JobStatus.PENDING,
                runAt,
                3,
                JobPriority.NORMAL
        );

        submitJob(
                "priority-queue",
                "task",
                JobStatus.PENDING,
                runAt,
                3,
                JobPriority.HIGH
        );

        UUID criticalId = submitJob(
                "priority-queue",
                "task",
                JobStatus.PENDING,
                runAt,
                3,
                JobPriority.CRITICAL
        );

        Optional<Job> first =
                jobRepository.leaseNextAvailable(
                        "w1",
                        "priority-queue",
                        LEASE_DURATION
                );

        assertThat(first).isPresent();
        assertThat(first.get().id())
                .isEqualTo(criticalId);
    }

    private Instant readyAt() {
        return Instant.now().minusSeconds(5);
    }

    private UUID submitPendingJob(
            String queue,
            String type) throws Exception {

        return submitJob(
                queue,
                type,
                JobStatus.PENDING,
                readyAt()
        );
    }

    private UUID submitJob(
            String queue,
            String type,
            JobStatus status,
            Instant runAt) throws Exception {

        return submitJob(
                queue,
                type,
                status,
                runAt,
                3,
                JobPriority.NORMAL
        );
    }

    private UUID submitJob(
            String queue,
            String type,
            JobStatus status,
            Instant runAt,
            int maxAttempts) throws Exception {

        return submitJob(
                queue,
                type,
                status,
                runAt,
                maxAttempts,
                JobPriority.NORMAL
        );
    }

    private UUID submitJob(
            String queue,
            String type,
            JobStatus status,
            Instant runAt,
            int maxAttempts,
            JobPriority priority) throws Exception {

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Job job = new Job(
                id,
                queue,
                type,
                objectMapper.readTree(
                        "{\"msg\":\"test\"}"),
                status,
                priority,
                runAt,
                0,
                maxAttempts,
                null,
                null,
                null,
                null,
                now,
                now,
                null,
                null
        );

        jobRepository.save(job);
        return id;
    }
}
