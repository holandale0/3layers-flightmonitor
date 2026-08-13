# =============================================================================
# core-java — etapa E4.2
#
# Duas etapas: a primeira compila, a segunda so roda. A imagem final nao carrega
# Maven, codigo-fonte nem o repositorio de dependencias — o que a deixa menor e,
# mais importante, tira do ambiente de execucao ferramentas que ninguem deveria
# poder usar la dentro.
# =============================================================================

# ----------------------------------------------------------------- construcao
FROM maven:3.9-eclipse-temurin-21 AS construcao
WORKDIR /construcao

# O pom sozinho primeiro, e so depois o codigo: enquanto as dependencias nao
# mudarem, o Docker reaproveita esta camada e nao baixa a internet inteira a
# cada alteracao de uma linha de Java.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Sem testes aqui de proposito: eles precisam de PostgreSQL e RabbitMQ, que nao
# existem durante o build da imagem. A suite roda antes, no `mvn test` — e o
# lugar dela e la, com os servicos de pe. Ver secao 9 do PLANO-DE-ACAO.
RUN mvn -B -DskipTests package

# ------------------------------------------------------------------- execucao
FROM eclipse-temurin:21-jre-alpine

# Usuario sem privilegio: se alguem escapar da aplicacao, escapa para uma conta
# que nao pode nada. `-S` cria conta de sistema, sem senha e sem shell de login.
RUN addgroup -S flightmon && adduser -S -G flightmon flightmon

WORKDIR /app
COPY --from=construcao /construcao/target/*.jar app.jar
USER flightmon

EXPOSE 8081

# `-XX:MaxRAMPercentage` em vez de `-Xmx` fixo: a JVM le o limite do container e
# calcula a heap a partir dele. Com valor fixo, mudar a memoria do container nao
# mudaria nada — e a JVM seria morta pelo cgroup sem explicacao no log.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# `exec` para o java virar o PID 1 e receber o SIGTERM do `docker stop`. Sem
# isso o shell fica no PID 1, engole o sinal, e o container leva 10 segundos
# para morrer no timeout — com desligamento sujo em vez de gracioso.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
