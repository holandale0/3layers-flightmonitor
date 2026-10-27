package com.flightmonitor.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AlertasMessagingService : FirebaseMessagingService() {

    /**
     * O Firebase trocou o token — por limpeza de dados, restauracao do
     * aparelho, ou por rotina dele. O antigo para de funcionar, e sem
     * reapresentar o novo o celular deixaria de receber alertas sem nenhum
     * aviso: o core so descobriria no proximo envio, ao receber UNREGISTERED.
     */
    override fun onNewToken(token: String) {
        val destinatario = Preferencias.destinatario(this) ?: return
        // O servico ja roda fora da thread principal.
        RegistroDoAparelho.registrar(this, destinatario, token)
    }

    /** So chega aqui com o app ABERTO; fechado, o Firebase mostra sozinho. */
    override fun onMessageReceived(mensagem: RemoteMessage) {
        val titulo = mensagem.notification?.title ?: getString(R.string.app_name)
        val corpo = mensagem.notification?.body.orEmpty()
        Notificacoes.mostrar(this, titulo, corpo, mensagem.data[Notificacoes.EXTRA_CAMINHO])
    }
}
