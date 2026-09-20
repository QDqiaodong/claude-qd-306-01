package com.print.shop.controller;

import com.print.shop.dto.DelayDeskRow;
import com.print.shop.service.DelayDeskService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 延误桌：一张桌同时读到交期、纸张、印版。
 * 桌上的天数、加急、原因全部按请求那一刻现算，不存状态。
 */
@RestController
@RequestMapping("/api/delay-desk")
public class DelayDeskController {

    private final DelayDeskService service;

    public DelayDeskController(DelayDeskService service) {
        this.service = service;
    }

    @GetMapping
    public List<DelayDeskRow> list() {
        return service.list();
    }
}
