package com.paylane.paymentmethod;

import com.paylane.paymentmethod.PaymentMethodDtos.AddBankRequest;
import com.paylane.paymentmethod.PaymentMethodDtos.AddCardRequest;
import com.paylane.paymentmethod.PaymentMethodDtos.PaymentMethodResponse;
import com.paylane.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-methods")
@Tag(name = "Payment methods")
public class PaymentMethodController {

    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaymentMethodResponse> list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodResponse addCard(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody AddCardRequest req) {
        return service.addCard(auth.id(), req);
    }

    @PostMapping("/banks")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodResponse addBank(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody AddBankRequest req) {
        return service.addBank(auth.id(), req);
    }

    @PatchMapping("/{id}/default")
    public PaymentMethodResponse makeDefault(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.makeDefault(auth.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.remove(auth.id(), id);
    }
}
