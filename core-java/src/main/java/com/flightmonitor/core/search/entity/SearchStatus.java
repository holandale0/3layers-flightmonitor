package com.flightmonitor.core.search.entity;

/** Situacao de uma execucao de varredura. Os nomes batem com o CHECK do banco. */
public enum SearchStatus {

    RUNNING,

    SUCCESS,

    /** Concluiu, mas alguma fonte falhou — o sistema degradou sem morrer. */
    PARTIAL,

    FAILED
}
