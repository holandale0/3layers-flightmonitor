package com.flightmonitor.app

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Registra o aparelho no core SEM passar pelo painel.
 *
 * So e usado quando o Firebase troca o token sozinho (`onNewToken`), com o app
 * possivelmente fechado: nao ha WebView para fazer a chamada. No caminho normal
 * — a pessoa tocando em "Receber neste aparelho" — quem registra e o painel,
 * pela mesma API.
 */
object RegistroDoAparelho {

    private const val TAG = "RegistroDoAparelho"

    /** "Samsung SM-S911B": o que aparece na lista de aparelhos do destinatario. */
    fun nome(): String {
        val fabricante = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val modelo = Build.MODEL
        val nome = if (modelo.startsWith(fabricante, ignoreCase = true)) modelo else "$fabricante $modelo"
        return nome.take(120)
    }

    /** Bloqueante: chamar fora da thread principal. */
    fun registrar(ctx: Context, destinatario: Long, token: String): Boolean {
        val credenciais = Preferencias.credenciais(ctx)
        val url = URL("${BuildConfig.PAINEL_URL}/api/recipients/$destinatario/push-devices")
        val corpo = JSONObject().put("token", token).put("name", nome()).toString()

        val conexao = url.openConnection() as HttpURLConnection
        return try {
            conexao.requestMethod = "POST"
            conexao.connectTimeout = 15_000
            conexao.readTimeout = 15_000
            conexao.doOutput = true
            conexao.setRequestProperty("Content-Type", "application/json")
            if (credenciais != null) {
                val par = "${credenciais.usuario}:${credenciais.senha}".toByteArray()
                conexao.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(par, Base64.NO_WRAP))
            }
            conexao.outputStream.use { it.write(corpo.toByteArray()) }

            val status = conexao.responseCode
            if (status !in 200..299) Log.w(TAG, "core recusou o registro do aparelho: HTTP $status")
            status in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "nao foi possivel registrar o aparelho: ${e.message}")
            false
        } finally {
            conexao.disconnect()
        }
    }
}
