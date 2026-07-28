package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * INTEGRATION TEST (name ends in {@code IT} → runs on {@code ./mvnw verify}, needs Docker).
 *
 * <p>Testcontainers boots a real MySQL 8.4, this module's Liquibase change sets create
 * {@code agreement_record} on it, and Hibernate runs {@code ddl-auto=validate} against that real
 * DDL. It catches what H2 hides — {@code TIMESTAMP}/{@code DECIMAL} column behaviour — which is
 * exactly the class of bug that otherwise only appears on {@code docker compose up}.</p>
 *
 * <p>{@code disabledWithoutDocker = true}: with Docker stopped this is SKIPPED, not failed.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional // roll back each test so methods don't leak rows into one another
class AgreementRecordRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("neo_06");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    AgreementRecordRepository agreementRecords;

    @Test
    void schemaValidatesAndStartsEmpty() {
        // Reaching here proves Liquibase applied 002/003/004 and ddl-auto=validate passed on real MySQL.
        assertThat(agreementRecords.findAll()).isEmpty();
    }

    @Test
    void aRowRoundTripsThroughRealMysql() {
        AgreementRecord saved = agreementRecords.saveAndFlush(
                new AgreementRecord("APP-1", AgreementStatus.GENERATING));

        assertThat(saved.getCreatedAt()).isNotNull(); // @PrePersist ran

        AgreementRecord reloaded = agreementRecords.findById("APP-1").orElseThrow();
        assertThat(reloaded.getApplicationId()).isEqualTo("APP-1");
        assertThat(reloaded.getStatus()).isEqualTo(AgreementStatus.GENERATING);
    }

    @Test
    void theBoardOrdersNewestFirst() {
        agreementRecords.saveAndFlush(new AgreementRecord("APP-OLD", AgreementStatus.GENERATING));
        agreementRecords.saveAndFlush(new AgreementRecord("APP-NEW", AgreementStatus.PENDING));

        assertThat(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .extracting(AgreementRecord::getApplicationId)
                .containsExactly("APP-NEW", "APP-OLD");
    }
}
