package com.flightmonitor.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notificacoes {

    /** Nome do extra com a tela a abrir. O mesmo do campo `data.caminho` do core. */
    const val EXTRA_CAMINHO = "caminho"

    /**
     * O canal de notificacao do Android 8+.
     *
     * IMPORTANCE_HIGH: aparece no topo da tela e toca som. E o que um alerta de
     * passagem precisa — chega de madrugada, e quem le de manha ja perdeu.
     * Idempotente: recriar um canal que existe nao muda o que a pessoa
     * configurou nele.
     */
    fun garantirCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            ctx.getString(R.string.canal_alertas_id),
            ctx.getString(R.string.canal_alertas_nome),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = ctx.getString(R.string.canal_alertas_descricao) }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun podeNotificar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Mostra a notificacao quando o app esta ABERTO. Fechado, quem desenha e o
     * proprio Firebase, com o icone e o canal do manifesto — e o toque chega a
     * MainActivity com os mesmos extras, entao os dois caminhos abrem a mesma tela.
     */
    fun mostrar(ctx: Context, titulo: String, corpo: String, caminho: String?) {
        if (!podeNotificar(ctx)) return
        garantirCanal(ctx)

        val abrir = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CAMINHO, caminho)
        }
        // Um request code por notificacao: com o mesmo, o Android reaproveitaria
        // o PendingIntent da anterior, e tocar no segundo alerta abriria o
        // monitor do primeiro.
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pendente = PendingIntent.getActivity(
            ctx, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacao = NotificationCompat.Builder(ctx, ctx.getString(R.string.canal_alertas_id))
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(ContextCompat.getColor(ctx, R.color.primary))
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendente)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(id, notificacao)
        } catch (_: SecurityException) {
            // A permissao foi revogada entre a checagem e o envio.
        }
    }
}
