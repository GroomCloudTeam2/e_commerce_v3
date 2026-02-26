package com.groom.order.presentation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final StreamsBuilderFactoryBean factoryBean;

    @GetMapping("/{storeId}")
    public Map<String, Object> getStoreRevenue(@PathVariable String storeId) {
        KafkaStreams kafkaStreams = factoryBean.getKafkaStreams();
        if (kafkaStreams == null) {
            throw new IllegalStateException("Kafka Streams is not running");
        }

        ReadOnlyWindowStore<String, Long> store = kafkaStreams.store(
                StoreQueryParameters.fromNameAndType("store-revenue-store", QueryableStoreTypes.windowStore()));

        // Query for the last 1 hour + 1 minute (to cover current window)
        Instant timeFrom = Instant.now().minusSeconds(3600);
        Instant timeTo = Instant.now().plusSeconds(60);

        WindowStoreIterator<Long> iterator = store.fetch(storeId, timeFrom, timeTo);

        Map<String, Object> result = new HashMap<>();
        long totalRevenue = 0L;
        
        while (iterator.hasNext()) {
            KeyValue<Long, Long> next = iterator.next();
            long windowStart = next.key;
            long amount = next.value;
            totalRevenue += amount;
            result.put(Instant.ofEpochMilli(windowStart).toString(), amount);
        }
        iterator.close();

        Map<String, Object> finalResponse = new HashMap<>();
        finalResponse.put("storeId", storeId);
        finalResponse.put("windows", result);
        finalResponse.put("totalRevenueInLookback", totalRevenue);
        
        return finalResponse;
    }
}
