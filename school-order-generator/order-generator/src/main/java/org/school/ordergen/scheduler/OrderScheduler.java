package org.school.ordergen.scheduler;

import org.school.ordergen.service.OrderGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderGenerationService orderGenerationService;

    @Scheduled(cron = "${order.scheduler.cron:0 0 0 * * *}")
    public void runGeneration() {
        log.info("Запуск плановой генерации приказов");
        orderGenerationService.generateOrders();
    }
}