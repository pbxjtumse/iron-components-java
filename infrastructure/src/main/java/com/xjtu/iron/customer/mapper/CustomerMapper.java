package com.xjtu.iron.customer.mapper;

import com.xjtu.iron.customer.dbo.CustomerDO;
public interface CustomerMapper{

  CustomerDO getById(String customerId);
}
