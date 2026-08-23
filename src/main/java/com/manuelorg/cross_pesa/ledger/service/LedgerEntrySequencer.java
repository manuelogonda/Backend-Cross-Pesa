package com.manuelorg.cross_pesa.ledger.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Allocates monotonic sequence values for {@code ledger_entries.entry_seq}.
 *
 * The DB column also carries a nextval() DEFAULT (Flyway V4 / schema.sql), but
 * Hibernate includes the property in its INSERT, so the value must be provided
 * by the application.
 *
 * NOTE: must be called OUTSIDE an aborted-transaction risk window — if the
 * sequence is missing this throws, which is intentional fail-fast behaviour
 * (production databases always have the sequence via Flyway V4 / schema.sql;
 * throwaway ddl-auto test schemas must CREATE SEQUENCE it themselves).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEntrySequencer {

    private final EntityManager entityManager;

    public Long next() {
        Object result = entityManager
                .createNativeQuery("SELECT nextval('ledger_entries_entry_seq_seq')")
                .getSingleResult();
        return ((Number) result).longValue();
    }
}
