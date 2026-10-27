package com.flightmonitor.core.alert.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;
import com.flightmonitor.core.recipient.entity.Recipient;

class AlcanceDosCanaisTest {

    private final PushDeviceRepository semAparelhos = Mockito.mock(PushDeviceRepository.class);
    private final AlcanceDosCanais alcance = new AlcanceDosCanais(semAparelhos);

    @Test
    @DisplayName("mantem a ordem configurada e tira o canal sem contato")
    void filtraPeloContato() {
        Recipient soEmail = new Recipient("Maria", null, "maria@exemplo.com");

        assertThat(alcance.canaisPara(soEmail,
                List.of(AlertChannel.WHATSAPP, AlertChannel.EMAIL, AlertChannel.LOG)))
                .containsExactly(AlertChannel.EMAIL, AlertChannel.LOG);
    }

    @Test
    @DisplayName("se nenhum canal alcanca, devolve todos — para a falha ficar registrada")
    void ninguemAlcancaViraTodos() {
        // Uma lista vazia faria o alerta nao existir: a pessoa perderia a
        // passagem sem rastro. Com os alertas criados, o motivo vai para
        // error_message.
        Recipient soTelefone = new Recipient("Joao", "+5511900001111");

        assertThat(alcance.canaisPara(soTelefone, List.of(AlertChannel.EMAIL, AlertChannel.PUSH)))
                .containsExactly(AlertChannel.EMAIL, AlertChannel.PUSH);
    }

    @Test
    @DisplayName("contato em branco nao alcanca ninguem")
    void contatoEmBranco() {
        Recipient r = new Recipient("Ana", "+5511900002222", "  ");

        assertThat(alcance.alcanca(AlertChannel.EMAIL, r)).isFalse();
        assertThat(alcance.alcanca(AlertChannel.WHATSAPP, r)).isTrue();
    }

    @Test
    @DisplayName("destinatario ainda nao salvo nao tem aparelho")
    void semIdSemPush() {
        // E nem consulta o banco: sem id nao ha aparelho que aponte para ele.
        assertThat(alcance.alcanca(AlertChannel.PUSH, new Recipient("Novo", "+5511900003333")))
                .isFalse();
        Mockito.verifyNoInteractions(semAparelhos);
    }
}
