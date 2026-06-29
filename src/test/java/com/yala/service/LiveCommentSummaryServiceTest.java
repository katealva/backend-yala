package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yala.dto.live.ResponseLiveCommentSummaryDTO;
import com.yala.exceptions.UnauthorizedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LiveCommentSummaryServiceTest {

    private final LiveStreamRepository streamRepo = mock(LiveStreamRepository.class);
    private final LiveCommentRepository commentRepo = mock(LiveCommentRepository.class);

    private LiveStream streamHostedBy(String email) {
        return LiveStream.builder().id(1L)
                .seller(User.builder().id(2L).name("Bob").email(email).build())
                .build();
    }

    private LiveComment comment(String author, String text) {
        return LiveComment.builder()
                .text(text).user(User.builder().id(9L).name(author).build())
                .build();
    }

    private LiveCommentSummaryService service(RestClient.Builder builder, String apiKey) {
        return new LiveCommentSummaryService(streamRepo, commentRepo, builder, apiKey,
                "https://api.openai.com", "gpt-5-nano");
    }

    @Test
    void rejectsWhenCallerIsNotTheHost() {
        when(streamRepo.findById(1L)).thenReturn(Optional.of(streamHostedBy("bob@yala.pe")));
        LiveCommentSummaryService svc = service(RestClient.builder(), "sk-test");

        assertThatThrownBy(() -> svc.summarize(1L, "intruder@yala.pe", 50))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void returnsFallbackWhenNotConfigured() {
        when(streamRepo.findById(1L)).thenReturn(Optional.of(streamHostedBy("bob@yala.pe")));
        when(commentRepo.findByLiveStreamIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment("Ana", "¿precio?"))));
        LiveCommentSummaryService svc = service(RestClient.builder(), "");

        ResponseLiveCommentSummaryDTO dto = svc.summarize(1L, "bob@yala.pe", 50);

        assertThat(dto.summary()).contains("no está configurado");
        assertThat(dto.commentCount()).isEqualTo(1);
    }

    @Test
    void summarizesViaOpenAiWhenConfigured() {
        when(streamRepo.findById(1L)).thenReturn(Optional.of(streamHostedBy("bob@yala.pe")));
        when(commentRepo.findByLiveStreamIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        comment("Ana", "¿hacen envío a provincia?"),
                        comment("Leo", "ofrezco 120"))));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-5-nano"))
                .andExpect(jsonPath("$.messages[1].content").exists())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"• La audiencia pregunta por envíos.\"}}]}",
                        MediaType.APPLICATION_JSON));

        ResponseLiveCommentSummaryDTO dto = service(builder, "sk-test").summarize(1L, "bob@yala.pe", 50);

        assertThat(dto.summary()).contains("envíos");
        assertThat(dto.commentCount()).isEqualTo(2);
        server.verify();
    }
}
