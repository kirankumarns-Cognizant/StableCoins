package com.cognizant.stablecoins.platform.security;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PlatformAccessErrors {
    @ExceptionHandler(AccessFailure.class)
    ResponseEntity<Map<String,String>> failure(AccessFailure error){return ResponseEntity.status(error.status()).body(Map.of("code",error.code(),"message",error.code()));}
}
