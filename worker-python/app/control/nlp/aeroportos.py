"""De nome de cidade para codigo IATA — etapa E3.1.

# Por que uma tabela, e nao so o LLM

O sistema inteiro fala IATA: o monitor guarda IATA, as fontes recebem IATA, o
historico e por IATA. Mas ninguem diz "quero ir para LIS" — diz "quero ir para
Lisboa".

Esta tabela precisa existir com ou sem LLM. Mesmo um modelo bom erra codigo de
aeroporto com frequencia, porque sao milhares e a diferenca entre GIG e SDU e
uma letra. Com a tabela, o modelo so precisa acertar o NOME da cidade, que e o
que ele faz bem; a traducao para codigo fica aqui, deterministica e conferivel.

# O que entra

Aeroportos que alguem no Brasil realmente monitoraria: capitais e destinos
turisticos nacionais, e os internacionais mais procurados. Nao e um banco de
dados de aviacao — e a lista de lugares que este sistema vai ver.

Cidade com mais de um aeroporto tem um preferido, e os outros continuam
reconheciveis pelo nome proprio: quem diz "Sao Paulo" quer GRU, quem diz
"Congonhas" quer CGH.
"""

import re
import unicodedata

#: nome normalizado -> codigo IATA
CIDADES: dict[str, str] = {}


def _normalizar(texto: str) -> str:
    """Minuscula, sem acento e sem pontuacao.

    Sem isso "Sao Paulo", "sao paulo" e "Sao  Paulo" seriam tres cidades
    diferentes — e a pessoa escreve dos tres jeitos.
    """
    sem_acento = "".join(
        c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn"
    )
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ]", " ", sem_acento.lower())).strip()


def _registrar(codigo: str, *nomes: str) -> None:
    for nome in nomes:
        CIDADES[_normalizar(nome)] = codigo


# ------------------------------------------------------------------ Brasil

_registrar("GRU", "Sao Paulo", "Guarulhos", "Cumbica")
_registrar("CGH", "Congonhas")
_registrar("VCP", "Viracopos", "Campinas")
_registrar("GIG", "Rio de Janeiro", "Galeao", "Rio")
_registrar("SDU", "Santos Dumont")
_registrar("BSB", "Brasilia")
_registrar("CNF", "Belo Horizonte", "Confins")
_registrar("POA", "Porto Alegre")
_registrar("CWB", "Curitiba")
_registrar("FLN", "Florianopolis", "Floripa")
_registrar("SSA", "Salvador")
_registrar("REC", "Recife")
_registrar("FOR", "Fortaleza")
_registrar("NAT", "Natal")
_registrar("MCZ", "Maceio")
_registrar("AJU", "Aracaju")
_registrar("JPA", "Joao Pessoa")
_registrar("THE", "Teresina")
_registrar("SLZ", "Sao Luis")
_registrar("BEL", "Belem")
_registrar("MAO", "Manaus")
_registrar("PMW", "Palmas")
_registrar("CGB", "Cuiaba")
_registrar("CGR", "Campo Grande")
_registrar("GYN", "Goiania")
_registrar("VIX", "Vitoria")
_registrar("IGU", "Foz do Iguacu", "Foz")
_registrar("IOS", "Ilheus")
_registrar("BPS", "Porto Seguro")
_registrar("FEN", "Fernando de Noronha", "Noronha")
_registrar("JJD", "Jericoacoara", "Jeri")
_registrar("LDB", "Londrina")
_registrar("RAO", "Ribeirao Preto")
_registrar("UDI", "Uberlandia")
_registrar("JOI", "Joinville")
_registrar("NVT", "Navegantes", "Balneario Camboriu")

# --------------------------------------------------------- America do Sul

_registrar("EZE", "Buenos Aires")
_registrar("MVD", "Montevideu", "Montevideo")
_registrar("SCL", "Santiago")
_registrar("LIM", "Lima")
_registrar("BOG", "Bogota")
_registrar("CTG", "Cartagena")
_registrar("ASU", "Assuncao", "Asuncion")
_registrar("LPB", "La Paz")
_registrar("CUZ", "Cusco", "Machu Picchu")
_registrar("UIO", "Quito")

# --------------------------------------------- America do Norte e Central

