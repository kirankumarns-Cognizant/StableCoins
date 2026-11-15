package com.cognizant.stablecoins.payment.event;

/**
 * Individual compliance check result carried inside ComplianceCompletedPayload.
 * simulation=true is ALWAYS set — these checks are never real.
 */
public class ComplianceCheckResult {

    private String checkType;
    private String result;
    private boolean simulation;
    private String reason;

    public ComplianceCheckResult() {}

    public ComplianceCheckResult(String checkType, String result, boolean simulation, String reason) {
        this.checkType = checkType;
        this.result = result;
        this.simulation = simulation;
        this.reason = reason;
    }

    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
