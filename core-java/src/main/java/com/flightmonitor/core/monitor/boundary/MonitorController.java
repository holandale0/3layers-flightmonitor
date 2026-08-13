package com.flightmonitor.core.monitor.boundary;

import com.flightmonitor.core.monitor.control.MonitorService;

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

import com.flightmonitor.core.monitor.control.dto.MonitorRequest;
import com.flightmonitor.core.monitor.control.dto.MonitorResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService service;

    public MonitorController(MonitorService service) {
        this.service = service;
    }

    @GetMapping
    public List<MonitorResponse> listar(
            @RequestParam(required = false, name = "active") Boolean apenasAtivos) {
        return service.listar(apenasAtivos);
    }

    @GetMapping("/{id}")
    public MonitorResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> criar(@Valid @RequestBody MonitorRequest req) {
        MonitorResponse criado = service.criar(req);
        return ResponseEntity
                .created(URI.create("/api/monitors/" + criado.id()))
                .body(criado);
    }

    @PutMapping("/{id}")
    public MonitorResponse atualizar(@PathVariable Long id, @Valid @RequestBody MonitorRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
