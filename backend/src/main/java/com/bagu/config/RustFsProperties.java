package com.bagu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bagu.rustfs")
public class RustFsProperties {

    private String endpoint;
    private String region = "us-east-1";
    private String bucket = "bagu-images";
    private String accessKey;
    private String secretKey;
    private String publicUrlPrefix = "/images";
}
