package com.doom.fg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching // 开启缓存功能
@EnableScheduling // 开启定时任务功能
public class FridgeGuardianApplication {

    public static void main(String[] args) {
        SpringApplication.run(FridgeGuardianApplication.class, args);
    }

}
