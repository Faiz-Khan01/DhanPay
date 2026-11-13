package com.dhanpay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically executes due automated recurring transfers and standing orders.
 */
@Component
public class ScheduledRecurringJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRecurringJob.class);

    private final RecurringTransferService recurringTransferService;

    public ScheduledRecurringJob(RecurringTransferService recurringTransferService) {
        this.recurringTransferService = recurringTransferService;
    }

    @Scheduled(
            fixedDelayString = "${dhanpay.recurring.fixed-delay-ms:60000}",
            initialDelayString = "${dhanpay.recurring.initial-delay-ms:15000}"
    )
    public void executeDueStandingOrders() {
        int executed = recurringTransferService.executeDueRules();
        if (executed > 0) {
            log.info("Executed {} due standing order recurring transfer(s)", executed);
        }
    }
}
