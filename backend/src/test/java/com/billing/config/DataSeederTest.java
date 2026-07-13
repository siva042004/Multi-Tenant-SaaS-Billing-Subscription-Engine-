package com.billing.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.billing.repository.PlanRepository;
import com.billing.repository.SubscriptionRepository;
import com.billing.repository.TenantRepository;

@SpringBootTest
@ActiveProfiles("test")
class DataSeederTest {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void seedsDefaultPlansAndSubscriptionsOnStartup() {
        assertThat(planRepository.findByCode("BASIC")).isPresent();
        assertThat(planRepository.findByCode("PRO")).isPresent();
        assertThat(tenantRepository.count()).isGreaterThan(0);
        assertThat(subscriptionRepository.count()).isGreaterThan(0);
    }
}
