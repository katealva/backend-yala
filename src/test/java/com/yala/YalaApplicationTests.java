package com.yala;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.cloud.aws.credentials.access-key=llave-falsa-de-prueba",
        "spring.cloud.aws.credentials.secret-key=secreto-falso-de-prueba",
        "spring.cloud.aws.region.static=us-east-1",
        "aws.s3.bucket=bucket-de-prueba"
})


class YalaApplicationTests {

    // Esto engaña a Spring Boot haciéndole creer que S3 está conectado
    @MockitoBean
    private S3Client s3Client;

    @Test
    void contextLoads() {
    }

}