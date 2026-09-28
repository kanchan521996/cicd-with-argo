package com.paylane.wallet;

import com.paylane.security.AuthUser;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.user.PinVerifier;
import com.paylane.wallet.WalletDtos.TopUpRequest;
import com.paylane.wallet.WalletDtos.WalletResponse;
import com.paylane.wallet.WalletDtos.WithdrawRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet")
public class WalletController {

    private final WalletService walletService;
    private final PinVerifier pinVerifier;

    public WalletController(WalletService walletService, PinVerifier pinVerifier) {
        this.walletService = walletService;
        this.pinVerifier = pinVerifier;
    }

    @GetMapping
    public WalletResponse myWallet(@AuthenticationPrincipal AuthUser auth) {
        return walletService.myWallet(auth.id());
    }

    @PostMapping("/topup")
    public TransactionResponse topUp(@AuthenticationPrincipal AuthUser auth,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                     @Valid @RequestBody TopUpRequest req) {
        return walletService.topUp(auth.id(), req, key);
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(@AuthenticationPrincipal AuthUser auth,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @Valid @RequestBody WithdrawRequest req) {
        pinVerifier.verify(auth.id(), req.pin());
        return walletService.withdraw(auth.id(), req, key);
    }
}
