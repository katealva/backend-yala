package com.yala.controller;

import com.yala.dto.appraisal.RequestAppraisalDTO;
import com.yala.dto.appraisal.ResponseAppraisalDTO;
import com.yala.service.AppraisalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appraisal")
@Tag(name = "Appraisal", description = "Agente de Tasación por Foto (OpenAI Vision)")
public class AppraisalController {

    private final AppraisalService appraisalService;

    public AppraisalController(AppraisalService appraisalService) {
        this.appraisalService = appraisalService;
    }

    @PostMapping("/identify")
    @Operation(summary = "Identifica un coleccionable a partir de su foto — devuelve JSON estructurado")
    public ResponseEntity<ResponseAppraisalDTO> identify(@Valid @RequestBody RequestAppraisalDTO request) {
        return ResponseEntity.ok(appraisalService.identify(request.imageBase64()));
    }
}
