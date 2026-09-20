package com.print.shop.controller;

import com.print.shop.dto.DelayBoardEntry;
import com.print.shop.service.DelayBoardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 延误桌只有一个读口：交期、纸、版在后台并好，页面一次端走。 */
@RestController
@RequestMapping("/api/delay-board")
public class DelayBoardController {

    private final DelayBoardService service;

    public DelayBoardController(DelayBoardService service) {
        this.service = service;
    }

    @GetMapping
    public List<DelayBoardEntry> board() {
        return service.board();
    }
}
