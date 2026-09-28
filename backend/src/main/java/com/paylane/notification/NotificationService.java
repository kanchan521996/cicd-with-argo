package com.paylane.notification;

import com.paylane.common.ApiException;
import com.paylane.common.PageResponse;
import com.paylane.user.User;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    public record NotificationResponse(Long id, String title, String message, boolean read, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /** Joins the caller's transaction, so a notification only exists if the payment committed. */
    public void notify(User user, String title, String message) {
        repo.save(new Notification(user, title, message));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        return PageResponse.of(repo.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(Long userId) {
        return Map.of("unread", repo.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"))
                .setRead(true);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId);
    }
}
