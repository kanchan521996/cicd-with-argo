package com.paylane.admin;

import com.paylane.admin.AdminDtos.AdminUserResponse;
import com.paylane.admin.AdminDtos.ReverseRequest;
import com.paylane.admin.AdminDtos.StatsResponse;
import com.paylane.admin.AdminDtos.UpdateLimitRequest;
import com.paylane.admin.AdminDtos.UpdateStatusRequest;
import com.paylane.common.PageResponse;
import com.paylane.security.AuthUser;
import com.paylane.transaction.TransactionDtos.AdminTransactionResponse;
import com.paylane.transaction.TransactionStatus;
import com.paylane.transaction.TransactionType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All routes require ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(@RequestParam(required = false) String search,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.users(search, page, size);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse setStatus(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                       @Valid @RequestBody UpdateStatusRequest req) {
        return service.setStatus(auth.id(), id, req.status());
    }

    @PatchMapping("/users/{id}/limit")
    public AdminUserResponse setLimit(@PathVariable Long id, @Valid @RequestBody UpdateLimitRequest req) {
        return service.setDailyLimit(id, req.dailyLimit());
    }

    @GetMapping("/transactions")
    public PageResponse<AdminTransactionResponse> transactions(@RequestParam(required = false) TransactionType type,
                                                               @RequestParam(required = false) TransactionStatus status,
                                                               @RequestParam(required = false) String reference,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return service.transactions(type, status, reference, page, size);
    }

    @PostMapping("/transactions/{reference}/reverse")
    public AdminTransactionResponse reverse(@AuthenticationPrincipal AuthUser auth, @PathVariable String reference,
                                            @Valid @RequestBody ReverseRequest req) {
        return service.reverse(auth.id(), reference, req.reason());
    }
}
