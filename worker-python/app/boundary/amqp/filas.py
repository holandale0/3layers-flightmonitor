"""Os nomes combinados com o core-java — etapa E4.1.

Espelho de `core-java/.../search/client/amqp/FilasDaBusca.java`. Um nome
digitado errado aqui nao da erro em lugar nenhum: a mensagem fica numa fila que
ninguem escuta, e a varredura do outro lado falha por timeout — sintoma que nao
aponta para a causa.

Quem **declara** as filas e o core, nao este servico. Com dois donos, uma
divergencia de argumento (durable, dead-letter) vira erro de canal na conexao, e
o servico que subir primeiro ganha. Aqui apenas consumimos.
"""

EXCHANGE = "flight.search"

FILA_CALENDARIO = "flight.search.requested.calendar"
FILA_CONFIRMACAO = "flight.search.requested.confirm"

#: Diz ao core que a FONTE falhou, e nao que a janela estava vazia.
#:
#: Equivalente do HTTP 502 no transporte REST. Espelho de
#: `FilasDaBusca.CABECALHO_FALHA`.
CABECALHO_FALHA = "x-fonte-falhou"

#: Quantas mensagens o worker aceita por vez.
#:
#: Um, de proposito. A confirmacao vai ao Google ao vivo e ja e limitada por
#: bloqueio de excesso de requisicoes (RISCO-004); puxar dez mensagens de uma vez
#: so aumentaria a chance de bloqueio, e as outras nove esperariam do mesmo
#: jeito. Escalar aqui e subir outro worker, nao buscar mais rapido.
PREFETCH = 1
