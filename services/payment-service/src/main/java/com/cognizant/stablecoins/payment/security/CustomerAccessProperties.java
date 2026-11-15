package com.cognizant.stablecoins.payment.security;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/** Payment-facing compatibility name for the reusable server-owned membership contract. */
@Component @ConfigurationProperties("app.access")
public class CustomerAccessProperties extends com.cognizant.stablecoins.platform.security.CustomerMembershipProperties {}
