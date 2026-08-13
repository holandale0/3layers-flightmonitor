package com.flightmonitor.core.monitor.control.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Valida as regras que dependem de mais de um campo: fim da janela depois do
 * inicio, janela de volta preenchida aos pares e permanencia minima menor ou
 * igual a maxima.
 *
 * <p>Sao as mesmas regras dos CHECK do banco. Duplicar aqui e proposital: o
 * banco garante a integridade, mas so a API consegue dizer <em>qual campo</em>
 * esta errado e por que.
 */
@Documented
@Constraint(validatedBy = JanelasCoerentesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JanelasCoerentes {

    String message() default "janelas de data incoerentes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
