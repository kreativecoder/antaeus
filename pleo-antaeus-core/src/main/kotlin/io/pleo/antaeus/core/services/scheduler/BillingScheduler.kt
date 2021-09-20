package io.pleo.antaeus.core.services.scheduler

import io.pleo.antaeus.core.services.BillingService
import mu.KotlinLogging
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory

class BillingScheduler(private val billingService: BillingService) {
    private val logger = KotlinLogging.logger { }

    private val scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()
    private val cron = System.getenv().getOrDefault("BILLING_SCHEDULER_CRON", "0 0 8 1 1/1 ? *")

    init {
        scheduler.context["billingService"] = billingService
        scheduler.start()
        logger.info { "Scheduler started..." }
    }

    /**
     * Create job and trigger and schedule it to run on the first day of each month at 8am.
     */
    fun schedule() {
        val jobDetail = createJobDetail()
        jobDetail.jobDataMap["billingService"] = billingService

        val cronTrigger = createTrigger()

        scheduler.scheduleJob(jobDetail, cronTrigger)
        logger.info { "Invoices job scheduled." }
    }

    private fun createJobDetail(): JobDetail {
        return JobBuilder.newJob()
            .ofType(BillingJob::class.java)
            .withIdentity("invoices")
            .build()
    }

    private fun createTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .withSchedule(CronScheduleBuilder.cronSchedule(cron))
            .build()
    }
}