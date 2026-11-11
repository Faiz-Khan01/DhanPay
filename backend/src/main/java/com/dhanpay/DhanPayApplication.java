package com.dhanpay;

import com.dhanpay.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the DhanPay FinTech Transaction Processing Engine.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class DhanPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DhanPayApplication.class, args);
    }
}
