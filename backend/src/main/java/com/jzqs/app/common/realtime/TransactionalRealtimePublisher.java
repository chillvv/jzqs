package com.jzqs.app.common.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionalRealtimePublisher {
    private final RealtimeEventPublisher delegate;

    public TransactionalRealtimePublisher(RealtimeEventPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(RealtimeEvent event) {
        if (event == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delegate.publish(event);
                }
            });
            return;
        }
        delegate.publish(event);
    }
}
