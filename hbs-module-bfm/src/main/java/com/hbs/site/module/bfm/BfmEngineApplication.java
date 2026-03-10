package com.hbs.site.module.bfm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${hbs.info.base-package}
@SpringBootApplication(scanBasePackages = {"${hbs.info.base-package}.module"})
public class BfmEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(BfmEngineApplication.class, args);
        log.info(".............. BFM-Core is running ..............");
    }
}
