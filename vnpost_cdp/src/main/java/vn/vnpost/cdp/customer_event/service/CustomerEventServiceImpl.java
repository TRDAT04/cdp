package vn.vnpost.cdp.customer_event.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;

@Slf4j
@Service
public class CustomerEventServiceImpl implements CustomerEventService {
    @Override
    public void process(CustomerEventMessage message){
        log.info("Processing customer event: {}",message.getMessageId());
    }
}
