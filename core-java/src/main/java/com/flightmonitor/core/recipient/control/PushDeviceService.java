package com.flightmonitor.core.recipient.control;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.recipient.control.dto.PushDeviceRequest;
import com.flightmonitor.core.recipient.control.dto.PushDeviceResponse;
import com.flightmonitor.core.recipient.entity.PushDevice;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.recipient.entity.RecipientRepository;

/** Os aparelhos que recebem push — D-113. */
@Service
@Transactional(readOnly = true)
public class PushDeviceService {

    private static final Logger log = LoggerFactory.getLogger(PushDeviceService.class);

    private final PushDeviceRepository aparelhos;
    private final RecipientRepository destinatarios;

    public PushDeviceService(PushDeviceRepository aparelhos, RecipientRepository destinatarios) {
        this.aparelhos = aparelhos;
        this.destinatarios = destinatarios;
    }

    public List<PushDeviceResponse> listar(Long recipientId) {
        buscarDestinatario(recipientId);
        return aparelhos.findByRecipientIdOrderByCreatedAtAscIdAsc(recipientId).stream()
                .map(PushDeviceResponse::de)
                .toList();
    }

    /**
     * Registra o aparelho para o destinatario — ou atualiza, se o token ja existe.
     *
     * <p><b>Registrar de novo nao e erro.</b> O aplicativo se reapresenta a cada
     * vez que alguem toca em "Receber neste aparelho", e pode estar trocando de
     * destinatario: o celular que era do Leonardo passa a ser da Maria. Um 409
     * aqui obrigaria a pessoa a descobrir e apagar o registro antigo, coisa que
     * ela nem sabe que existe.
     *
     * <p>Tambem reativa: um aparelho desligado porque o Firebase recusou o
     * token volta a valer quando o app se apresenta com um token que funciona.
     */
    @Transactional
    public PushDeviceResponse registrar(Long recipientId, PushDeviceRequest req) {
        Recipient dono = buscarDestinatario(recipientId);

        PushDevice aparelho = aparelhos.findByToken(req.token())
                .map(existente -> {
                    if (!existente.getRecipient().getId().equals(recipientId)) {
                        log.info("aparelho {} passou do destinatario {} para o {}",
                                existente.getId(), existente.getRecipient().getId(), recipientId);
                    }
                    existente.reativar(dono, req.name());
                    return existente;
                })
                .orElseGet(() -> new PushDevice(dono, req.token(), req.name()));

        return PushDeviceResponse.de(aparelhos.saveAndFlush(aparelho));
    }

    @Transactional
    public void excluir(Long id) {
        PushDevice aparelho = aparelhos.findById(id)
                .orElseThrow(() -> new NotFoundException("Aparelho", id));
        aparelhos.delete(aparelho);
    }

    private Recipient buscarDestinatario(Long id) {
        return destinatarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Destinatario", id));
    }
}
