package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertRepository;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.entity.Alert;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrega os alertas pendentes.
 *
 * <p>Separado da decisao de alertar ([D-043]): permite reenviar uma entrega que
 * falhou sem re-decidir se valia a pena alertar, e testar a regra sem mandar
 * mensagem nenhuma.
 *
 * <p><b>Reivindica antes de entregar</b>, com {@code SKIP LOCKED}, pela mesma
 * razao do scheduler de varredura — mas aqui a consequencia de errar e pior:
 * entregar duas vezes significa mensagem repetida no WhatsApp do usuario.
 *
 * <p>O envio acontece <b>fora</b> de transacao. Uma chamada a Meta pode levar
 * segundos, e prender conexao do pool durante isso esgotaria o pool ([D-034]).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AlertRepository alertas;
    private final TransactionTemplate tx;
    private final NotificationProperties props;
    private final Map<AlertChannel, NotificationChannel> canais = new EnumMap<>(AlertChannel.class);

    /**
     * Impede dois despachos simultaneos nesta instancia.
     *
     * <p>Cobre o caso real de hoje: entrega imediata coincidindo com a varredura
     * agendada. <b>Nao cobre</b> duas instancias da aplicacao — para isso seria
     * preciso um estado {@code SENDING} persistido, com recuperacao de alertas
     * travados. Fica para a Fase 4, quando o deploy em container tornar
     * multi-instancia real.
     */
    private final java.util.concurrent.locks.ReentrantLock despachoEmAndamento =
            new java.util.concurrent.locks.ReentrantLock();

    public NotificationService(
            AlertRepository alertas,
            TransactionTemplate tx,
            NotificationProperties props,
            List<NotificationChannel> canaisDisponiveis) {
        this.alertas = alertas;
        this.tx = tx;
        this.props = props;

        for (NotificationChannel canal : canaisDisponiveis) {
            this.canais.put(canal.canal(), canal);
        }
        log.info("canais de notificacao disponiveis: {}; ativo: {}",
                this.canais.keySet(), props.canal());
    }

    /**
     * Entrega os alertas pendentes e devolve quantos foram entregues.
     *
     * <p>Chamado logo apos criar alertas (entrega imediata) e por varredura
     * agendada (recuperacao). As duas rotas usam o mesmo metodo — a licao do
     * BUG-005 foi nao ter dois caminhos para a mesma coisa.
     */
    public DispatchResult despacharPendentes() {
        // A entrega imediata e a varredura agendada podem coincidir. O SKIP
        // LOCKED da reivindicacao nao basta sozinho: a trava e liberada no
        // commit, e o envio acontece depois disso. Sem este guarda, dois
        // despachos simultaneos poderiam entregar A MESMA mensagem — que no
        // WhatsApp do usuario aparece como notificacao repetida.
        //
        // tryLock e nao lock: se ja ha um despacho rodando, nao ha por que
        // esperar. Os alertas que sobrarem serao pegos na proxima varredura.
        if (!despachoEmAndamento.tryLock()) {
            log.debug("despacho ja em andamento; deixando para a proxima varredura");
            return DispatchResult.vazio();
        }

        try {
            return despachar();
        } finally {
            despachoEmAndamento.unlock();
        }
    }

    private DispatchResult despachar() {
        List<Long> ids = reivindicar();
        if (ids.isEmpty()) {
            return DispatchResult.vazio();
        }

        int entregues = 0;
        int falhas = 0;
        int retentar = 0;

        for (Long id : ids) {
            switch (entregar(id)) {
                // ENTREGUE aqui significa "despachado sem erro". Em canal
                // assincrono a entrega de fato ainda esta em aberto.
                case ENTREGUE -> entregues++;
                case FALHOU -> falhas++;
                case VAI_RETENTAR -> retentar++;
            }
        }

        DispatchResult resultado = new DispatchResult(ids.size(), entregues, falhas, retentar);
        log.info("despacho concluido: {}", resultado);
        return resultado;
    }

    private enum Desfecho { ENTREGUE, FALHOU, VAI_RETENTAR }

    private Desfecho entregar(Long alertaId) {
        // findByIdParaEntrega e nao findById: o envio acontece fora de transacao,
        // entao a entidade chega desanexada ao canal. Sem o join fetch, ler o
        // telefone do destinatario estouraria LazyInitializationException — e o
        // alerta ficaria retentando para sempre sem nunca sair.
        Alert alerta = tx.execute(s -> alertas.findByIdParaEntrega(alertaId).orElse(null));
        if (alerta == null) {
            return Desfecho.FALHOU;
        }

        NotificationChannel canal = canais.get(alerta.getChannel());
        if (canal == null) {
            // Canal configurado no alerta nao tem implementacao registrada.
            // E erro de configuracao, nao de rede: retentar nao resolveria.
            return finalizar(alertaId, DeliveryResult.falhaPermanente(
                    "nenhuma implementacao para o canal " + alerta.getChannel()), false);
        }

        boolean assincrono = canal.confirmacaoAssincrona();

        DeliveryResult resultado;
        try {
            // Fora de transacao, de proposito.
            resultado = canal.enviar(alerta);
        } catch (RuntimeException e) {
            // O contrato pede que o canal nao lance, mas nao da para confiar:
            // um canal mal comportado nao pode derrubar o despacho inteiro.
            log.error("canal {} lancou excecao inesperada", alerta.getChannel(), e);
            resultado = DeliveryResult.falhaTransitoria(
                    "excecao nao tratada: " + e.getClass().getSimpleName());
        }

        return finalizar(alertaId, resultado, assincrono);
    }

    private Desfecho finalizar(Long alertaId, DeliveryResult resultado, boolean assincrono) {
        return tx.execute(s -> {
            Alert alerta = alertas.findById(alertaId).orElseThrow();

            if (resultado.sucesso()) {
                // A distincao que o BUG-007 cobrou: em canal assincrono, o
                // sucesso aqui significa "o provedor aceitou", e nada mais. Só
                // o webhook transforma isso em entrega.
                if (assincrono) {
                    alerta.marcarAceito(resultado.providerMessageId());
                } else {
                    alerta.marcarEnviado(resultado.providerMessageId());
                }
                alertas.saveAndFlush(alerta);
                return Desfecho.ENTREGUE;
            }

            if (!resultado.transitorio()) {
                // Numero invalido, mensagem recusada: retentar so adia o
                // diagnostico e gasta cota.
                alerta.marcarFalha(resultado.erro());
                alertas.saveAndFlush(alerta);
                log.warn("alerta {} falhou em definitivo: {}", alertaId, resultado.erro());
                return Desfecho.FALHOU;
            }

            boolean vaiRetentar = alerta.registrarTentativaFalha(
                    resultado.erro(), props.maxTentativas());
            alertas.saveAndFlush(alerta);

            if (vaiRetentar) {
                log.warn("alerta {} falhou (tentativa {}/{}): {}",
                        alertaId, alerta.getAttempts(), props.maxTentativas(), resultado.erro());
                return Desfecho.VAI_RETENTAR;
            }

            log.error("alerta {} desistido apos {} tentativas: {}",
                    alertaId, alerta.getAttempts(), resultado.erro());
            return Desfecho.FALHOU;
        });
    }

    private List<Long> reivindicar() {
        return tx.execute(s -> alertas
                .reivindicarPendentes(PageRequest.of(0, props.lote()))
                .stream()
                .map(Alert::getId)
                .toList());
    }

    /** Canal configurado como ativo, gravado em cada alerta criado. */
    public AlertChannel canalAtivo() {
        return props.canal();
    }
}
