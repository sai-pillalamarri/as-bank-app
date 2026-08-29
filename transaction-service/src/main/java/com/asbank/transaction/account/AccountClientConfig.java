package com.asbank.transaction.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
class AccountClientConfig {

    @Bean
    RestClient accountServiceRestClient(
            RestClient.Builder builder,
            @Value("${asbank.account-service.base-url}")
            String baseUrl,
            @Value("${asbank.account-service.connect-timeout}")
            Duration connectTimeout,
            @Value("${asbank.account-service.read-timeout}")
            Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}