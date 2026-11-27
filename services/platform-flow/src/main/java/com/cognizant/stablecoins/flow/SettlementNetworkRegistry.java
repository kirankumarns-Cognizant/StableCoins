package com.cognizant.stablecoins.flow;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-owned registry primitive for ADR-016. No default or alternate selection.
 * Integration into payment execution is gated until the complete private flow passes.
 */
public final class SettlementNetworkRegistry {
    public record Entry(SettlementNetwork.Context context, boolean enabledForNewWork) {
        public Entry { Objects.requireNonNull(context, "CONTEXT_REQUIRED"); }
    }
    private final Map<String, Entry> entries;

    public SettlementNetworkRegistry(Collection<Entry> configured) {
        Objects.requireNonNull(configured, "REGISTRY_REQUIRED");
        var copy = new LinkedHashMap<String, Entry>();
        for (var entry : configured) {
            Objects.requireNonNull(entry, "ENTRY_REQUIRED");
            String id = entry.context().token().chain().networkId();
            if (copy.putIfAbsent(id, entry) != null)
                throw new IllegalArgumentException("DUPLICATE_NETWORK_ID");
        }
        entries = Map.copyOf(copy);
    }

    public SettlementNetwork.Context forNewWork(String networkId) {
        Entry entry = required(networkId);
        if (!entry.enabledForNewWork()) throw new IllegalStateException("NETWORK_DISABLED");
        return entry.context();
    }

    /** Monitoring is allowed after disablement, but never against replacement chain/token/signers. */
    public SettlementNetwork.Context forPinnedExecution(SettlementNetwork.Context pinned) {
        Objects.requireNonNull(pinned, "PINNED_CONTEXT_REQUIRED");
        var configured = required(pinned.token().chain().networkId()).context();
        if (!configured.equals(pinned)) throw new IllegalStateException("PINNED_NETWORK_CONTEXT_CHANGED");
        return configured;
    }

    private Entry required(String id) {
        Entry entry = id == null ? null : entries.get(id);
        if (entry == null) throw new IllegalArgumentException("NETWORK_NOT_CONFIGURED");
        return entry;
    }
}
