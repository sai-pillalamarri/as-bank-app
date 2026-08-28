package com.asbank.account.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class CustomerClientConfig {

    @Bean
    RestClient customerServiceRestClient(
            RestClient.Builder builder,
            @Value("${asbank.customer-service.base-url}")
            String baseUrl,
            @Value("${asbank.customer-service.connect-timeout}")
            Duration connectTimeout,
            @Value("${asbank.customer-service.read-timeout}")
            Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}