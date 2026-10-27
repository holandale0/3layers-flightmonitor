package com.flightmonitor.core.recipient.boundary;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.flightmonitor.core.recipient.control.PushDeviceService;
import com.flightmonitor.core.recipient.control.dto.PushDeviceRequest;
import com.flightmonitor.core.recipient.control.dto.PushDeviceResponse;

import jakarta.validation.Valid;

/**
 * Aparelhos de um destinatario. O registro e feito pelo aplicativo Android; a
 * listagem e a exclusao, por qualquer tela do painel.
 */
@RestController
public class PushDeviceController {

    private final PushDeviceService service;

    public PushDeviceController(PushDeviceService service) {
        this.service = service;
    }

    @GetMapping("/api/recipients/{recipientId}/push-devices")
    public List<PushDeviceResponse> listar(@PathVariable Long recipientId) {
        return service.listar(recipientId);
    }

    /** 200, e nao 201: registrar de novo o mesmo aparelho e o caso comum. */
    @PostMapping("/api/recipients/{recipientId}/push-devices")
    public PushDeviceResponse registrar(
            @PathVariable Long recipientId, @Valid @RequestBody PushDeviceRequest req) {
        return service.registrar(recipientId, req);
    }

    @DeleteMapping("/api/push-devices/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
