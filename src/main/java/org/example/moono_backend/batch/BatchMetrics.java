package org.example.moono_backend.batch;

import java.util.concurrent.atomic.AtomicLong;

public class BatchMetrics {
    public final AtomicLong dbReadNanos = new AtomicLong(0);
    public final AtomicLong mapNanos = new AtomicLong(0);
    public final AtomicLong kafkaSendNanos = new AtomicLong(0);

    public final AtomicLong kafkaSuccess = new AtomicLong(0);
    public final AtomicLong kafkaFail = new AtomicLong(0);
}