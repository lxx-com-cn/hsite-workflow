package com.hbs.site.server;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${hbs.info.base-package}
@SpringBootApplication(scanBasePackages = {"${hbs.info.base-package}.server", "${hbs.info.base-package}.module"})
public class HbsServerApplication {

    public static void main(String[] args) {
        ///SpringApplication.run(HbsServerApplication.class, args);
        SpringApplication springApplication = new SpringApplication(HbsServerApplication.class);
        //Banner.Mode.OFF 关闭
        springApplication.setBannerMode(Banner.Mode.CONSOLE);
        springApplication.run(args);
    }

}
