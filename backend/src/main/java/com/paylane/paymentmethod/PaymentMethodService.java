package com.paylane.paymentmethod;

import com.paylane.common.ApiException;
import com.paylane.paymentmethod.PaymentMethodDtos.AddBankRequest;
import com.paylane.paymentmethod.PaymentMethodDtos.AddCardRequest;
import com.paylane.paymentmethod.PaymentMethodDtos.PaymentMethodResponse;
import com.paylane.user.User;
import com.paylane.user.UserService;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    static final int MAX_METHODS = 10;

    private final PaymentMethodRepository repo;
    private final UserService userService;

    public PaymentMethodService(PaymentMethodRepository repo, UserService userService) {
        this.repo = repo;
        this.userService = userService;
    }

    public PaymentMethod getOwned(Long userId, Long id) {
        return repo.findByIdAndUserIdAndRemovedFalse(id, userId)
                .orElseThrow(() -> ApiException.notFound("Payment method not found"));
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list(Long userId) {
        return repo.findByUserIdAndRemovedFalseOrderByCreatedAtDesc(userId).stream()
                .map(PaymentMethodResponse::from).toList();
    }

    @Transactional
    public PaymentMethodResponse addCard(Long userId, AddCardRequest req) {
        User user = userService.getActive(userId);
        String digits = req.cardNumber().replace(" ", "");
        if (digits.length() < 12 || digits.length() > 19 || !CardValidator.luhn(digits)) {
            throw ApiException.badRequest("INVALID_CARD", "Card number is not valid");
        }
        YearMonth expiry = YearMonth.of(req.expiryYear(), req.expiryMonth());
        if (expiry.isBefore(YearMonth.now(ZoneOffset.UTC))) {
            throw ApiException.badRequest("CARD_EXPIRED", "This card has expired");
        }
        ensureCapacity(userId);
        // In production the card would be tokenized by the processor here; CVV is checked and discarded.
        PaymentMethod pm = new PaymentMethod();
        pm.setUser(user);
        pm.setType(PaymentMethodType.CARD);
        pm.setBrand(CardValidator.brand(digits));
        pm.setLast4(digits.substring(digits.length() - 4));
        pm.setHolderName(req.holderName().trim());
        pm.setExpiryMonth(req.expiryMonth());
        pm.setExpiryYear(req.expiryYear());
        pm.setDefaultMethod(repo.countByUserIdAndRemovedFalse(userId) == 0);
        return PaymentMethodResponse.from(repo.save(pm));
    }

    @Transactional
    public PaymentMethodResponse addBank(Long userId, AddBankRequest req) {
        User user = userService.getActive(userId);
        ensureCapacity(userId);
        PaymentMethod pm = new PaymentMethod();
        pm.setUser(user);
        pm.setType(PaymentMethodType.BANK_ACCOUNT);
        pm.setBrand("Bank account");
        pm.setBankName(req.bankName().trim());
        pm.setLast4(req.accountNumber().substring(req.accountNumber().length() - 4));
        pm.setHolderName(req.holderName().trim());
        pm.setDefaultMethod(repo.countByUserIdAndRemovedFalse(userId) == 0);
        return PaymentMethodResponse.from(repo.save(pm));
    }

    @Transactional
    public void remove(Long userId, Long id) {
        PaymentMethod pm = getOwned(userId, id);
        pm.setRemoved(true);
        if (pm.isDefaultMethod()) {
            pm.setDefaultMethod(false);
            repo.findByUserIdAndRemovedFalseOrderByCreatedAtDesc(userId).stream()
                    .filter(other -> !other.getId().equals(id))
                    .findFirst()
                    .ifPresent(other -> other.setDefaultMethod(true));
        }
    }

    @Transactional
    public PaymentMethodResponse makeDefault(Long userId, Long id) {
        PaymentMethod target = getOwned(userId, id);
        repo.findByUserIdAndRemovedFalseOrderByCreatedAtDesc(userId)
                .forEach(pm -> pm.setDefaultMethod(pm.getId().equals(target.getId())));
        return PaymentMethodResponse.from(target);
    }

    private void ensureCapacity(Long userId) {
        if (repo.countByUserIdAndRemovedFalse(userId) >= MAX_METHODS) {
            throw ApiException.unprocessable("TOO_MANY_METHODS", "You can link up to " + MAX_METHODS + " cards and accounts");
        }
    }
}
