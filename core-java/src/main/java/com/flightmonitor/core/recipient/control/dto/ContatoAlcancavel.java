package com.flightmonitor.core.recipient.control.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Exige que o destinatario tenha ao menos uma forma de ser alcancado.
 *
 * <p>Anotacao de classe, e nao de campo, porque a regra e "um OU outro" — algo
 * que {@code @NotBlank} em cada campo nao consegue expressar. Mesmo padrao do
 * {@code JanelasCoerentes}, no monitor.
 *
 * <p>O CHECK {@code recipient_tem_algum_contato} diz o mesmo no banco. A
 * duplicacao e proposital ([D-016]): aqui a mensagem aponta o campo e cabe no
 * formulario; la e a ultima linha, que protege contra carga manual e bug nosso.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ContatoAlcancavelValidator.class)
public @interface ContatoAlcancavel {

    String message() default "informe telefone ou e-mail";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
