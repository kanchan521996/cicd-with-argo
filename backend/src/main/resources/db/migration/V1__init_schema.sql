-- Paylane core schema (MySQL 8)

CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    full_name        VARCHAR(120) NOT NULL,
    email            VARCHAR(160) NOT NULL,
    phone            VARCHAR(20)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    pin_hash         VARCHAR(100) NULL,
    pin_attempts     INT          NOT NULL DEFAULT 0,
    pin_locked_until DATETIME(6)  NULL,
    role             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE wallets (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    user_id     BIGINT         NOT NULL,
    balance     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency    CHAR(3)        NOT NULL,
    daily_limit DECIMAL(19, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_methods (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    brand        VARCHAR(40)  NOT NULL,
    last4        CHAR(4)      NOT NULL,
    holder_name  VARCHAR(120) NOT NULL,
    expiry_month INT          NULL,
    expiry_year  INT          NULL,
    bank_name    VARCHAR(120) NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    removed      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_pm_user ON payment_methods (user_id);

CREATE TABLE billers (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    category      VARCHAR(40)  NOT NULL,
    account_label VARCHAR(60)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_billers_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transactions (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    reference          VARCHAR(32)    NOT NULL,
    type               VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    amount             DECIMAL(19, 2) NOT NULL,
    currency           CHAR(3)        NOT NULL,
    sender_wallet_id   BIGINT         NULL,
    receiver_wallet_id BIGINT         NULL,
    payment_method_id  BIGINT         NULL,
    biller_id          BIGINT         NULL,
    bill_account_ref   VARCHAR(60)    NULL,
    description        VARCHAR(255)   NULL,
    failure_reason     VARCHAR(255)   NULL,
    idempotency_key    VARCHAR(64)    NULL,
    initiated_by       BIGINT         NOT NULL,
    reversal_of_id     BIGINT         NULL,
    created_at         DATETIME(6)    NOT NULL,
    completed_at       DATETIME(6)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_txn_reference UNIQUE (reference),
    CONSTRAINT uk_txn_idempotency UNIQUE (initiated_by, idempotency_key),
    CONSTRAINT fk_txn_sender FOREIGN KEY (sender_wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_txn_receiver FOREIGN KEY (receiver_wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_txn_pm FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id),
    CONSTRAINT fk_txn_biller FOREIGN KEY (biller_id) REFERENCES billers (id),
    CONSTRAINT fk_txn_initiator FOREIGN KEY (initiated_by) REFERENCES users (id),
    CONSTRAINT fk_txn_reversal FOREIGN KEY (reversal_of_id) REFERENCES transactions (id),
    CONSTRAINT chk_txn_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_txn_sender_created ON transactions (sender_wallet_id, created_at);
CREATE INDEX idx_txn_receiver_created ON transactions (receiver_wallet_id, created_at);
CREATE INDEX idx_txn_created ON transactions (created_at);

-- Double-entry style audit trail: every balance change writes one row here.
CREATE TABLE ledger_entries (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT         NOT NULL,
    wallet_id      BIGINT         NOT NULL,
    entry_type     VARCHAR(10)    NOT NULL,
    amount         DECIMAL(19, 2) NOT NULL,
    balance_after  DECIMAL(19, 2) NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_txn FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_ledger_wallet ON ledger_entries (wallet_id, created_at);

CREATE TABLE money_requests (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    requester_id   BIGINT         NOT NULL,
    payer_id       BIGINT         NOT NULL,
    amount         DECIMAL(19, 2) NOT NULL,
    currency       CHAR(3)        NOT NULL,
    note           VARCHAR(255)   NULL,
    status         VARCHAR(20)    NOT NULL,
    transaction_id BIGINT         NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_req_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_req_payer FOREIGN KEY (payer_id) REFERENCES users (id),
    CONSTRAINT fk_req_txn FOREIGN KEY (transaction_id) REFERENCES transactions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_req_payer ON money_requests (payer_id, status);
CREATE INDEX idx_req_requester ON money_requests (requester_id, status);

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(120) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_notif_user ON notifications (user_id, is_read, created_at);
