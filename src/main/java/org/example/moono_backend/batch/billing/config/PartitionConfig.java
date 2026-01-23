package org.example.moono_backend.batch.billing.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PartitionConfig {

    private static final int GRID_SIZE = 4;

    @Bean
    public Partitioner publicInfoHashPartitioner() {
        return gridSize -> {
            Map<String, ExecutionContext> result = new HashMap<>();
            for (int i = 0; i < gridSize; i++) {
                ExecutionContext ctx = new ExecutionContext();
                ctx.putInt("partition", i);
                ctx.putInt("gridSize", gridSize);
                result.put("partition" + i, ctx);
            }
            return result;
        };
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(GRID_SIZE);
        ex.setMaxPoolSize(GRID_SIZE);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("billing-part-");
        ex.initialize();
        return ex;
    }
}
