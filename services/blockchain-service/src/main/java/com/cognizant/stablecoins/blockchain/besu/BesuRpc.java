package com.cognizant.stablecoins.blockchain.besu;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** One RPC attempt. Implementations must redact provider errors and never retry broadcasts. */
@FunctionalInterface
public interface BesuRpc {
    JsonNode call(String method, List<?> parameters);
}
