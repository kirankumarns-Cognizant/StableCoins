package com.cognizant.stablecoins.payment.flow;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PaymentFlowWorkerTest {
 @Test void databaseDeferralReportsSqlStateWithoutLeakingExceptionPayload(){
  var jdbc=mock(JdbcTemplate.class);var flow=mock(PaymentFlowService.class);var id=UUID.randomUUID();
  when(jdbc.queryForList(contains("payment_flow_preparation"),eq(UUID.class))).thenReturn(List.of(id));
  when(jdbc.queryForList(contains("payment_execution"),eq(UUID.class))).thenReturn(List.of());
  doThrow(new DataIntegrityViolationException("sensitive SQL parameters",new SQLException("secret-token-and-wallet-key","23503"))).when(flow).finishPreparation(id);
  var logger=(Logger)LoggerFactory.getLogger(PaymentFlowWorker.class);var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
  try{
   new PaymentFlowWorker(jdbc,flow).tick();
   assertThat(appender.list).hasSize(1);
   var entry=appender.list.getFirst();
   assertThat(entry.getFormattedMessage()).contains("preparation_deferred","requestId="+id,"errorType=DataIntegrityViolationException","causeType=SQLException","sqlState=23503").doesNotContain("sensitive","secret-token","wallet-key");
   assertThat(entry.getThrowableProxy()).isNull();
  } finally {logger.detachAppender(appender);appender.stop();}
 }
}
