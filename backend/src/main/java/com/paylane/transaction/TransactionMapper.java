package com.paylane.transaction;

import com.paylane.transaction.TransactionDtos.AdminTransactionResponse;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.user.User;
import com.paylane.wallet.Wallet;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Maps entities to DTOs. Must be called inside a transaction (lazy associations). */
@Component
public class TransactionMapper {

    private final LedgerEntryRepository entries;

    public TransactionMapper(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    public TransactionResponse toResponse(Transaction t, Long myWalletId) {
        Direction direction = Direction.NONE;
        Wallet other = null;
        if (t.getReceiverWallet() != null && t.getReceiverWallet().getId().equals(myWalletId)) {
            direction = Direction.CREDIT;
            other = t.getSenderWallet();
        } else if (t.getSenderWallet() != null && t.getSenderWallet().getId().equals(myWalletId)) {
            direction = Direction.DEBIT;
            other = t.getReceiverWallet();
        }

        String counterparty;
        String detail = null;
        switch (t.getType()) {
            case TRANSFER, REVERSAL -> {
                User u = other == null ? null : other.getUser();
                counterparty = u == null ? "Unknown" : u.getFullName();
                detail = u == null ? null : u.getEmail();
            }
            case TOPUP, WITHDRAWAL -> counterparty = t.getPaymentMethod() == null ? "Card or bank"
                    : t.getPaymentMethod().label();
            case BILL_PAYMENT -> {
                counterparty = t.getBiller() == null ? "Biller" : t.getBiller().getName();
                detail = t.getBillAccountRef();
            }
            default -> counterparty = "";
        }

        BigDecimal balanceAfter = t.getId() == null ? null
                : entries.balanceAfter(t.getId(), myWalletId).orElse(null);

        return new TransactionResponse(t.getId(), t.getReference(), t.getType(), t.getStatus(), direction,
                t.getAmount(), t.getCurrency(), counterparty, detail, t.getDescription(), t.getFailureReason(),
                balanceAfter, t.getCreatedAt(), t.getCompletedAt());
    }

    public AdminTransactionResponse toAdmin(Transaction t) {
        return new AdminTransactionResponse(t.getId(), t.getReference(), t.getType(), t.getStatus(), t.getAmount(),
                t.getCurrency(), side(t, t.getSenderWallet(), true), side(t, t.getReceiverWallet(), false),
                t.getDescription(), t.getFailureReason(),
                t.getReversalOf() == null ? null : t.getReversalOf().getReference(), t.getCreatedAt());
    }

    private String side(Transaction t, Wallet wallet, boolean sender) {
        if (wallet != null) {
            return wallet.getUser().getFullName() + " <" + wallet.getUser().getEmail() + ">";
        }
        if (t.getType() == TransactionType.BILL_PAYMENT && !sender && t.getBiller() != null) {
            return t.getBiller().getName();
        }
        if (t.getPaymentMethod() != null) {
            return t.getPaymentMethod().label();
        }
        return "-";
    }
}
