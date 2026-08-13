package com.flightmonitor.core.monitor.control;

import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.monitor.entity.Monitor;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.common.ConflitoException;
import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.monitor.control.dto.MonitorRequest;
import com.flightmonitor.core.monitor.control.dto.MonitorResponse;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;

@Service
@Transactional(readOnly = true)
public class MonitorService {

    private final MonitorRepository monitores;
    private final RecipientRepository destinatarios;

    public MonitorService(MonitorRepository monitores, RecipientRepository destinatarios) {
        this.monitores = monitores;
        this.destinatarios = destinatarios;
    }

    public List<MonitorResponse> listar(Boolean apenasAtivos) {
        List<Monitor> encontrados = Boolean.TRUE.equals(apenasAtivos)
                ? monitores.findByActiveTrue()
                : monitores.findAll();

        // Carrega os destinatarios de cada um para nao estourar lazy loading
        // na serializacao — open-in-view esta desligado de proposito.
        return encontrados.stream()
                .map(m -> buscarEntidade(m.getId()))
                .map(MonitorResponse::de)
                .toList();
    }

    public MonitorResponse buscar(Long id) {
        return MonitorResponse.de(buscarEntidade(id));
    }

    @Transactional
    public MonitorResponse criar(MonitorRequest req) {
        Monitor m = new Monitor();
        aplicar(req, m);
        // Um monitor novo deve ser varrido na primeira passada do scheduler.
        m.setNextSearchAt(Instant.now());
        return MonitorResponse.de(monitores.saveAndFlush(m));
    }

    @Transactional
    public MonitorResponse atualizar(Long id, MonitorRequest req) {
        Monitor m = buscarEntidade(id);
        aplicar(req, m);
        return MonitorResponse.de(monitores.saveAndFlush(m));
    }

    @Transactional
    public void excluir(Long id) {
        Monitor m = monitores.findById(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));
        // O historico de precos sobrevive: a FK e ON DELETE SET NULL (D-016).
        monitores.delete(m);
    }

    private Monitor buscarEntidade(Long id) {
        return monitores.findByIdComDestinatarios(id)
                .orElseThrow(() -> new NotFoundException("Monitor", id));
    }

    private void aplicar(MonitorRequest req, Monitor m) {
        if (req.origin().equals(req.destination())) {
            throw new ConflitoException("origem e destino nao podem ser iguais");
        }

        m.setLabel(req.label());
        m.setOrigin(req.origin());
        m.setDestination(req.destination());
        m.setDepartureWindowStart(req.departureWindowStart());
        m.setDepartureWindowEnd(req.departureWindowEnd());
        m.setReturnWindowStart(req.returnWindowStart());
        m.setReturnWindowEnd(req.returnWindowEnd());
        m.setMinStayDays(req.minStayDays());
        m.setMaxStayDays(req.maxStayDays());
        m.setMaxPrice(req.maxPrice());
        m.setCurrency(req.currency());
        m.setMaxStops(req.maxStops());
        m.setPassengers(req.passengers());
        m.setActive(req.active());
        m.setSearchIntervalMinutes(req.searchIntervalMinutes());

        m.getRecipients().clear();
        m.getRecipients().addAll(resolverDestinatarios(req.recipientIds()));

        // ------------------------------------------------ preferencias (E2.6)

        m.setPrefereVooDireto(Boolean.TRUE.equals(req.prefereVooDireto()));

        // Substituicao, e nao acrescimo: a lista enviada e a lista final. Uma
        // API que so acrescenta nao teria como remover uma companhia.
        m.getAvoidedAirlines().clear();
        req.avoidedAirlines().forEach(m::evitarCompanhia);

        m.setPesoPreco(req.pesoPreco());
        m.setPesoEscalas(req.pesoEscalas());
        m.setPesoDuracao(req.pesoDuracao());
        m.setPesoHorario(req.pesoHorario());

        if (todosOsPesosZerados(req)) {
            // O banco tambem barra, mas aqui a mensagem explica o problema em
            // vez de devolver violacao de constraint.
            throw new ConflitoException(
                    "pelo menos um peso do score precisa ser maior que zero");
        }
    }

    private boolean todosOsPesosZerados(MonitorRequest req) {
        return zero(req.pesoPreco()) && zero(req.pesoEscalas())
                && zero(req.pesoDuracao()) && zero(req.pesoHorario());
    }

    /** Nulo nao conta: significa "usa o global", que e positivo. */
    private boolean zero(Short peso) {
        return peso != null && peso == 0;
    }

    private Set<Recipient> resolverDestinatarios(Set<Long> ids) {
        Set<Recipient> resolvidos = new LinkedHashSet<>();
        for (Long id : ids) {
            resolvidos.add(destinatarios.findById(id)
                    .orElseThrow(() -> new NotFoundException("Destinatario", id)));
        }
        return resolvidos;
    }
}
