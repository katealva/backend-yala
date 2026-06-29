package com.yala.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JsonPeServiceTest {

    @Test
    void shouldReturnRecordWhenJsonPeRespondsSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JsonPeService service = new JsonPeService(builder, "https://api.json.pe", "test-key");

        String body = "{\"success\":true,\"message\":\"exito\",\"data\":{"
                + "\"nombres\":\"JOSE PEDRO\",\"apellido_paterno\":\"CASTILLO\","
                + "\"apellido_materno\":\"TERRONES\",\"nombre_completo\":\"CASTILLO TERRONES, JOSE PEDRO\"}}";
        server.expect(requestTo("https://api.json.pe/api/dni"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var record = service.lookup("27427864");

        assertThat(record).isPresent();
        assertThat(record.get().nombres()).isEqualTo("JOSE PEDRO");
        assertThat(record.get().apellidoPaterno()).isEqualTo("CASTILLO");
        server.verify();
    }

    @Test
    void shouldDecodeLatin1NamesWithEnie() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JsonPeService service = new JsonPeService(builder, "https://api.json.pe", "test-key");

        String body = "{\"success\":true,\"data\":{"
                + "\"nombres\":\"MARIA\",\"apellido_paterno\":\"ZUÑIGA\","
                + "\"apellido_materno\":\"PEÑA\",\"nombre_completo\":\"ZUÑIGA PEÑA, MARIA\"}}";
        // JSON.pe envía Latin-1 sin charset (Ñ = 0xD1), no UTF-8 — debe decodificarse igual.
        server.expect(requestTo("https://api.json.pe/api/dni"))
                .andRespond(withSuccess(body.getBytes(StandardCharsets.ISO_8859_1),
                        MediaType.APPLICATION_JSON));

        var record = service.lookup("12345678");

        assertThat(record).isPresent();
        assertThat(record.get().apellidoPaterno()).isEqualTo("ZUÑIGA");
        assertThat(record.get().apellidoMaterno()).isEqualTo("PEÑA");
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsNotSuccessful() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JsonPeService service = new JsonPeService(builder, "https://api.json.pe", "test-key");

        server.expect(requestTo("https://api.json.pe/api/dni"))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"no encontrado\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.lookup("00000000")).isEmpty();
    }

    @Test
    void shouldReturnEmptyAndSkipCallWhenNotConfigured() {
        JsonPeService service = new JsonPeService(RestClient.builder(), "https://api.json.pe", "");
        assertThat(service.isConfigured()).isFalse();
        assertThat(service.lookup("27427864")).isEmpty();
    }
}
