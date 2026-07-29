package com.neobank.module.integrations.esignmock;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * Holds the mock's current dials — in memory, not a database row.
 *
 * <p>The doc's suggested entity model lists {@code EsignProviderConfig} as living in "the mock's
 * own schema, deliberately unversioned." This module's e-sign mock is an in-process stand-in (see
 * {@link InMemoryEsignMockClient}'s javadoc), not a separate service with its own schema, and
 * nothing in the acceptance criteria needs the dials to survive a restart — the doc's own "Out of
 * scope" section rules out "restarting anything" as part of this use case's flows. A single
 * mutable holder is the whole implementation; upgrading to a persisted row is a contained change
 * if a later use case needs it.</p>
 *
 * <p>Reads and writes are last-write-wins ({@link AtomicReference#set}) — there is exactly one
 * operator at a time driving this panel, so no compare-and-swap or locking is needed.</p>
 */
@Service
public class EsignProviderConfigService {

    private final AtomicReference<EsignProviderConfig> current =
            new AtomicReference<>(EsignProviderConfig.defaults());

    /** The dials as they stand right now — what the panel shows, and what the NEXT envelope gets. */
    public EsignProviderConfig get() {
        return current.get();
    }

    /**
     * Replaces the dials. AC6: applies to the next envelope registered, never rewrites one already
     * in flight — {@link InMemoryEsignMockClient#registerEnvelope} snapshots {@link #get()} once,
     * at registration time, so a change made after that snapshot cannot affect it.
     */
    public EsignProviderConfig apply(EsignProviderConfig dials) {
        current.set(dials);
        return dials;
    }
}
