package com.flightmonitor.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.flightmonitor.app.databinding.ActivityMainBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

/**
 * O painel web inteiro, dentro de um WebView — no molde do app do tour-guide.
 *
 * Tudo que o painel faz no navegador, faz aqui, porque e o MESMO painel: nao
 * ha tela nativa para manter em dia com a web. O app so acrescenta o que o
 * navegador nao tem — push, e a senha lembrada.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val painel: Uri = Uri.parse(BuildConfig.PAINEL_URL)

    /**
     * A senha salva ja foi tentada nesta carga? Se o servidor pedir de novo, a
     * salva esta errada (foi trocada no prod) e a pessoa precisa digitar —
     * reenviar a mesma prenderia o app num laco de 401.
     */
    private var credencialSalvaJaTentada = false

    /** Destinatario a registrar, guardado enquanto o dialogo de permissao esta aberto. */
    private var pushPendente: Long? = null

    private val pedirPermissao = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedida ->
        val destinatario = pushPendente ?: return@registerForActivityResult
        pushPendente = null
        if (concedida) {
            obterToken(destinatario)
        } else {
            responderPush(erro = getString(R.string.push_permissao_negada))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Notificacoes.garantirCanal(this)
        setupWebView()
        setupBackNavigation()
        binding.retryButton.setOnClickListener { carregar(null) }

        if (savedInstanceState == null) {
            carregar(intent.getStringExtra(Notificacoes.EXTRA_CAMINHO))
        }
    }

    /** Toque numa notificacao com o app ja aberto (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notificacoes.EXTRA_CAMINHO)?.let { carregar(it) }
    }

    /**
     * Abre o painel, opcionalmente numa tela.
     *
     * O caminho vem do push, e por isso e conferido: so caminho relativo do
     * proprio painel. Uma URL inteira — ou `//outro.host` — faria uma mensagem
     * forjada abrir qualquer site dentro do app, com a ponte JavaScript ligada.
     */
    private fun carregar(caminho: String?) {
        mostrarErro(false)
        val seguro = caminho?.takeIf { it.startsWith("/") && !it.startsWith("//") } ?: "/"
        binding.webView.loadUrl(BuildConfig.PAINEL_URL + seguro)
    }

    private fun daAplicacao(url: Uri): Boolean =
        url.scheme == painel.scheme && url.host == painel.host && url.port == painel.port

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(false)
            builtInZoomControls  = false
            displayZoomControls  = false
            cacheMode            = WebSettings.LOAD_DEFAULT

            // A origem e HTTPS: nada de conteudo misto, e o WebView nao le o
            // sistema de arquivos do aparelho.
            mixedContentMode     = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess      = false
            allowContentAccess   = false
        }

        binding.webView.addJavascriptInterface(PonteDoApp(this), "FlightMonitorApp")

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                credencialSalvaJaTentada = false
            }

            /**
             * So o painel fica no app. O resto — o link da companhia aerea, o
             * Google Flights — abre no navegador, onde a pessoa tem as contas
             * dela e a ponte JavaScript nao existe.
             */
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                if (daAplicacao(url)) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            }

            /** A senha do painel: o nginx do prod pede Basic Auth para tudo. */
            override fun onReceivedHttpAuthRequest(
                view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?,
            ) {
                val salva = Preferencias.credenciais(this@MainActivity)
                if (salva != null && !credencialSalvaJaTentada) {
                    credencialSalvaJaTentada = true
                    handler.proceed(salva.usuario, salva.senha)
                    return
                }
                pedirSenha(handler, salvaRecusada = salva != null)
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    binding.progressBar.visibility = View.GONE
                    mostrarErro(true)
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
    }

    private fun pedirSenha(handler: HttpAuthHandler, salvaRecusada: Boolean) {
        val usuario = EditText(this).apply {
            hint = getString(R.string.login_usuario)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Preferencias.credenciais(this@MainActivity)?.usuario ?: "admin")
        }
        val senha = EditText(this).apply {
            hint = getString(R.string.login_senha)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val campos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val m = (20 * resources.displayMetrics.density).toInt()
            setPadding(m, m / 2, m, 0)
            addView(usuario)
            addView(senha)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.login_titulo)
            .setMessage(if (salvaRecusada) R.string.login_recusado else R.string.login_mensagem)
            .setView(campos)
            .setCancelable(false)
            .setPositiveButton(R.string.login_entrar) { _, _ ->
                val c = Preferencias.Credenciais(usuario.text.toString().trim(), senha.text.toString())
                // Salva antes de saber se esta certa: se estiver errada, o
                // servidor pede de novo e este mesmo dialogo volta, avisando.
                Preferencias.salvarCredenciais(this, c)
                credencialSalvaJaTentada = true
                handler.proceed(c.usuario, c.senha)
            }
            .setNegativeButton(R.string.login_cancelar) { _, _ ->
                handler.cancel()
                mostrarErro(true)
            }
            .show()
    }

    // ------------------------------------------------------------------ push

    fun firebaseDisponivel(): Boolean = FirebaseApp.getApps(this).isNotEmpty()

    /** Chamado pela ponte, ja na thread principal. */
    fun ativarPush(destinatario: Long) {
        // A ponte responde so ao painel. Se por algum caminho o WebView estiver
        // noutra pagina, ela nao entrega o token.
        val atual = binding.webView.url?.let(Uri::parse)
        if (atual == null || !daAplicacao(atual)) return

        if (!firebaseDisponivel()) {
            responderPush(erro = getString(R.string.push_sem_firebase))
            return
        }
        if (!Notificacoes.podeNotificar(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pushPendente = destinatario
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        obterToken(destinatario)
    }

    private fun obterToken(destinatario: Long) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tarefa ->
            if (tarefa.isSuccessful && tarefa.result != null) {
                // Guardado para o onNewToken: se o Firebase trocar o token, o
                // app sabe a quem reapresentar o aparelho.
                Preferencias.salvarDestinatario(this, destinatario)
                responderPush(token = tarefa.result)
            } else {
                responderPush(erro = getString(R.string.push_sem_token) +
                    (tarefa.exception?.message?.let { " ($it)" } ?: ""))
            }
        }
    }

    private fun responderPush(token: String? = null, erro: String? = null) {
        val detalhe = JSONObject().apply {
            token?.let { put("token", it) }
            erro?.let { put("erro", it) }
        }
        binding.webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('flightmonitor:push', { detail: $detalhe }))",
            null,
        )
    }

    // ------------------------------------------------------------------ resto

    private fun mostrarErro(visivel: Boolean) {
        binding.errorGroup.visibility = if (visivel) View.VISIBLE else View.GONE
        binding.webView.visibility    = if (visivel) View.GONE else View.VISIBLE
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
    }
}