_registrar("MIA", "Miami")
_registrar("MCO", "Orlando")
_registrar("JFK", "Nova York", "New York")
_registrar("LAX", "Los Angeles")
_registrar("SFO", "Sao Francisco", "San Francisco")
_registrar("ORD", "Chicago")
_registrar("IAD", "Washington")
_registrar("BOS", "Boston")
_registrar("LAS", "Las Vegas")
_registrar("YYZ", "Toronto")
_registrar("YUL", "Montreal")
_registrar("MEX", "Cidade do Mexico")
_registrar("CUN", "Cancun")
_registrar("PTY", "Cidade do Panama", "Panama")
_registrar("SJO", "Costa Rica")
_registrar("HAV", "Havana")
_registrar("PUJ", "Punta Cana")

# ------------------------------------------------------------------ Europa

_registrar("LIS", "Lisboa")
_registrar("OPO", "Porto")
_registrar("MAD", "Madri", "Madrid")
_registrar("BCN", "Barcelona")
_registrar("CDG", "Paris")
_registrar("LHR", "Londres", "London")
_registrar("FCO", "Roma")
_registrar("MXP", "Milao", "Milano")
_registrar("VCE", "Veneza")
_registrar("AMS", "Amsterda", "Amsterdam")
_registrar("FRA", "Frankfurt")
_registrar("MUC", "Munique")
_registrar("BER", "Berlim")
_registrar("ZRH", "Zurique")
_registrar("VIE", "Viena")
_registrar("PRG", "Praga")
_registrar("BUD", "Budapeste")
_registrar("ATH", "Atenas")
_registrar("IST", "Istambul")
_registrar("DUB", "Dublin")
_registrar("CPH", "Copenhague")
_registrar("OSL", "Oslo")
_registrar("ARN", "Estocolmo")
_registrar("BRU", "Bruxelas")

# --------------------------------------------- Africa, Asia e Oceania

_registrar("JNB", "Joanesburgo")
_registrar("CPT", "Cidade do Cabo", "Cape Town")
_registrar("CAI", "Cairo")
_registrar("CMN", "Casablanca")
_registrar("RAK", "Marrakech")
_registrar("DXB", "Dubai")
_registrar("DOH", "Doha")
_registrar("TLV", "Tel Aviv")
_registrar("NRT", "Toquio")
_registrar("ICN", "Seul")
_registrar("PEK", "Pequim")
_registrar("PVG", "Xangai")
_registrar("HKG", "Hong Kong")
_registrar("BKK", "Bangkok", "Bangcoc")
_registrar("SIN", "Singapura")
_registrar("DPS", "Bali")
_registrar("DEL", "Nova Delhi")
_registrar("SYD", "Sidney", "Sydney")
_registrar("AKL", "Auckland")


def resolver(nome: str | None) -> str | None:
    """Codigo IATA de um nome de cidade, ou o proprio codigo se ja for um.

    Devolve `None` quando nao reconhece, e o chamador transforma isso em aviso.
    **Chutar aeroporto e pior do que perguntar**: o monitor passaria meses
    vigiando a rota errada, em silencio, e o usuario so descobriria pela
    ausencia de alertas.
    """
    if not nome:
        return None

    bruto = nome.strip()

    # Ja e codigo IATA? Tres letras, e nao e nome de cidade conhecido.
    if re.fullmatch(r"[A-Za-z]{3}", bruto) and _normalizar(bruto) not in CIDADES:
        return bruto.upper()

    return CIDADES.get(_normalizar(bruto))


def procurar_no_texto(texto: str) -> list[tuple[str, str, int]]:
    """Cidades citadas no texto, na ordem em que aparecem.

    Devolve `(codigo, nome_encontrado, posicao)`. A posicao importa: e ela que
    permite distinguir origem de destino em "de X para Y". Sem ela, duas
    cidades numa frase seriam indistinguiveis.

    Nomes mais longos vencem os curtos — "Porto Alegre" nao pode ser lido como
    "Porto", que e outra cidade, em outro continente.
    """
    normalizado = _normalizar(texto)
    achados: list[tuple[str, str, int]] = []
    ocupado: list[tuple[int, int]] = []

    for nome in sorted(CIDADES, key=len, reverse=True):
        for m in re.finditer(rf"(?<![a-z0-9]){re.escape(nome)}(?![a-z0-9])", normalizado):
            if any(m.start() < fim and inicio < m.end() for inicio, fim in ocupado):
                # Trecho ja consumido por um nome mais longo.
                continue
            achados.append((CIDADES[nome], nome, m.start()))
            ocupado.append((m.start(), m.end()))

    return sorted(achados, key=lambda a: a[2])
