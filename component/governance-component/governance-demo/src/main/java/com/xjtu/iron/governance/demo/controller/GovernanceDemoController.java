package com.xjtu.iron.governance.demo.controller;


import com.xjtu.iron.governance.api.template.GovernanceTemplate;
import com.xjtu.iron.governance.demo.service.CouponService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo/governance")
public class GovernanceDemoController {

    private final CouponService couponService;

    private final GovernanceTemplate governanceTemplate;

    public GovernanceDemoController(CouponService couponService,
                                    GovernanceTemplate governanceTemplate) {
        this.couponService = couponService;
        this.governanceTemplate = governanceTemplate;
    }

    @GetMapping("/annotation")
    public String annotation(@RequestParam Long userId) {
        return couponService.queryCoupon(userId);
    }

    @GetMapping("/timeout")
    public String timeout(@RequestParam Long userId) {
        return couponService.slowQuery(userId);
    }

    @GetMapping("/template")
    public String template(@RequestParam Long userId) {
        return governanceTemplate.execute(
                "internal.coupon.queryUserCoupon",
                () -> "template-result-" + userId,
                throwable -> "template-fallback, reason=" + throwable.getClass().getSimpleName()
        );
    }
}
