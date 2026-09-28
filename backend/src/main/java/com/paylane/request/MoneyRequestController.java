package com.paylane.request;

import com.paylane.common.PageResponse;
import com.paylane.request.MoneyRequestDtos.CreateMoneyRequest;
import com.paylane.request.MoneyRequestDtos.MoneyRequestResponse;
import com.paylane.request.MoneyRequestDtos.PayMoneyRequest;
import com.paylane.security.AuthUser;
import com.paylane.user.PinVerifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
@Tag(name = "Money requests")
public class MoneyRequestController {

    private final MoneyRequestService service;
    private final PinVerifier pinVerifier;

    public MoneyRequestController(MoneyRequestService service, PinVerifier pinVerifier) {
        this.service = service;
        this.pinVerifier = pinVerifier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MoneyRequestResponse create(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody CreateMoneyRequest req) {
        return service.create(auth.id(), req);
    }

    /** Requests other people sent to you. */
    @GetMapping("/incoming")
    public PageResponse<MoneyRequestResponse> incoming(@AuthenticationPrincipal AuthUser auth,
                                                       @RequestParam(required = false) MoneyRequestStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return service.incoming(auth.id(), status, page, size);
    }

    /** Requests you sent. */
    @GetMapping("/outgoing")
    public PageResponse<MoneyRequestResponse> outgoing(@AuthenticationPrincipal AuthUser auth,
                                                       @RequestParam(required = false) MoneyRequestStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return service.outgoing(auth.id(), status, page, size);
    }

    @GetMapping("/pending-count")
    public Map<String, Long> pendingCount(@AuthenticationPrincipal AuthUser auth) {
        return service.pendingCount(auth.id());
    }

    @PostMapping("/{id}/pay")
    public MoneyRequestResponse pay(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                    @Valid @RequestBody PayMoneyRequest req) {
        pinVerifier.verify(auth.id(), req.pin());
        return service.pay(auth.id(), id);
    }

    @PostMapping("/{id}/decline")
    public MoneyRequestResponse decline(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.decline(auth.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public MoneyRequestResponse cancel(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.cancel(auth.id(), id);
    }
}
