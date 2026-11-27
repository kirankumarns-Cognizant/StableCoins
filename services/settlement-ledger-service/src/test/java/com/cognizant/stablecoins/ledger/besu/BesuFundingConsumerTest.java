package com.cognizant.stablecoins.ledger.besu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuFundingConsumerTest {
    final BesuFundingHandler handler=mock(BesuFundingHandler.class);
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(BesuFundingConsumer.class)
        .withBean(BesuFundingHandler.class,()->handler);
    @Test void bothExplicitGatesAreRequired(){
        runner.run(c->assertThat(c).doesNotHaveBean(BesuFundingConsumer.class));
        runner.withPropertyValues("app.besu.accounting.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuFundingConsumer.class));
        runner.withPropertyValues("app.besu.funding-events.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuFundingConsumer.class));
        runner.withPropertyValues("app.besu.accounting.enabled=true","app.besu.funding-events.enabled=true")
            .run(c->{assertThat(c).hasSingleBean(BesuFundingConsumer.class);c.getBean(BesuFundingConsumer.class).consume("committed");verify(handler).handle("committed");});
    }
    @Test void failurePropagatesInsteadOfAcknowledging(){
        doThrow(new IllegalStateException("COMMITTED_BESU_FUNDING_REQUIRED")).when(handler).handle("untrusted");
        assertThatThrownBy(()->new BesuFundingConsumer(handler).consume("untrusted")).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");
    }
    @Test void redeliveryUsesTheSameHandlerContract(){
        var consumer=new BesuFundingConsumer(handler);consumer.consume("committed");consumer.consume("committed");
        verify(handler,times(2)).handle("committed");
    }
}
