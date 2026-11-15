package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.exception.PaymentException;
public class ApprovalConflictException extends PaymentException {
    public ApprovalConflictException(String code) { super(code, "Recommendation cannot be accepted: " + code); }
}
