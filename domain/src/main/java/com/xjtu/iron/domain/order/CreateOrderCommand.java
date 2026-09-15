package com.xjtu.iron.domain.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class CreateOrderCommand {
    private String userId;
    private String skuId;
}
