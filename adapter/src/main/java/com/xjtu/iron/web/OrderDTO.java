package com.xjtu.iron.web;

import com.xjtu.iron.domain.order.Order;

public class OrderDTO {
    public static OrderDTO from(Order order){
        return new OrderDTO();
    }
}
