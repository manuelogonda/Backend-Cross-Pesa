package com.manuelorg.cross_pesa.notification.service;

import com.manuelorg.cross_pesa.notification.entity.Notification;
import com.manuelorg.cross_pesa.notification.enums.NotificationStatus;
import com.manuelorg.cross_pesa.notification.repository.NotificationRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduled poller that scans for pending notifications and drives their dispatch.
 *
 * <p>Every cycle gets its own MDC {@code traceId} so all log lines for the cycle
 * are correlated, and {@code jobName=notification-poll} distinguishes scheduler
 * traffic from HTTP request traffic. A per-item try/catch inside the loop keeps
 * one bad notification from aborting the batch, and an outer try/catch keeps a
 * cycle-level exception from silently killing future scheduled runs.
 *
 * <p>The last successful cycle timestamp is exposed as the Micrometer gauge
 * {@code notification_poll_last_success_timestamp} (epoch seconds), and cycle
 * duration is captured by the {@code @Timed} annotation on {@link #poll()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPoller {

    static final String JOB_NAME = "notification-poll";
    static final int BATCH_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    private final AtomicLong lastSuccessTimestamp = new AtomicLong(0);

    @PostConstruct
    void bindGauge() {
        Gauge.builder("notification_poll_last_success_timestamp", lastSuccessTimestamp, AtomicLong::get)
                .description("Epoch seconds of the last successful notification poll cycle")
                .register(meterRegistry);
    }

    @Timed(value = "notification_poll_duration", description = "Notification poll cycle duration")
    @Scheduled(fixedDelayString = "PT5M")
    public void poll() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        MDC.put("jobName", JOB_NAME);

        long start = System.currentTimeMillis();
        int processed = 0;
        int success = 0;
        int failure = 0;
        try {
            log.atInfo()
                    .addKeyValue("event", "notification.poll.started")
                    .addKeyValue("trigger", "scheduler")
                    .log("Notification poll cycle started");

            List<Notification> batch = notificationRepository.findByStatus(
                    NotificationStatus.UNREAD, PageRequest.of(0, BATCH_SIZE));

            for (Notification n : batch) {
                processed++;
                try {
                    notificationService.dispatch(n.getId());
                    success++;
                } catch (Exception itemEx) {
                    failure++;
                    log.atError()
                            .addKeyValue("event", "notification.poll.item.failed")
                            .addKeyValue("notificationId", n.getId())
                            .setCause(itemEx)
                            .log("Notification dispatch failed; continuing batch");
                }
            }

            lastSuccessTimestamp.set(Instant.now().getEpochSecond());

            log.atInfo()
                    .addKeyValue("event", "notification.poll.finished")
                    .addKeyValue("processedCount", processed)
                    .addKeyValue("successCount", success)
                    .addKeyValue("failureCount", failure)
                    .addKeyValue("durationMs", System.currentTimeMillis() - start)
                    .log("Notification poll cycle finished");
        } catch (Exception cycleEx) {
            log.atError()
                    .addKeyValue("event", "notification.poll.failed")
                    .addKeyValue("processedCount", processed)
                    .addKeyValue("successCount", success)
                    .addKeyValue("failureCount", failure)
                    .addKeyValue("durationMs", System.currentTimeMillis() - start)
                    .setCause(cycleEx)
                    .log("Notification poll cycle failed; schedule continues");
        } finally {
            MDC.remove("traceId");
            MDC.remove("jobName");
        }
    }
}
