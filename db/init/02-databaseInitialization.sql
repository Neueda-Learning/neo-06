-- =====================================================================
--  AGREEMENT SIGNATURE SCHEMA  (MySQL 8.0.16+ / MariaDB 10.5+)
--  InnoDB required for foreign keys. utf8mb4 throughout.
-- =====================================================================

-- ---------------------------------------------------------------------
-- AgreementConfig : insert-only, versioned terms. current = MAX(version)
-- ---------------------------------------------------------------------
CREATE TABLE AgreementConfig (
    version            INT           NOT NULL AUTO_INCREMENT,   -- PK, surrogate row id
    termsVersion       VARCHAR(32)   NOT NULL,                  -- legal-terms stamp, e.g. 2026-06-01
    expiryDays         INT           NOT NULL,                  -- PENDING TTL, seeded 5
    minPaymentPct      DECIMAL(5,2)  NOT NULL,                  -- % leg, seeded 3.00
    minPaymentFloorGbp INT           NOT NULL,                  -- floor leg, seeded 5
    PRIMARY KEY (version),
    -- NOTE (design decision): termsVersion is the FK target from AgreementRecord,
    -- so it MUST be a candidate key. This assumes a 1:1 between a config row and a
    -- legal-terms id (see §7 for the alternative if operational dials must change
    -- independently of legal terms).
    UNIQUE KEY uk_cfg_terms (termsVersion),
    CONSTRAINT chk_cfg_expiry CHECK (expiryDays > 0),
    CONSTRAINT chk_cfg_pct    CHECK (minPaymentPct >= 0),
    CONSTRAINT chk_cfg_floor  CHECK (minPaymentFloorGbp >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- EsignProviderConfig : the mock's dials, singleton, unversioned.
-- Lives logically in the mock's schema (create a separate schema if you
-- want physical separation: CREATE SCHEMA esign_mock;).
-- ---------------------------------------------------------------------
CREATE TABLE EsignProviderConfig (
    id            TINYINT                              NOT NULL DEFAULT 1,
    mode          ENUM('instant','delayed','silent')   NOT NULL,
    delaySeconds  INT                                  NOT NULL DEFAULT 0,
    autoOutcome   ENUM('SIGN','DECLINE')               NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_esign_singleton CHECK (id = 1),          -- enforces exactly one row
    CONSTRAINT chk_esign_delay     CHECK (delaySeconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- AgreementRecord : the signature case, one row per applicationId,
--                   born in GENERATING, ends in a terminal state.
-- ---------------------------------------------------------------------
CREATE TABLE AgreementRecord (
    applicationId  VARCHAR(64)  NOT NULL,                       -- PK, the journey key
    status         ENUM('GENERATING','PENDING','SIGNED',
                       'DECLINED','EXPIRED')          NOT NULL DEFAULT 'GENERATING',
    reference      VARCHAR(32)  NOT NULL,                       -- e.g. agr-000123
    envelopeId     VARCHAR(64)  NULL,                           -- CURRENT envelope; rotated on resend
    termsVersion   VARCHAR(32)  NOT NULL,                       -- FK, pinned at generation
    approvedLimit  INT          NOT NULL,                       -- copied at GENERATING
    apr            DECIMAL(6,2) NOT NULL,                       -- copied at GENERATING
    minPaymentGbp  INT          NOT NULL,                       -- computed at GENERATING
    sentAt         TIMESTAMP    NULL DEFAULT NULL,
    expiresAt      TIMESTAMP    NULL DEFAULT NULL,
    signedAt       TIMESTAMP    NULL DEFAULT NULL,
    PRIMARY KEY (applicationId),
    UNIQUE KEY uk_ar_reference (reference),                     -- human ref must be unique
    UNIQUE KEY uk_ar_envelope  (envelopeId),                    -- NULLs allowed (GENERATING rows);
                                                                -- non-null values unique -> O(1) callback lookup
    KEY        idx_ar_terms          (termsVersion),
    KEY        idx_ar_pending_expiry (status, expiresAt),       -- the expiry queue scan
    CONSTRAINT fk_ar_terms FOREIGN KEY (termsVersion)
        REFERENCES AgreementConfig (termsVersion)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_ar_limit  CHECK (approvedLimit  >= 0),
    CONSTRAINT chk_ar_apr    CHECK (apr            >= 0),
    CONSTRAINT chk_ar_minpay CHECK (minPaymentGbp  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- OfferDocument : the contract artifact, one per agreement, immutable.
-- PK = applicationId simultaneously enforces the 1:1 cardinality.
-- ---------------------------------------------------------------------
CREATE TABLE OfferDocument (
    applicationId  VARCHAR(64)  NOT NULL,                       -- PK + FK  => 1:1
    pdfBlob        MEDIUMBLOB   NOT NULL,                       -- generated ONCE
    sha256         CHAR(64)     NOT NULL,                       -- fingerprint, never recomputed
    sizeBytes      INT          NOT NULL,
    generatedAt    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    limitGbp       INT          NOT NULL,
    apr            DECIMAL(6,2) NOT NULL,
    termsVersion   VARCHAR(32)  NOT NULL,
    PRIMARY KEY (applicationId),
    CONSTRAINT fk_od_agreement FOREIGN KEY (applicationId)
        REFERENCES AgreementRecord (applicationId)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_od_size  CHECK (sizeBytes >= 0),
    CONSTRAINT chk_od_limit CHECK (limitGbp  >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- AgreementStatusHistory : insert-only timeline, one row per transition.
-- ---------------------------------------------------------------------
CREATE TABLE AgreementStatusHistory (
    id            BIGINT        NOT NULL AUTO_INCREMENT,        -- surrogate PK (logical spec has none)
    applicationId VARCHAR(64)   NOT NULL,
    fromStatus    ENUM('GENERATING','PENDING','SIGNED',
                       'DECLINED','EXPIRED')            NOT NULL,
    toStatus      ENUM('GENERATING','PENDING','SIGNED',
                       'DECLINED','EXPIRED')            NOT NULL,
    event         VARCHAR(64)   NOT NULL,
    actor         VARCHAR(128)  NOT NULL,
    occurredAt    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ash_app_time (applicationId, occurredAt),           -- detail-screen timeline order
    CONSTRAINT fk_ash_agreement FOREIGN KEY (applicationId)
        REFERENCES AgreementRecord (applicationId)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- OverrideLog : audit trail, one row per manual override, never deleted.
-- ---------------------------------------------------------------------
CREATE TABLE OverrideLog (
    id            BIGINT        NOT NULL AUTO_INCREMENT,        -- surrogate PK (logical spec has none)
    applicationId VARCHAR(64)   NOT NULL,
    oldStatus     ENUM('GENERATING','PENDING','SIGNED',
                       'DECLINED','EXPIRED')            NOT NULL,
    newStatus     ENUM('GENERATING','PENDING','SIGNED',
                       'DECLINED','EXPIRED')            NOT NULL,
    reason        VARCHAR(1000) NOT NULL,
    operator      VARCHAR(128)  NOT NULL,
    overriddenAt  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ol_app_time (applicationId, overriddenAt),
    CONSTRAINT fk_ol_agreement FOREIGN KEY (applicationId)
        REFERENCES AgreementRecord (applicationId)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ol_not_signed CHECK (newStatus <> 'SIGNED')  -- SIGNED is never a legal target
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;