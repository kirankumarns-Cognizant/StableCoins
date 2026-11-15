package com.cognizant.stablecoins.payment.agent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
final class AgentInput {
    private AgentInput(){ }
    static String redact(String text){
        if(text==null||text.length()>2000)throw new AgentFailure("AGENT_INVALID_INSTRUCTION");
        return text.replaceAll("(?i)-----BEGIN[\\s\\S]*?-----END[^-]+-----","[REDACTED_KEY]")
            .replaceAll("(?i)\\b(?:sk-[A-Za-z0-9_-]+|(?:AKIA|ASIA)[A-Z0-9]{16}|0x[0-9a-f]{40,64}|[0-9a-f]{64})\\b","[REDACTED_CREDENTIAL_OR_ADDRESS]")
            .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[REDACTED_EMAIL]");
    }
    static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
