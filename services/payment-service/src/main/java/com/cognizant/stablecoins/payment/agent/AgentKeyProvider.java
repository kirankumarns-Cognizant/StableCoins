package com.cognizant.stablecoins.payment.agent;
/** Server-only secret boundary; key material must never appear in logs, DTOs or persistence. */
public interface AgentKeyProvider { String load(); }
