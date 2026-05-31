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
                    简知轻食骑手小程序后端 API
                    
                    ## 功能模块
                    - 骑手认证：登录、个人信息
                    - 订单管理：订单列表、订单详情、标记完成、撤回状态
                    - 配送管理：排序、上传图片、地理编码
                    - 异常上报：配送异常记录
                    
                    ## 认证方式
                    当前使用 URL 参数传递骑手身份（riderName），后续将升级为 JWT Token 认证。
                    
                    ## 版本历史
                    - v1.0.0 (2026-05-22): 初始版本，统一 API 路径
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
                    .url("http://localhost:8080")
                    .description("本地开发环境"),
                new Server()
                    .url("https://api.jzqs.com")
                    .description("生产环境")
            ));
    }
}
