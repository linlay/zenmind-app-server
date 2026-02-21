package com.app.auth.web;

import java.util.List;
import java.util.Map;

import com.app.auth.domain.InboxMessage;
import com.app.auth.service.InboxService;
import com.app.auth.web.dto.AdminInboxSendRequest;
import com.app.auth.web.dto.InboxMarkReadRequest;
import com.app.auth.web.dto.InboxMessageResponse;
import com.app.auth.web.dto.InboxUnreadCountResponse;
import com.app.auth.websocket.AppWsPushService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/inbox")
public class AdminInboxController {

    private final InboxService inboxService;
    private final AppWsPushService appWsPushService;

    public AdminInboxController(InboxService inboxService, AppWsPushService appWsPushService) {
        this.inboxService = inboxService;
        this.appWsPushService = appWsPushService;
    }

    @GetMapping
    public List<InboxMessageResponse> list(
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return inboxService.listMessages(unreadOnly, limit).stream()
            .map(this::toInboxMessageResponse)
            .toList();
    }

    @GetMapping("/unread-count")
    public InboxUnreadCountResponse unreadCount() {
        return new InboxUnreadCountResponse(inboxService.unreadCount());
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    public InboxMessageResponse send(@Valid @RequestBody AdminInboxSendRequest request) {
        InboxMessage message = inboxService.createMessage(
            request.title(),
            request.content(),
            request.type(),
            request.payload(),
            "ADMIN"
        );

        InboxMessageResponse response = toInboxMessageResponse(message);
        long unreadCount = inboxService.unreadCount();
        appWsPushService.broadcast("inbox.new", Map.of(
            "message", response,
            "unreadCount", unreadCount
        ));
        appWsPushService.broadcast("inbox.sync", Map.of("unreadCount", unreadCount));
        return response;
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@Valid @RequestBody InboxMarkReadRequest request) {
        inboxService.markRead(request.messageIds());
        appWsPushService.broadcast("inbox.sync", Map.of("unreadCount", inboxService.unreadCount()));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        inboxService.markAllRead();
        appWsPushService.broadcast("inbox.sync", Map.of("unreadCount", inboxService.unreadCount()));
    }

    @PostMapping("/realtime")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void realtime(@RequestBody(required = false) Map<String, Object> payload) {
        appWsPushService.broadcast("realtime.event", payload == null ? Map.of() : payload);
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

