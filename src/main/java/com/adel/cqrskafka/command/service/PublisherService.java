package com.adel.cqrskafka.command.service;

import com.adel.cqrskafka.entities.enums.LogsStatus;
import com.adel.cqrskafka.entities.model.BaseEvent;
import com.adel.cqrskafka.entities.model.Logs;
import com.adel.cqrskafka.entities.model.SimpleMessageEvent;
import com.adel.cqrskafka.entities.repository.LogsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublisherService {

    private final KafkaTemplate<String, BaseEvent> kafkaTemplate;
    private final LogsRepository logsRepository;

    @Transactional
    public String publish(final String topic, final String message, final String... owner) {
        log.info("Publish to topic {} ...", topic);

        final Logs logs = Logs.builder()
                .data(message)
                .topic(topic)
                .status(LogsStatus.PROCESS)
                .publishedAt(new Date())
                .build();
        logsRepository.save(logs);

        final CompletableFuture<SendResult<String, BaseEvent>> result = kafkaTemplate.send(topic,
                new SimpleMessageEvent(
                        logs.getId(),
                        topic,
                        "simple message",
                        message,
                        owner.length > 0 ? owner[0] : "anonymous"
                )
        );
        result
                .thenAccept(r -> {
                    log.info("Published successfully");
                    logs.setStatus(LogsStatus.COMPLETE);
                    logs.setListenedAt(new Date());
                    logsRepository.save(logs);
                })
                .exceptionally(ex -> {
                    log.error("Fail to published");
                    logs.setStatus(LogsStatus.ERROR);
                    logs.setListenedAt(new Date());
                    logsRepository.save(logs);
                    throw new RuntimeException(ex.getMessage());
                });

        return logs.getId();

    }

}
