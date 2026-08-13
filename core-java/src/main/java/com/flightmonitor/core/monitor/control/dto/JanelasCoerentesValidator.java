package com.flightmonitor.core.monitor.control.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JanelasCoerentesValidator implements ConstraintValidator<JanelasCoerentes, MonitorRequest> {

    @Override
    public boolean isValid(MonitorRequest req, ConstraintValidatorContext ctx) {
        if (req == null) {
            return true;
        }

        boolean valido = true;
        ctx.disableDefaultConstraintViolation();

        if (req.departureWindowStart() != null
                && req.departureWindowEnd() != null
                && req.departureWindowEnd().isBefore(req.departureWindowStart())) {
            erro(ctx, "departureWindowEnd", "deve ser igual ou posterior a departureWindowStart");
            valido = false;
        }

        boolean temInicioVolta = req.returnWindowStart() != null;
        boolean temFimVolta = req.returnWindowEnd() != null;

        if (temInicioVolta != temFimVolta) {
            erro(ctx, temInicioVolta ? "returnWindowEnd" : "returnWindowStart",
                    "a janela de volta precisa ter inicio e fim, ou nenhum dos dois");
            valido = false;
        } else if (temInicioVolta && req.returnWindowEnd().isBefore(req.returnWindowStart())) {
            erro(ctx, "returnWindowEnd", "deve ser igual ou posterior a returnWindowStart");
            valido = false;
        }

        if (temInicioVolta
                && req.departureWindowStart() != null
                && req.returnWindowStart().isBefore(req.departureWindowStart())) {
            erro(ctx, "returnWindowStart", "a volta nao pode comecar antes da ida");
            valido = false;
        }

        boolean temMin = req.minStayDays() != null;
        boolean temMax = req.maxStayDays() != null;

        if (temMin != temMax) {
            erro(ctx, temMin ? "maxStayDays" : "minStayDays",
                    "a permanencia precisa ter minimo e maximo, ou nenhum dos dois");
            valido = false;
        } else if (temMin && req.maxStayDays() < req.minStayDays()) {
            erro(ctx, "maxStayDays", "deve ser maior ou igual a minStayDays");
            valido = false;
        }

        return valido;
    }

    private void erro(ConstraintValidatorContext ctx, String campo, String mensagem) {
        ctx.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode(campo)
                .addConstraintViolation();
    }
}
