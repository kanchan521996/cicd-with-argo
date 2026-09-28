package com.paylane.transaction;

import com.paylane.common.PageResponse;
import com.paylane.security.AuthUser;
import com.paylane.transaction.TransactionDtos.SummaryResponse;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.transaction.TransactionDtos.TransferRequest;
import com.paylane.user.PinVerifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransferService transferService;
    private final PinVerifier pinVerifier;

    public TransactionController(TransactionService transactionService, TransferService transferService,
                                 PinVerifier pinVerifier) {
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.pinVerifier = pinVerifier;
    }

    /** Send money to another user by email or phone. Send an Idempotency-Key header to make retries safe. */
    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse send(@AuthenticationPrincipal AuthUser auth,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @Valid @RequestBody TransferRequest req) {
        pinVerifier.verify(auth.id(), req.pin());
        return transferService.send(auth.id(), req, key);
    }

    @GetMapping("/api/transactions")
    public PageResponse<TransactionResponse> history(
            @AuthenticationPrincipal AuthUser auth,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return transactionService.history(auth.id(), type, status, from, to, reference, page, size);
    }

    @GetMapping("/api/transactions/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AuthUser auth) {
        return transactionService.summary(auth.id());
    }

    @GetMapping("/api/transactions/{reference}")
    public TransactionResponse byReference(@AuthenticationPrincipal AuthUser auth, @PathVariable String reference) {
        return transactionService.byReference(auth.id(), reference);
    }
}
