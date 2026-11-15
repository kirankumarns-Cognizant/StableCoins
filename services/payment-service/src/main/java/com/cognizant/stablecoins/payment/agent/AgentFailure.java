package com.cognizant.stablecoins.payment.agent;
/** Constant codes only: never include provider payloads, secrets or prompt text. */
public class AgentFailure extends RuntimeException {
    private final String code;
    public AgentFailure(String code) { super(code); this.code=code; }
    public String code(){return code;}
}
