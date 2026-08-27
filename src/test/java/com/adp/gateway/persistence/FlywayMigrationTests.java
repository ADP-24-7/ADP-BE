package com.adp.gateway.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class FlywayMigrationTests {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void baselineMigrationCreatesAuditEventTable() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'audit_event'
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(1);
    }
}
