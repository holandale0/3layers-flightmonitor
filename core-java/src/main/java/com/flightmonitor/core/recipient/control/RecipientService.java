package com.flightmonitor.core.recipient.control;

import com.flightmonitor.core.recipient.entity.PushDeviceRepository;
import com.flightmonitor.core.recipient.entity.RecipientRepository;
import com.flightmonitor.core.recipient.entity.Recipient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.common.ConflitoException;
import com.flightmonitor.core.common.NotFoundException;
import com.flightmonitor.core.recipient.control.dto.PushDeviceResponse;
import com.flightmonitor.core.recipient.control.dto.RecipientRequest;
import com.flightmonitor.core.recipient.control.dto.RecipientResponse;

@Service
@Transactional(readOnly = true)
public class RecipientService {

    private final RecipientRepository destinatarios;
    private final PushDeviceRepository aparelhos;

    public RecipientService(RecipientRepository destinatarios, PushDeviceRepository aparelhos) {
        this.destinatarios = destinatarios;
        this.aparelhos = aparelhos;
    }

    public List<RecipientResponse> listar(Boolean apenasAtivos) {
        List<Recipient> encontrados = Boolean.TRUE.equals(apenasAtivos)
                ? destinatarios.findByActiveTrue()
                : destinatarios.findAll();

        // Uma consulta para todos os aparelhos, e nao uma por destinatario: a
        // tela lista todo mundo de uma vez.
        Map<Long, List<PushDeviceResponse>> porDono = aparelhos.findAll(Sort.by("createdAt", "id"))
                .stream()
                .map(PushDeviceResponse::de)
                .collect(Collectors.groupingBy(PushDeviceResponse::recipientId));

        return encontrados.stream()
                .map(r -> RecipientResponse.de(r, porDono.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    public RecipientResponse buscar(Long id) {
        return resposta(buscarEntidade(id));
    }

    private RecipientResponse resposta(Recipient r) {
        List<PushDeviceResponse> deste = r.getId() == null
                ? List.of()
                : aparelhos.findByRecipientIdOrderByCreatedAtAscIdAsc(r.getId()).stream()
                        .map(PushDeviceResponse::de)
                        .toList();
        return RecipientResponse.de(r, deste);
    }

    @Transactional
    public RecipientResponse criar(RecipientRequest req) {
        recusarContatoDuplicado(req, null);

        Recipient r = new Recipient(req.name(), req.phoneE164(), req.email());
        r.setActive(req.active());
        return resposta(destinatarios.saveAndFlush(r));
    }

    @Transactional
    public RecipientResponse atualizar(Long id, RecipientRequest req) {
        Recipient r = buscarEntidade(id);
        recusarContatoDuplicado(req, id);

        r.setName(req.name());
        r.setPhoneE164(req.phoneE164());
        r.setEmail(req.email());
        r.setActive(req.active());
        return resposta(destinatarios.saveAndFlush(r));
    }

    /**
     * Recusa telefone ou e-mail que ja pertencam a outro destinatario.
     *
     * <p>Os dois contatos tem indice unico no banco, e sem esta checagem o
     * conflito chegaria ao usuario como violacao de constraint — mensagem de
     * banco de dados numa tela de formulario.
     *
     * <p>Os campos sao opcionais desde a E4.6, entao o nulo e ignorado: varios
     * destinatarios sem e-mail nao disputam unicidade entre si.
     *
     * @param idAtual nulo na criacao; na atualizacao, o proprio registro nao
     *        conta como conflito consigo mesmo
     */
    private void recusarContatoDuplicado(RecipientRequest req, Long idAtual) {
        if (req.phoneE164() != null) {
            destinatarios.findByPhoneE164(req.phoneE164())
                    .filter(outro -> !outro.getId().equals(idAtual))
                    .ifPresent(outro -> {
                        throw new ConflitoException("o telefone " + req.phoneE164()
                                + " ja pertence a outro destinatario");
                    });
        }
        if (req.email() != null) {
            destinatarios.findByEmail(req.email())
                    .filter(outro -> !outro.getId().equals(idAtual))
                    .ifPresent(outro -> {
                        throw new ConflitoException("o e-mail " + req.email()
                                + " ja pertence a outro destinatario");
                    });
        }
    }

    @Transactional
    public void excluir(Long id) {
        Recipient r = buscarEntidade(id);
        // Os vinculos em monitor_recipient somem em cascata; os monitores
        // permanecem. Alertas ja enviados ficam com recipient_id nulo, mantendo
        // o historico de entregas.
        destinatarios.delete(r);
    }

    private Recipient buscarEntidade(Long id) {
        return destinatarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Destinatario", id));
    }
}
