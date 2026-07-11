package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.live.RequestFlashAuctionDTO;
import com.yala.dto.live.RequestLiveBidDTO;
import com.yala.dto.live.RequestLiveCommentDTO;
import com.yala.dto.live.RequestStartLiveDTO;
import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.dto.live.ResponseLiveBidDTO;
import com.yala.dto.live.ResponseLiveCommentDTO;
import com.yala.dto.live.ResponseLiveCommentSummaryDTO;
import com.yala.dto.live.ResponseLiveStreamDTO;
import com.yala.dto.live.ResponseLiveSummaryDTO;
import com.yala.dto.live.ResponseLiveTokenDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
@Tag(name = "Live", description = "Transmisiones en vivo: streams, subastas flash, pujas y chat en tiempo real")
public class LiveController {

    private final LiveStreamService liveStreamService;
    private final LiveAuctionService liveAuctionService;
    private final LiveBidService liveBidService;
    private final LiveCommentService liveCommentService;
    private final LiveCommentSummaryService liveCommentSummaryService;
    private final HighlightService highlightService;
    private final ProductVoiceService productVoiceService;
    private final LiveTranscriptionService liveTranscriptionService;

    // ----- Streams -----

    @GetMapping
    @Operation(summary = "Lista las transmisiones en vivo activas (para el carrusel de la home). Público.")
    public ResponseEntity<Page<ResponseLiveSummaryDTO>> listActive(
            @PageableDefault(size = 12, sort = "startedAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(liveStreamService.listActive(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una transmisión, con su subasta flash activa. Público.")
    public ResponseEntity<ResponseLiveStreamDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(liveStreamService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Inicia una transmisión (solo seller verificado). Devuelve el token publisher de LiveKit.")
    public ResponseEntity<ResponseLiveTokenDTO> start(
            @Valid @RequestBody RequestStartLiveDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liveStreamService.start(request, auth.getName()));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Termina la transmisión (solo el host).")
    public ResponseEntity<Void> end(@PathVariable Long id, Authentication auth) {
        liveStreamService.end(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/watch-token")
    @Operation(summary = "Token de visualización (subscribe-only) de LiveKit. Público; usa la identidad si hay sesión.")
    public ResponseEntity<ResponseLiveTokenDTO> watchToken(
            @PathVariable Long id, Authentication auth) {
        String email = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(liveStreamService.watchToken(id, email));
    }

    // ----- Flash auctions -----

    @PostMapping("/{id}/auctions")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Crea una subasta flash dentro del live (título, precio base, incremento — default 1).")
    public ResponseEntity<ResponseLiveAuctionDTO> createAuction(
            @PathVariable Long id, @Valid @RequestBody RequestFlashAuctionDTO request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liveAuctionService.create(id, request, auth.getName()));
    }

    @PostMapping("/auctions/{auctionId}/close")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Cierra la subasta flash: con pujas → vendida (orden + 48h de pago); sin pujas → desierta.")
    public ResponseEntity<ResponseLiveAuctionDTO> closeAuction(
            @PathVariable Long auctionId, Authentication auth) {
        return ResponseEntity.ok(liveAuctionService.close(auctionId, auth.getName()));
    }

    // ----- Bids -----

    @PostMapping("/auctions/{auctionId}/bids")
    @Operation(summary = "Puja en una subasta flash (usuario autenticado).")
    public ResponseEntity<ResponseLiveBidDTO> placeBid(
            @PathVariable Long auctionId, @Valid @RequestBody RequestLiveBidDTO request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liveBidService.place(auctionId, request, auth.getName()));
    }

    @GetMapping("/auctions/{auctionId}/bids")
    @Operation(summary = "Historial paginado de pujas de una subasta flash. Público.")
    public ResponseEntity<Page<ResponseLiveBidDTO>> findBids(
            @PathVariable Long auctionId,
            @PageableDefault(size = 20, sort = "amount", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(liveBidService.findByAuction(auctionId, pageable));
    }

    // ----- Chat -----

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comentarios recientes del chat del live. Público.")
    public ResponseEntity<Page<ResponseLiveCommentDTO>> listComments(
            @PathVariable Long id,
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(liveCommentService.listRecent(id, pageable));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Envía un comentario al chat del live (usuario autenticado).")
    public ResponseEntity<ResponseLiveCommentDTO> postComment(
            @PathVariable Long id, @Valid @RequestBody RequestLiveCommentDTO request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liveCommentService.post(id, request, auth.getName()));
    }

    @PostMapping("/{id}/comments/summary")
    @Operation(summary = "Resumen con IA de los comentarios del live (solo el host). No se transmite a los espectadores.")
    public ResponseEntity<ResponseLiveCommentSummaryDTO> summarizeComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") Integer limit,
            Authentication auth) {
        return ResponseEntity.ok(liveCommentSummaryService.summarize(id, auth.getName(), limit));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Mis transmisiones (incluye finalizadas) con sus clips de highlights. Solo el propio seller.")
    public ResponseEntity<java.util.List<com.yala.dto.live.ResponseMyLiveDTO>> myLives(Authentication auth) {
        return ResponseEntity.ok(liveStreamService.listMine(auth.getName()));
    }

    @GetMapping("/{id}/clips")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Clips de highlights auto-generados del live, para descargar (solo el host).")
    public ResponseEntity<java.util.List<com.yala.dto.live.ResponseLiveClipDTO>> clips(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(highlightService.listClips(id, auth.getName()));
    }

    @PostMapping("/{id}/detect-product")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Extrae con IA los atributos del coleccionable que el host describe en voz alta, "
            + "para pre-llenar una subasta flash (ADR-002). Solo el host.")
    public ResponseEntity<com.yala.dto.live.ResponseDetectedProductDTO> detectProduct(
            @PathVariable Long id,
            @RequestBody com.yala.dto.live.RequestDetectProductDTO body,
            Authentication auth) {
        return ResponseEntity.ok(productVoiceService.detect(id, auth.getName(),
                body != null ? body.transcript() : null));
    }

    @PostMapping(value = "/{id}/transcribe", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @Operation(summary = "Transcribe un chunk de audio del mic del host con gpt-4o-mini-transcribe "
            + "(cross-browser, reemplaza Web Speech API). Solo el host.")
    public ResponseEntity<com.yala.dto.live.ResponseTranscriptDTO> transcribe(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam("audio")
                    org.springframework.web.multipart.MultipartFile audio,
            Authentication auth) {
        return ResponseEntity.ok(liveTranscriptionService.transcribe(id, auth.getName(), audio));
    }

    // ----- Webhook -----

    @PostMapping("/webhook")
    @Operation(summary = "Webhook público de LiveKit (verificado por firma). Termina el live en room_finished.")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        liveStreamService.handleWebhook(payload, authHeader);
        return ResponseEntity.ok().build();
    }
}
