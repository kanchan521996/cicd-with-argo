package com.paylane.config;

import com.paylane.user.Role;
import com.paylane.user.User;
import com.paylane.user.UserRepository;
import com.paylane.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the admin account on first start. Safe to run on many replicas at once:
 * the unique email constraint means only one insert wins.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final UserRepository users;
    private final WalletService walletService;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public AdminBootstrap(AppProperties props, UserRepository users, WalletService walletService,
                          PasswordEncoder encoder, TransactionTemplate tx) {
        this.props = props;
        this.users = users;
        this.walletService = walletService;
        this.encoder = encoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.AdminProps admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()) {
            return;
        }
        String email = admin.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> {
                User user = new User();
                user.setFullName(admin.fullName() == null ? "Admin" : admin.fullName());
                user.setEmail(email);
                user.setPhone("+10000000000");
                user.setPasswordHash(encoder.encode(admin.password()));
                user.setRole(Role.ADMIN);
                users.save(user);
                walletService.createWalletFor(user);
            });
            log.info("Created admin account {}", email);
        } catch (DataIntegrityViolationException e) {
            log.info("Admin account already created by another instance");
        }
    }
}
