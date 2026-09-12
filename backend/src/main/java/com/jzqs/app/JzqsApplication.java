package com.jzqs.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class JzqsApplication {
    public static void main(String[] args) {
        SpringApplication.run(JzqsApplication.class, args);
    }

    /**
     * 微信等第三方接口调用统一走该 RestTemplate。
     *
     * <p>必须显式设置超时：默认实现的连接/读取超时是「无限等待」，上游网络一旦卡住，
     * 调用线程会被永久挂起。取餐订阅消息的定时任务跑在后台线程池里，线程挂起会把池子占满，
     * 进而让整个到点发送流程停摆、恢复后成批补发（即"11:30 的消息拖到下午 4 点才收到"）。
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }
}
