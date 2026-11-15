package com.cognizant.stablecoins.platform.security;
public class AccessFailure extends RuntimeException {
    private final String code; private final int status;
    public AccessFailure(String code,int status){super(code);this.code=code;this.status=status;}
    public String code(){return code;} public int status(){return status;}
}
