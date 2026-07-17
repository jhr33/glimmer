package com.glimmer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * glimmer 后端启动类
 * 扫描 com.glimmer 下所有组件（controller、service、config 等）
 */
@SpringBootApplication
@MapperScan("com.glimmer.mapper")
@EnableScheduling
public class GlimmerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlimmerApplication.class, args);
    }
}
