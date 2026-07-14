package com.jzqs.app.common.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebEncodingConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MediaType utf8Json = new MediaType("application", "json", StandardCharsets.UTF_8);
        MediaType utf8ProblemJson = new MediaType("application", "problem+json", StandardCharsets.UTF_8);
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
                continue;
            }
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
                List<MediaType> mediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                if (!mediaTypes.contains(utf8Json)) {
                    mediaTypes.add(0, utf8Json);
                }
                if (!mediaTypes.contains(utf8ProblemJson)) {
                    mediaTypes.add(1, utf8ProblemJson);
                }
                jacksonConverter.setSupportedMediaTypes(mediaTypes);
            }
        }
    }
}
