package com.cognizant.stablecoins.reconciliation.flow;
@org.springframework.web.bind.annotation.RestControllerAdvice
public class LifecycleErrors {
 @org.springframework.web.bind.annotation.ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
 public org.springframework.http.ResponseEntity<java.util.Map<String,String>> invalid(RuntimeException error){
  String code=error.getMessage();if(code==null||!code.matches("[A-Z][A-Z0-9_]{0,99}"))code="LIFECYCLE_REQUEST_REJECTED";
  return org.springframework.http.ResponseEntity.status(409).body(java.util.Map.of("code",code));
 }
}
