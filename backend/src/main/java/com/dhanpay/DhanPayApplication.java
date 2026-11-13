package com.dhanpay;

import com.dhanpay.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the DhanPay FinTech Transaction Processing Engine.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
public class DhanPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DhanPayApplication.class, args);
    }
}
