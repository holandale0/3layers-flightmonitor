package com.flightmonitor.core.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.flightmonitor.core.search.control.client.WorkerUnavailableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz excecoes para respostas RFC 7807 (ProblemDetail).
 *
 * <p>Sem isso, uma violacao de CHECK do banco chegaria ao cliente como um 500
 * com stack trace do PostgreSQL — vazando detalhe interno e sem dizer o que
 * corrigir.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail naoEncontrado(NotFoundException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        p.setTitle("Recurso nao encontrado");
        p.setType(URI.create("urn:flightmonitor:nao-encontrado"));
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail payloadInvalido(MethodArgumentNotValidException e) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError erro : e.getBindingResult().getFieldErrors()) {
            erros.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(erro -> erros.putIfAbsent(erro.getObjectName(), erro.getDefaultMessage()));

        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos estao invalidos");
        p.setTitle("Payload invalido");
        p.setType(URI.create("urn:flightmonitor:payload-invalido"));
        p.setProperty("errors", erros);
        return p;
    }

    /**
     * O worker esta fora do ar — etapa E3.1.
     *
     * <p>503 e nao 500: o defeito nao e nosso, e a distincao importa para quem
     * le. Um 500 manda o usuario procurar problema no pedido dele; um 503 diz
     * "tente de novo em instantes", que e a verdade.
     *
     * <p>Antes desta etapa a excecao subia sem tratamento e virava pagina de
     * erro do servlet, com pilha de excecao no corpo.
     */
    @ExceptionHandler(WorkerUnavailableException.class)
    public ProblemDetail workerIndisponivel(WorkerUnavailableException e) {
        log.warn("worker indisponivel: {}", e.getMessage());

        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "o servico de busca esta indisponivel no momento");
        p.setTitle("Servico indisponivel");
        // A mensagem tecnica vai num campo separado: util para depurar, sem
        // poluir o texto que a interface mostra.
        p.setProperty("causa", e.getMessage());
        return p;
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Conflito");
        p.setType(URI.create("urn:flightmonitor:conflito"));
        return p;
    }

    /**
     * Ultima linha de defesa: se uma regra passou pela validacao da API mas
     * bateu num CHECK do banco, devolve 409 em vez de 500 e registra o caso —
     * e sinal de que falta uma validacao na camada de aplicacao.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail violacaoDeIntegridade(DataIntegrityViolationException e) {
        log.warn("Restricao do banco violada — considere validar isso na API: {}",
                e.getMostSpecificCause().getMessage());

        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A operacao viola uma restricao de integridade dos dados");
        p.setTitle("Conflito de integridade");
        p.setType(URI.create("urn:flightmonitor:integridade"));
        return p;
    }
}
