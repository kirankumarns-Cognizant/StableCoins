package com.cognizant.stablecoins.payment.event;

import java.util.List;

/**
 * Payload for the ComplianceCompleted domain event.
 * overallResult is APPROVED or REJECTED.
 * simulation=true is ALWAYS set.
 */
public class ComplianceCompletedPayload {

    private String overallResult;
    private boolean simulation;
    private List<ComplianceCheckResult> checks;

    public ComplianceCompletedPayload() {}

    public ComplianceCompletedPayload(String overallResult, boolean simulation,
                                       List<ComplianceCheckResult> checks) {
        this.overallResult = overallResult;
        this.simulation = simulation;
        this.checks = checks;
    }

    public String getOverallResult() { return overallResult; }
    public void setOverallResult(String overallResult) { this.overallResult = overallResult; }
    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }
    public List<ComplianceCheckResult> getChecks() { return checks; }
    public void setChecks(List<ComplianceCheckResult> checks) { this.checks = checks; }
}
