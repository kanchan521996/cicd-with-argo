package com.paylane.request;

import com.paylane.common.ApiException;
import com.paylane.common.PageResponse;
import com.paylane.config.AppProperties;
import com.paylane.notification.NotificationService;
import com.paylane.request.MoneyRequestDtos.CreateMoneyRequest;
import com.paylane.request.MoneyRequestDtos.MoneyRequestResponse;
import com.paylane.transaction.LedgerService;
import com.paylane.transaction.Transaction;
import com.paylane.transaction.TransferService;
import com.paylane.user.User;
import com.paylane.user.UserService;
import com.paylane.user.UserStatus;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoneyRequestService {

    private final MoneyRequestRepository repo;
    private final UserService userService;
    private final LedgerService ledger;
    private final TransferService transferService;
    private final NotificationService notifications;
    private final AppProperties props;

    public MoneyRequestService(MoneyRequestRepository repo, UserService userService, LedgerService ledger,
                               TransferService transferService, NotificationService notifications, AppProperties props) {
        this.props = props;
        this.repo = repo;
        this.userService = userService;
        this.ledger = ledger;
        this.transferService = transferService;
        this.notifications = notifications;
    }

    @Transactional
    public MoneyRequestResponse create(Long userId, CreateMoneyRequest req) {
        User requester = userService.getActive(userId);
        User payer = userService.findByIdentifier(req.payer());
        if (payer.getId().equals(requester.getId())) {
            throw ApiException.badRequest("SELF_REQUEST", "You can't request money from yourself");
        }
        if (payer.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unprocessable("PAYER_UNAVAILABLE", "This account can't receive requests right now");
        }
        BigDecimal amount = ledger.validateAmount(req.amount());
        MoneyRequest r = new MoneyRequest();
        r.setRequester(requester);
        r.setPayer(payer);
        r.setAmount(amount);
        r.setCurrency(props.wallet().currency());
        r.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        repo.save(r);
        notifications.notify(payer, "Money requested",
                requester.getFullName() + " requested " + r.getCurrency() + " " + amount
                        + (r.getNote() == null ? "." : " for " + r.getNote()));
        return MoneyRequestResponse.from(r);
    }

    @Transactional(readOnly = true)
    public PageResponse<MoneyRequestResponse> incoming(Long userId, MoneyRequestStatus status, int page, int size) {
        Pageable p = page(page, size);
        Page<MoneyRequest> result = status == null
                ? repo.findByPayerIdOrderByCreatedAtDesc(userId, p)
                : repo.findByPayerIdAndStatusOrderByCreatedAtDesc(userId, status, p);
        return PageResponse.of(result, MoneyRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<MoneyRequestResponse> outgoing(Long userId, MoneyRequestStatus status, int page, int size) {
        Pageable p = page(page, size);
        Page<MoneyRequest> result = status == null
                ? repo.findByRequesterIdOrderByCreatedAtDesc(userId, p)
                : repo.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, status, p);
        return PageResponse.of(result, MoneyRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> pendingCount(Long userId) {
        return Map.of("pending", repo.countByPayerIdAndStatus(userId, MoneyRequestStatus.PENDING));
    }

    @Transactional
    public MoneyRequestResponse pay(Long userId, Long requestId) {
        MoneyRequest r = lockPending(requestId);
        if (!r.getPayer().getId().equals(userId)) {
            throw ApiException.notFound("Request not found");
        }
        User payer = userService.getActive(userId);
        String note = "Request #" + r.getId() + (r.getNote() == null ? "" : ": " + r.getNote());
        Transaction t = transferService.execute(payer, r.getRequester(), r.getAmount(), note, null);
        r.setTransaction(t);
        r.setStatus(MoneyRequestStatus.PAID);
        return MoneyRequestResponse.from(r);
    }

    @Transactional
    public MoneyRequestResponse decline(Long userId, Long requestId) {
        MoneyRequest r = lockPending(requestId);
        if (!r.getPayer().getId().equals(userId)) {
            throw ApiException.notFound("Request not found");
        }
        r.setStatus(MoneyRequestStatus.DECLINED);
        notifications.notify(r.getRequester(), "Request declined",
                r.getPayer().getFullName() + " declined your request for " + r.getCurrency() + " " + r.getAmount() + ".");
        return MoneyRequestResponse.from(r);
    }

    @Transactional
    public MoneyRequestResponse cancel(Long userId, Long requestId) {
        MoneyRequest r = lockPending(requestId);
        if (!r.getRequester().getId().equals(userId)) {
            throw ApiException.notFound("Request not found");
        }
        r.setStatus(MoneyRequestStatus.CANCELLED);
        notifications.notify(r.getPayer(), "Request cancelled",
                r.getRequester().getFullName() + " cancelled their request for " + r.getCurrency() + " "
                        + r.getAmount() + ".");
        return MoneyRequestResponse.from(r);
    }

    private MoneyRequest lockPending(Long id) {
        MoneyRequest r = repo.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (r.getStatus() != MoneyRequestStatus.PENDING) {
            throw ApiException.conflict("REQUEST_CLOSED", "This request is already " + r.getStatus().name().toLowerCase());
        }
        return r;
    }

    private static Pageable page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }
}
