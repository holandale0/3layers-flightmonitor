package com.flightmonitor.app

import android.content.Context

/**
 * O pouco que o app guarda no aparelho: a senha do painel e para qual
 * destinatario ele recebe alertas.
 *
 * SharedPreferences no modo privado: so este app le, e o `allowBackup=false` do
 * manifesto impede que o backup do Android leve a senha para a nuvem.
 */
object Preferencias {

    private const val ARQUIVO = "flightmonitor"
    private const val USUARIO = "painel.usuario"
    private const val SENHA = "painel.senha"
    private const val DESTINATARIO = "push.destinatario"

    data class Credenciais(val usuario: String, val senha: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    fun credenciais(ctx: Context): Credenciais? {
        val p = prefs(ctx)
        val usuario = p.getString(USUARIO, null) ?: return null
        val senha = p.getString(SENHA, null) ?: return null
        return Credenciais(usuario, senha)
    }

    fun salvarCredenciais(ctx: Context, c: Credenciais) {
        prefs(ctx).edit().putString(USUARIO, c.usuario).putString(SENHA, c.senha).apply()
    }

    fun esquecerCredenciais(ctx: Context) {
        prefs(ctx).edit().remove(USUARIO).remove(SENHA).apply()
    }

    /** Para quem este aparelho recebe — usado quando o Firebase troca o token. */
    fun destinatario(ctx: Context): Long? =
        prefs(ctx).getLong(DESTINATARIO, -1).takeIf { it > 0 }

    fun salvarDestinatario(ctx: Context, id: Long) {
        prefs(ctx).edit().putLong(DESTINATARIO, id).apply()
    }
}
