package com.yala.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class S3ConfigTest {

    private S3Config configWithKeys(String accessKeyId, String secretAccessKey) {
        S3Config cfg = new S3Config();
        ReflectionTestUtils.setField(cfg, "accessKeyId", accessKeyId);
        ReflectionTestUtils.setField(cfg, "secretAccessKey", secretAccessKey);
        return cfg;
    }

    @Test
    void usesDefaultCredentialChainWhenNoStaticKeys() {
        // Caso prod: sin llaves → cae al rol de la task ECS.
        assertThat(configWithKeys("", "").credentialsProvider())
                .isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void usesDefaultCredentialChainWhenDummyKeys() {
        assertThat(configWithKeys("dummy-access-key", "dummy-secret-key").credentialsProvider())
                .isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void usesStaticCredentialsWhenRealKeysProvided() {
        assertThat(configWithKeys("AKIAREALKEY123", "realsecret123").credentialsProvider())
                .isInstanceOf(StaticCredentialsProvider.class);
    }
}
