package com.jzqs.app.common.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class TransactionalRealtimePublisherTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    private RealtimeEventPublisher delegate;
    private TransactionalRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        delegate = mock(RealtimeEventPublisher.class);
        publisher = new TransactionalRealtimePublisher(delegate);
    }

    @Test
    void shouldPublishImmediatelyWhenNoTransactionIsActive() {
        RealtimeEvent event = RealtimeEvent.builder("system.home.changed")
            .audience("admin")
            .build();

        publisher.publish(event);

        verify(delegate).publish(event);
    }

    @Test
    void shouldPublishAfterCommitWhenTransactionIsActive() {
        RealtimeEvent event = RealtimeEvent.builder("dispatch.queue.changed")
            .audience("admin")
            .build();

        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                publisher.publish(event);
                verifyNoInteractions(delegate);
            }
        });

        verify(delegate).publish(event);
    }

    @Test
    void shouldNotPublishWhenTransactionRollsBack() {
        RealtimeEvent event = RealtimeEvent.builder("customer.order.changed")
            .audience("admin")
            .build();

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        try {
            template.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    publisher.publish(event);
                    status.setRollbackOnly();
                }
            });
        } catch (Exception ignored) {
            // No exception is expected for rollbackOnly, but keep the test focused on publish timing.
        }

        verifyNoInteractions(delegate);
    }
}
