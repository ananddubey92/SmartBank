package com.project.onlinebankingsystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class DatabaseMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    @Bean
    public static BeanPostProcessor dataSourceMigrationPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSource dataSource) {
                    log.info("Checking database schema compatibility before JPA initialization...");
                    try (Connection conn = dataSource.getConnection();
                         Statement stmt = conn.createStatement()) {

                        // 1. Ensure users.enabled exists with DEFAULT TRUE and NOT NULL
                        try {
                            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE");
                            log.info("Verified/Migrated column 'users.enabled' (BOOLEAN NOT NULL DEFAULT TRUE)");
                        } catch (Exception e1) {
                            try {
                                stmt.execute("ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE");
                            } catch (Exception ignored) {
                            }
                        }

                        // 2. Ensure any NULL enabled values in existing records are backfilled to TRUE
                        try {
                            stmt.execute("UPDATE users SET enabled = TRUE WHERE enabled IS NULL");
                        } catch (Exception ignored) {
                        }

                        // 3. Ensure users.role can store ROLE_SUPER_ADMIN (convert H2 ENUM to VARCHAR(50) if needed)
                        try {
                            stmt.execute("ALTER TABLE users ALTER COLUMN role SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated column 'users.role' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 4. Ensure transactions.type can store UPI_TRANSFER and other new types (convert H2 ENUM to VARCHAR(50))
                        try {
                            stmt.execute("ALTER TABLE transactions ALTER COLUMN type SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated column 'transactions.type' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 5. Ensure transactions.status can store BLOCKED etc. (convert H2 ENUM to VARCHAR(50))
                        try {
                            stmt.execute("ALTER TABLE transactions ALTER COLUMN status SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated column 'transactions.status' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 6. Ensure upi_transactions columns can store enum values
                        try {
                            stmt.execute("ALTER TABLE upi_transactions ALTER COLUMN transaction_type SET DATA TYPE VARCHAR(50)");
                            stmt.execute("ALTER TABLE upi_transactions ALTER COLUMN status SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated upi_transactions columns to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 7. Ensure accounts.status can store PENDING, FROZEN, etc. (convert H2 ENUM to VARCHAR(50))
                        try {
                            stmt.execute("ALTER TABLE accounts ALTER COLUMN status SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated column 'accounts.status' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 8. Ensure accounts.account_type can store all AccountType values (convert H2 ENUM to VARCHAR(50))
                        try {
                            stmt.execute("ALTER TABLE accounts ALTER COLUMN account_type SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated column 'accounts.account_type' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 9. Ensure accounts.branch_name exists
                        try {
                            stmt.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS branch_name VARCHAR(150) DEFAULT 'Main Branch, Mumbai'");
                            stmt.execute("UPDATE accounts SET branch_name = 'Main Branch, Mumbai' WHERE branch_name IS NULL");
                            log.info("Verified/Migrated column 'accounts.branch_name'");
                        } catch (Exception ignored) {
                        }

                        // 10. Ensure users.gender can store any gender value (convert H2 ENUM to VARCHAR(50))
                        try {
                            stmt.execute("ALTER TABLE users ALTER COLUMN gender SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated column 'users.gender' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 11. Ensure users.kyc_status exists and is VARCHAR(50)
                        try {
                            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(50) DEFAULT 'VERIFIED'");
                            stmt.execute("ALTER TABLE users ALTER COLUMN kyc_status SET DATA TYPE VARCHAR(50)");
                            stmt.execute("UPDATE users SET kyc_status = 'VERIFIED' WHERE kyc_status IS NULL");
                            log.info("Verified/Migrated column 'users.kyc_status' to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 12. Ensure users.pin_code exists
                        try {
                            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_code VARCHAR(20)");
                            log.info("Verified/Migrated column 'users.pin_code'");
                        } catch (Exception ignored) {
                        }

                        // 13. Ensure debit_cards columns can store enum values (convert H2 ENUM to VARCHAR(50))
                        try {
                            stmt.execute("ALTER TABLE debit_cards ALTER COLUMN status SET DATA TYPE VARCHAR(50)");
                            stmt.execute("ALTER TABLE debit_cards ALTER COLUMN card_type SET DATA TYPE VARCHAR(50)");
                            log.info("Verified/Migrated debit_cards enum columns to VARCHAR(50)");
                        } catch (Exception ignored) {
                        }

                        // 14. Ensure debit_cards.cvv is VARCHAR(255) for encrypted storage
                        try {
                            stmt.execute("ALTER TABLE debit_cards ALTER COLUMN cvv SET DATA TYPE VARCHAR(255)");
                            log.info("Verified/Migrated column 'debit_cards.cvv' to VARCHAR(255) for AES-256 encryption");
                        } catch (Exception ignored) {
                        }

                        // 15. Ensure accounts has nominee columns
                        try {
                            stmt.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS nominee_name VARCHAR(150)");
                            stmt.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS nominee_relationship VARCHAR(50)");
                            log.info("Verified/Migrated nominee columns in 'accounts'");
                        } catch (Exception ignored) {
                        }

                    } catch (Exception e) {
                        // In a fresh database where tables do not yet exist, Hibernate ddl-auto will create them.
                        log.debug("Database migration pre-check completed (tables may be initialized by Hibernate).");
                    }
                }
                return bean;
            }
        };
    }
}
