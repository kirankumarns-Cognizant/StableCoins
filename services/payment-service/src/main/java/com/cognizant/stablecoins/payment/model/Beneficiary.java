package com.cognizant.stablecoins.payment.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "beneficiary")
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "beneficiary_id")
    private UUID beneficiaryId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "wallet_address")
    private String walletAddress;

    @Column(name = "status", nullable = false)
    private String status;

    @PrePersist
    void prePersist() {
        if (status == null) status = "ACTIVE";
    }

    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
