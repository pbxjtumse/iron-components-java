package com.xjtu.iron.governance.core.event;


import com.xjtu.iron.governance.model.event.GovernanceEvent;
import com.xjtu.iron.governance.spi.event.GovernanceEventListener;

import java.util.ArrayList;
import java.util.List;

public class SimpleGovernanceEventBus implements GovernanceEventBus {

    private final List<GovernanceEventListener> listeners = new ArrayList<>();

    public SimpleGovernanceEventBus(List<GovernanceEventListener> listeners) {
        if (listeners != null) {
            this.listeners.addAll(listeners);
        }
    }

    @Override
    public void publish(GovernanceEvent event) {
        for (GovernanceEventListener listener : listeners) {
            if (listener.supports(event)) {
                listener.onEvent(event);
            }
        }
    }
}
