package com.jzqs.app.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) 配置
 * 访问地址: http://localhost:8080/swagger-ui.html
 * 
 * @author Kiro AI
 * @since 2026-05-22
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("简知轻食 API 文档")
                .version("1.0.0")
                .description("""
                    简知轻食统一后端 API
                    
                    ## 功能模块
                    - 管理后台：订单、调度、售后、设置
                    - 顾客小程序：登录、下单、地址、订阅规则
                    - 骑手工作台：登录、队列、回执、异常上报
                    - 订单管理：订单列表、订单详情、标记完成、撤回状态
                    - 配送管理：排序、上传图片、地理编码
                    - 异常上报：配送异常记录
                    
                    ## 认证方式
                    当前统一使用 JWT Token 鉴权，服务端从 Token Claim 派生顾客、管理员和骑手身份。
                    
                    ## 版本历史
                    - v1.1.0 (2026-06-27): 完成统一 JWT 鉴权与骑手工作台重构
                    """)
                .contact(new Contact()
                    .name("简知轻食技术团队")
                    .email("tech@jzqs.com")
                    .url("https://jzqs.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8081")
                    .description("本地开发环境"),
                new Server()
                    .url("https://api.jzqs.com")
                    .description("生产环境")
            ));
    }
}
