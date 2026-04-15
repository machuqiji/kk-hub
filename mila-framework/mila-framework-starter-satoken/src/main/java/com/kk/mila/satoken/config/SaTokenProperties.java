package com.kk.mila.satoken.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.satoken")
public class SaTokenProperties {
    private String tokenName = "Authorization";
    private long timeout = 7200;
    private String secretKey;
    private boolean isConcurrent = true;
    private boolean isShare = true;
    private String tokenStyle = "uuid";

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isConcurrent() {
        return isConcurrent;
    }

    public void setConcurrent(boolean concurrent) {
        isConcurrent = concurrent;
    }

    public boolean isShare() {
        return isShare;
    }

    public void setShare(boolean share) {
        isShare = share;
    }

    public String getTokenStyle() {
        return tokenStyle;
    }

    public void setTokenStyle(String tokenStyle) {
        this.tokenStyle = tokenStyle;
    }
}
