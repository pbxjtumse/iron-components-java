package com.xjtu.iron.message.demo.controller;

import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.api.publish.SendOptions;
import com.xjtu.iron.message.api.publish.SendResult;
import com.xjtu.iron.message.core.MessageTemplate;
import com.xjtu.iron.message.demo.dto.MultiSendMessageResponse;
import com.xjtu.iron.message.demo.dto.ReceivedMessageView;
import com.xjtu.iron.message.demo.dto.SendMessageRequest;
import com.xjtu.iron.message.demo.dto.SendMessageResponse;
import com.xjtu.iron.message.demo.store.InMemoryReceivedMessageStore;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo 消息发送和消费验证接口。
 */
@RestController
@RequestMapping("/demo/messages")
public class MessageDemoController {

    private static final String DEFAULT_TOPIC = "message";
    private static final String DEFAULT_EVENT_TYPE = "DemoMessage";
    private static final List<String> DEMO_PROVIDERS = List.of("kafka", "pulsar", "rocketmq");

    private final MessageTemplate messageTemplate;
    private final InMemoryReceivedMessageStore store;

    public MessageDemoController(
            MessageTemplate messageTemplate,
            InMemoryReceivedMessageStore store) {
        this.messageTemplate = messageTemplate;
        this.store = store;
    }

    @PostMapping("/send")
    public SendMessageResponse send(
            @RequestBody SendMessageRequest request) {
        SendMessageRequest actualRequest = request == null ? new SendMessageRequest() : request;
        return SendMessageResponse.from(sendToProvider(actualRequest, normalizeProvider(actualRequest.getProvider()), null));
    }

    @PostMapping("/send/all")
    public MultiSendMessageResponse sendAll(
            @RequestBody SendMessageRequest request) {
        SendMessageRequest actualRequest = request == null ? new SendMessageRequest() : request;
        String batchId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        List<SendMessageResponse> results = DEMO_PROVIDERS.stream()
                .map(provider -> sendSafely(actualRequest, provider, batchId))
                .toList();
        return new MultiSendMessageResponse(batchId, startedAt, Instant.now(), results);
    }

    @GetMapping("/received")
    public List<ReceivedMessageView> received() {
        return store.list();
    }

    @GetMapping("/received/{provider}")
    public List<ReceivedMessageView> receivedByProvider(@PathVariable String provider) {
        String normalizedProvider = normalizeProvider(provider);
        return store.list().stream()
                .filter(message -> normalizedProvider == null
                        || normalizedProvider.equals(normalizeProvider(message.getProviderName())))
                .toList();
    }

    @GetMapping("/received-summary")
    public Map<String, Long> receivedSummary() {
        return store.list().stream()
                .collect(Collectors.groupingBy(
                        message -> normalizeProvider(message.getProviderName()) == null
                                ? "unknown"
                                : normalizeProvider(message.getProviderName()),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private SendMessageResponse sendSafely(
            SendMessageRequest request,
            String provider,
            String batchId) {
        try {
            return SendMessageResponse.from(sendToProvider(request, provider, batchId));
        } catch (RuntimeException exception) {
            return SendMessageResponse.failed(provider, logicalTopic(request), exception.getMessage());
        }
    }

    private SendResult sendToProvider(
            SendMessageRequest request,
            String provider,
            String batchId) {
        MessageDestination destination = MessageDestination.of("demo", logicalTopic(request));
        if (provider != null) {
            destination = destination.withProviderHint(provider);
        }

        Map<String, Object> payload = request.getPayload() == null
                ? Map.<String, Object>of()
                : request.getPayload();
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.getHeaders() != null) {
            headers.putAll(request.getHeaders());
        }
        if (batchId != null) {
            headers.put("demo-broadcast-batch-id", batchId);
        }

        MessageEnvelope<Map<String, Object>> envelope =
                MessageEnvelope.builder(eventType(request), payload)
                        .messageKey(request.getBusinessKey())
                        .headers(headers)
                        .build();

        return messageTemplate.send(destination, envelope, SendOptions.defaults());
    }

    private static String logicalTopic(SendMessageRequest request) {
        String topic = request == null ? null : text(request.getTopic());
        return topic == null ? DEFAULT_TOPIC : topic;
    }

    private static String eventType(SendMessageRequest request) {
        String eventType = request == null ? null : text(request.getEventType());
        return eventType == null ? DEFAULT_EVENT_TYPE : eventType;
    }

    private static String normalizeProvider(String value) {
        String text = text(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
