package com.xjtu.iron.governance.core.event;

import com.xjtu.iron.governance.model.event.GovernanceEvent;
import com.xjtu.iron.governance.spi.event.GovernanceEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingGovernanceEventListener implements GovernanceEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingGovernanceEventListener.class);

    @Override
    public boolean supports(GovernanceEvent event) {
        return true;
    }

    @Override
    public void onEvent(GovernanceEvent event) {
        log.info("[governance-event] type={}, resource={}, attributes={}",
                event.getEventType(),
                event.getResourceName(),
                event.getAttributes());
    }
}
