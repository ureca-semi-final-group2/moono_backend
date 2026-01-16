package org.example.moono_backend.Kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

public class KafkaConfig {

    //1. 토픽 자동 생성 설정
    @Bean
    public NewTopic testTopic() {
        return TopicBuilder.name("test-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic dispatchTopic(){
        return TopicBuilder.name("dispatch.topic")
                .partitions(1)
                .replicas(1)
                .build();
    }



}
