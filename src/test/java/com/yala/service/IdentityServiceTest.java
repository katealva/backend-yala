package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yala.dto.identity.ResponseSessionDTO;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IdentityServiceTest {

    /**
     * Regression for the seller-KYC bug: the Didit session request MUST include workflow_id.
     * Didit returns 400 "workflow_id: This field is required" when it's missing.
     */
    @Test
    void createSessionIncludesWorkflowIdInRequestBody() {
        UserRepository userRepo = mock(UserRepository.class);
        SellerApplicationRepository sellerRepo = mock(SellerApplicationRepository.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        IdentityService service = new IdentityService(userRepo, sellerRepo, messaging, builder);
        ReflectionTestUtils.setField(service, "diditApiKey", "test-key");
        ReflectionTestUtils.setField(service, "diditWorkflowId", "wf-1234");
        ReflectionTestUtils.setField(service, "diditCallbackUrl", "https://yala.dpdns.org/verify/done");

        when(userRepo.findByEmail("ada@yala.pe"))
                .thenReturn(Optional.of(User.builder().id(7L).email("ada@yala.pe").build()));

        server.expect(requestTo("https://verification.didit.me/v3/session/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.workflow_id").value("wf-1234"))
                .andExpect(jsonPath("$.vendor_data").value("7"))
                .andRespond(withSuccess(
                        "{\"url\":\"https://verify.didit.me/s/x\",\"session_id\":\"sess-1\"}",
                        MediaType.APPLICATION_JSON));

        ResponseSessionDTO dto = service.createSession("ada@yala.pe");

        assertThat(dto.url()).isEqualTo("https://verify.didit.me/s/x");
        assertThat(dto.sessionId()).isEqualTo("sess-1");
        server.verify();
    }

    @Test
    void isConfiguredRequiresApiKeyAndWorkflowId() {
        IdentityService service = new IdentityService(
                mock(UserRepository.class), mock(SellerApplicationRepository.class),
                mock(SimpMessagingTemplate.class), RestClient.builder());
        ReflectionTestUtils.setField(service, "diditApiKey", "k");
        ReflectionTestUtils.setField(service, "diditWorkflowId", "");
        assertThat(service.isConfigured()).isFalse();
        ReflectionTestUtils.setField(service, "diditWorkflowId", "wf");
        assertThat(service.isConfigured()).isTrue();
    }
}
