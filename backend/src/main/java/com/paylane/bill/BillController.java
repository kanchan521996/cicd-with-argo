package com.paylane.bill;

import com.paylane.bill.BillDtos.BillerResponse;
import com.paylane.bill.BillDtos.PayBillRequest;
import com.paylane.security.AuthUser;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.user.PinVerifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Bills")
public class BillController {

    private final BillService service;
    private final PinVerifier pinVerifier;

    public BillController(BillService service, PinVerifier pinVerifier) {
        this.service = service;
        this.pinVerifier = pinVerifier;
    }

    @GetMapping("/api/billers")
    public List<BillerResponse> billers() {
        return service.billers();
    }

    @PostMapping("/api/bills/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse pay(@AuthenticationPrincipal AuthUser auth,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   @Valid @RequestBody PayBillRequest req) {
        pinVerifier.verify(auth.id(), req.pin());
        return service.pay(auth.id(), req, key);
    }
}
