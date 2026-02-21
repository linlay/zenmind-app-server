package com.agw.auth.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.agw.auth.domain.InboxMessage;
import com.agw.auth.service.AppInternalEventAuthService;
import com.agw.auth.service.ChatEventDedupService;
import com.agw.auth.service.InboxService;
import com.agw.auth.web.dto.InboxMarkReadRequest;
import com.agw.auth.web.dto.InboxMessageResponse;
import com.agw.auth.web.dto.InboxUnreadCountResponse;
import com.agw.auth.web.dto.InternalChatEventAckResponse;
import com.agw.auth.web.dto.InternalChatEventRequest;
import com.agw.auth.websocket.AppWsPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/app")
public class AppInboxController {

    private final InboxService inboxService;
    private final AppWsPushService appWsPushService;
    private final AppInternalEventAuthService appInternalEventAuthService;
    private final ChatEventDedupService chatEventDedupService;
    private final ObjectMapper objectMapper;

    public AppInboxController(
        InboxService inboxService,
        AppWsPushService appWsPushService,
        AppInternalEventAuthService appInternalEventAuthService,
        ChatEventDedupService chatEventDedupService,
        ObjectMapper objectMapper
    ) {
        this.inboxService = inboxService;
        this.appWsPushService = appWsPushService;
        this.appInternalEventAuthService = appInternalEventAuthService;
        this.chatEventDedupService = chatEventDedupService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/inbox")
    public List<InboxMessageResponse> inbox(
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return inboxService.listMessages(unreadOnly, limit).stream()
            .map(this::toInboxMessageResponse)
            .toList();
    }

    @GetMapping("/inbox/unread-count")
    public InboxUnreadCountResponse unreadCount() {
        return new InboxUnreadCountResponse(inboxService.unreadCount());
    }

    @PostMapping("/inbox/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@Valid @RequestBody InboxMarkReadRequest request) {
        inboxService.markRead(request.messageIds());
        pushInboxSync();
    }

    @PostMapping("/inbox/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        inboxService.markAllRead();
        pushInboxSync();
    }

    @PostMapping("/internal/chat-events")
    public ResponseEntity<InternalChatEventAckResponse> chatEvents(
        @RequestHeader("X-AGW-Timestamp") String timestamp,
        @RequestHeader("X-AGW-Signature") String signature,
        @RequestBody String body
    ) {
        try {
            appInternalEventAuthService.verifyOrThrow(timestamp, signature, body);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }

        InternalChatEventRequest request;
        try {
            request = objectMapper.readValue(body, InternalChatEventRequest.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid internal payload");
        }

        boolean first = chatEventDedupService.markIfFirst(request.chatId(), request.runId());
        if (first) {
            long updatedAt = request.updatedAt() == null ? Instant.now().toEpochMilli() : request.updatedAt();
            appWsPushService.broadcast("chat.new_content", Map.of(
                "chatId", request.chatId(),
                "runId", request.runId(),
                "updatedAt", updatedAt,
                "chatName", request.chatName() == null ? "" : request.chatName(),
                "refreshHints", Map.of("refreshChats", true, "refreshActiveChat", true)
            ));
        }

        return ResponseEntity.ok(new InternalChatEventAckResponse(true, !first));
    }

    private void pushInboxSync() {
        appWsPushService.broadcast("inbox.sync", Map.of("unreadCount", inboxService.unreadCount()));
    }

    private InboxMessageResponse toInboxMessageResponse(InboxMessage message) {
        return new InboxMessageResponse(
            message.messageId(),
            message.title(),
            message.content(),
            message.type(),
            message.sender(),
            inboxService.parsePayload(message.payloadJson()),
            message.isRead(),
            message.readAt(),
            message.createAt(),
            message.updateAt()
        );
    }
}
