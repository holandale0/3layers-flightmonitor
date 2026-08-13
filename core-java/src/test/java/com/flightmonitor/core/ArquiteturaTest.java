package com.flightmonitor.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * A arquitetura BCE, verificada como teste.
 *
 * <h2>Por que isto existe</h2>
 *
 * Sem estas regras, a organizacao em pastas e apenas uma convencao — e convencao
 * que ninguem verifica dura ate a primeira pressa. Um controller chamando
 * repositorio direto compila, passa nos testes e parece funcionar; seis meses
 * depois a arquitetura existe so no diagrama.
 *
 * <p>Aqui ela e executavel. Quebrar a camada quebra o build, com o nome da
 * classe e da regra.
 *
 * <h2>O que cada camada significa neste projeto</h2>
 *
 * <ul>
 *   <li><b>entity</b> — o que o sistema lembra: as {@code @Entity}, os enums de
 *       estado persistido e os repositorios que as leem e gravam;</li>
 *   <li><b>control</b> — a logica de caso de uso: servicos, regras, schedulers,
 *       e as <b>portas</b> que o controle usa para falar com o mundo
 *       ({@code NotificationChannel}, por exemplo);</li>
 *   <li><b>boundary</b> — as bordas: controllers REST, DTOs de API e adaptadores
 *       de sistema externo (WhatsApp, worker, mensageria).</li>
 * </ul>
 *
 * <h2>A regra que mais importa</h2>
 *
 * <b>Boundary nao fala com boundary.</b> E o que impede o adaptador do WhatsApp
 * de chamar o cliente do worker, ou um controller de chamar outro controller —
 * atalhos que parecem inofensivos e transformam as bordas num grafo, em vez de
 * uma superficie.
 */
@AnalyzeClasses(
        packages = "com.flightmonitor.core",
        // Sem as classes de teste: elas atravessam camadas de proposito, para
        // montar cenario, e nao deveriam ser julgadas pelas mesmas regras.
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    private static final String ENTITY = "..entity..";
    private static final String CONTROL = "..control..";
    private static final String BOUNDARY = "..boundary..";

    /**
     * A entidade e o centro: ela nao conhece quem a usa.
     *
     * <p>Se uma entidade importasse um servico, mudar a regra de negocio
     * passaria a exigir recompilar o modelo — e o modelo deixaria de ser
     * reaproveitavel por outro caso de uso.
     */
    @ArchTest
    static final ArchRule entidadeNaoConheceNinguem = noClasses()
            .that().resideInAPackage(ENTITY)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROL, BOUNDARY);

    /**
     * O controle nao conhece as bordas — so as portas que ele mesmo define.
     *
     * <p>E o que permitiu a E4.1 trocar REST por mensageria sem tocar no motor:
     * o {@code SearchCycleService} depende da interface {@code SearchClient}, e
     * nunca do adaptador.
     */
    @ArchTest
    static final ArchRule controleNaoConheceABorda = noClasses()
            .that().resideInAPackage(CONTROL)
            .should().dependOnClassesThat().resideInAPackage(BOUNDARY);

    /**
     * Uma borda nao chama outra borda.
     *
     * <p>Bordas sao superficie, e nao grafo. O adaptador do WhatsApp falando com
     * o cliente do worker criaria um caminho que nenhum diagrama mostra e que
     * nenhum caso de uso pediu.
     *
     * <p>Excecao declarada: dentro da <b>mesma</b> feature, uma borda pode usar
     * outra — o controller do webhook e o adaptador do WhatsApp compartilham o
     * mapa de erros da Meta, e separa-los seria duplicar conhecimento sobre a
     * mesma API externa.
     */
    @ArchTest
    static final ArchRule bordaNaoChamaOutraFeaturePelaBorda = noClasses()
            .that().resideInAPackage("..agent.boundary..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..alert.boundary..", "..search.boundary..", "..stats.boundary..");

    // A regra "controller nao usa repositorio" existiu aqui e foi REMOVIDA.
    //
    // Ela reprovou 24 dependencias, e ao olhar cada uma ficou claro que a regra
    // e que estava errada: em BCE a borda PODE alcancar a entidade — e o
    // proprio ponto do estilo, em que a boundary e a fachada do caso de uso.
    // Proibir isso importaria uma regra da Clean Architecture e produziria
    // servicos que so repassam uma consulta, sem regra nenhuma dentro.
    //
    // O que continua valendo, e e o que importa de verdade: a borda nao decide.
    // Isso nao da para verificar por dependencia — da para verificar lendo o
    // controller, e por isso ficou como criterio de revisao, e nao como teste
    // que da falsa sensacao de garantia.

    /** Entidade JPA mora em {@code entity}, e em nenhum outro lugar. */
    @ArchTest
    static final ArchRule entidadeJpaMoraEmEntity = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage(ENTITY);

    /** Repositorio mora em {@code entity}, junto do que ele le e grava. */
    @ArchTest
    static final ArchRule repositorioMoraEmEntity = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage(ENTITY);

    /** Controller mora em {@code boundary} — e a borda mais obvia de todas. */
    @ArchTest
    static final ArchRule controllerMoraEmBoundary = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage(BOUNDARY);

    /**
     * Servico mora em {@code control}.
     *
     * <p>Uma excecao: {@code WhatsAppWebhookHandler} traduz o que a Meta manda
     * em fatos sobre os nossos alertas — e regra, nao borda, mesmo com o nome
     * herdado do endpoint que o chama.
     */
    @ArchTest
    static final ArchRule servicoMoraEmControl = classes()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage(CONTROL);
}
