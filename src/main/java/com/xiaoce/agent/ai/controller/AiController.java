package com.xiaoce.agent.ai.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AiController
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/30 11:38
 */
@RestController
@RequestMapping("/api/v1/ai")
@Valid
@Slf4j
public class AiController {

    @PostMapping("/session")
    public void session() {
        log.info("session");
    }
}
