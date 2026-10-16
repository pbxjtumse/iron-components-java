package com.xjtu.iron.customer.gateway;

import com.xjtu.iron.customer.dbo.CustomerDO;
import com.xjtu.iron.customer.mapper.CustomerMapper;
import com.xjtu.iron.domain.customer.gateway.CustomerGateway;
import com.xjtu.iron.domain.customer.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomerGatewayImpl implements CustomerGateway {
    @Autowired
    private CustomerMapper customerMapper;

    public Customer getByById(String customerId){
      CustomerDO customerDO = customerMapper.getById(customerId);
      //Convert to Customer
      return null;
    }
}
