package com.flightmonitor.core.recipient.boundary;

import com.flightmonitor.core.recipient.control.RecipientService;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flightmonitor.core.recipient.control.dto.RecipientRequest;
import com.flightmonitor.core.recipient.control.dto.RecipientResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recipients")
public class RecipientController {

    private final RecipientService service;

    public RecipientController(RecipientService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecipientResponse> listar(
            @RequestParam(required = false, name = "active") Boolean apenasAtivos) {
        return service.listar(apenasAtivos);
    }

    @GetMapping("/{id}")
    public RecipientResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<RecipientResponse> criar(@Valid @RequestBody RecipientRequest req) {
        RecipientResponse criado = service.criar(req);
        return ResponseEntity
                .created(URI.create("/api/recipients/" + criado.id()))
                .body(criado);
    }

    @PutMapping("/{id}")
    public RecipientResponse atualizar(@PathVariable Long id, @Valid @RequestBody RecipientRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
