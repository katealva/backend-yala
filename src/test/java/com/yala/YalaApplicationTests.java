package com.yala;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.cloud.aws.region.static=us-east-1",
        "aws.access-key-id=test-access-key",
        "aws.secret-access-key=test-secret-key",
        "aws.s3.bucket=test-bucket"
})
class YalaApplicationTests {

    @MockitoBean
    private S3Client s3Client;

    @Test
    void contextLoads() {
    }
}
