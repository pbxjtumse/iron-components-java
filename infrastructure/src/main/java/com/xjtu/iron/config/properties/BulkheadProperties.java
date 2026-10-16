package com.xjtu.iron.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "bulkheads")
@Component
@Getter
@Setter
public class BulkheadProperties {

    private List<Item> items = new ArrayList<>();
    @Getter
    @Setter
    public static class Item {
        private String name;
        private Map<String, String> rule;
        private int limit;
    }
}

