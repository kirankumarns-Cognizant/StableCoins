package com.cognizant.stablecoins.platform.security;
import java.util.*;
public class CustomerMembershipProperties {
    public enum Role { PAYMENT_OPERATOR, PAYMENT_APPROVER }
    public record Membership(UUID customerId, Set<Role> roles) {
        public Membership { Objects.requireNonNull(customerId); roles = Set.copyOf(roles); }
    }
    private Map<String, Membership> memberships = Map.of();
    public Map<String, Membership> getMemberships() { return memberships; }
    public void setMemberships(Map<String, Membership> value) { memberships = Map.copyOf(value); }
}
