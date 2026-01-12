package org.example.moono_backend.batch;

import org.example.moono_backend.batch.dto.BillingWriteItem;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;

@Component
public class LastIdListener implements StepExecutionListener, ItemWriteListener<BillingWriteItem> {

    public static final String KEY_LAST_ID = "lastId";
    private StepExecution stepExecution;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        if (!stepExecution.getExecutionContext().containsKey(KEY_LAST_ID)) {
            stepExecution.getExecutionContext().putLong(KEY_LAST_ID, 0L);
        }
    }

    @Override
    public void afterWrite(Chunk<? extends BillingWriteItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        long maxId = items.getItems().stream()
                .mapToLong(BillingWriteItem::memberCredentialId)
                .max()
                .orElse(stepExecution.getExecutionContext().getLong(KEY_LAST_ID));

        stepExecution.getExecutionContext().putLong(KEY_LAST_ID, maxId);
    }
}
