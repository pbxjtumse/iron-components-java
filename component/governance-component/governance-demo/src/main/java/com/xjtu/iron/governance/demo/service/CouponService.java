package com.xjtu.iron.governance.demo.service;


import com.xjtu.iron.governance.api.annotation.GovernedCall;
import com.xjtu.iron.governance.demo.client.MockCouponClient;
import org.springframework.stereotype.Service;

@Service
public class CouponService {

    private final MockCouponClient mockCouponClient;

    public CouponService(MockCouponClient mockCouponClient) {
        this.mockCouponClient = mockCouponClient;
    }

    @GovernedCall(
            name = "internal.coupon.queryUserCoupon",
            downstream = "coupon-service",
            operation = "queryUserCoupon",
            fallbackMethod = "queryCouponFallback"
    )
    public String queryCoupon(Long userId) {
        return mockCouponClient.queryCoupon(userId);
    }

    public String queryCouponFallback(Long userId, Throwable throwable) {
        return "empty-coupon-list, reason=" + throwable.getClass().getSimpleName();
    }

    @GovernedCall(
            name = "internal.coupon.slowQuery",
            downstream = "coupon-service",
            operation = "slowQuery",
            fallbackMethod = "slowQueryFallback"
    )
    public String slowQuery(Long userId) {
        return mockCouponClient.slowQuery(userId);
    }

    public String slowQueryFallback(Long userId, Throwable throwable) {
        return "slow-query-fallback, reason=" + throwable.getClass().getSimpleName();
    }
}
