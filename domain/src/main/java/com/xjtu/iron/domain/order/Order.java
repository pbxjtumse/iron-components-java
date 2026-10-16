package com.xjtu.iron.domain.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Order{
    private String userId;
    private String skuId;

}
