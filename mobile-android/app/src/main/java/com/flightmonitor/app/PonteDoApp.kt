package com.flightmonitor.app

import android.webkit.JavascriptInterface

/**
 * O que o painel enxerga como `window.FlightMonitorApp`.
 *
 * E o unico ponto de contato entre o Vue e o Android, e de proposito pequeno:
 * o painel continua sendo o mesmo do navegador, e so pergunta ao app o que o
 * navegador nao sabe responder — o token de push e o nome do aparelho. O
 * registro em si o painel faz pela API, como faz todo o resto.
 *
 * Os metodos rodam numa thread do WebView, e nao na principal.
 */
class PonteDoApp(private val activity: MainActivity) {

    @JavascriptInterface
    fun versao(): String = BuildConfig.VERSION_NAME

    /** Falso quando o APK foi gerado sem google-services.json. */
    @JavascriptInterface
    fun pushDisponivel(): Boolean = activity.firebaseDisponivel()

    @JavascriptInterface
    fun nomeDoAparelho(): String = RegistroDoAparelho.nome()

    /**
     * Pede permissao de notificacao e o token do Firebase. A resposta chega
     * depois, como evento `flightmonitor:push` no `window`, com
     * `{ token }` ou `{ erro }` — pedir permissao abre um dialogo do sistema, e
     * nao ha como devolver isso de forma sincrona.
     */
    @JavascriptInterface
    fun ativarPush(destinatario: Double) {
        // Numero do JavaScript chega como Double.
        activity.runOnUiThread { activity.ativarPush(destinatario.toLong()) }
    }
}
