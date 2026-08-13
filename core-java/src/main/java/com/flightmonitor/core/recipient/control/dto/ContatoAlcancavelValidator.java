package com.flightmonitor.core.recipient.control.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ContatoAlcancavelValidator
        implements ConstraintValidator<ContatoAlcancavel, RecipientRequest> {

    @Override
    public boolean isValid(RecipientRequest req, ConstraintValidatorContext ctx) {
        if (req == null) {
            return true;
        }
        // O construtor compacto do record ja transformou vazio em nulo, entao
        // basta testar nulidade aqui.
        if (req.phoneE164() != null || req.email() != null) {
            return true;
        }

        ctx.disableDefaultConstraintViolation();
        // Ancorado no telefone porque ele e o primeiro campo do formulario: a
        // marcacao aparece onde o olho ja esta. A mensagem cita os dois.
        ctx.buildConstraintViolationWithTemplate(
                        "informe ao menos um contato: telefone ou e-mail")
                .addPropertyNode("phoneE164")
                .addConstraintViolation();
        return false;
    }
}
