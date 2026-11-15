package com.cognizant.stablecoins.payment.agent;
import java.net.URI;
import java.util.Map;
/** Provider authentication only. Never a blockchain signing or model tool capability. */
@FunctionalInterface public interface AgentRequestAuthorizer {Map<String,String> headers(URI endpoint,byte[] body);}
