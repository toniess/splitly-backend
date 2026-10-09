# ============================================================
#  Ступень 1: сборка. Здесь нужен JDK и Gradle — в финальный
#  образ эта ступень целиком не попадёт.
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Сначала только то, что описывает зависимости. Этот слой переживёт
# правки кода: пока build.gradle.kts не менялся, Docker возьмёт его
# из кеша и не будет заново качать сотни мегабайт.
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# И только теперь исходники — они меняются каждую сборку.
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# Раскладываем jar по слоям: код отдельно, библиотеки отдельно.
# Маска *-SNAPSHOT.jar берёт только исполняемый jar — splitly-...-plain.jar
# под неё не подходит.
RUN java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar \
        extract --layers --destination extracted \
    && mv extracted/application/*-SNAPSHOT.jar extracted/application/app.jar

# ============================================================
#  Ступень 2: запуск. Только JRE — ни Gradle, ни компилятора,
#  ни исходников здесь нет.
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Процесс не должен работать от root: если его скомпрометируют,
# права внутри контейнера будут минимальными.
RUN addgroup -S splitly && adduser -S splitly -G splitly

# Порядок важен: от самого редко меняющегося слоя к самому частому.
# При пересборке после правки кода Docker переиспользует первые три.
COPY --from=builder --chown=splitly:splitly /build/extracted/dependencies/ ./
COPY --from=builder --chown=splitly:splitly /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=splitly:splitly /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=splitly:splitly /build/extracted/application/ ./

USER splitly

# Пометка для читателя и для docker compose; сам порт она не открывает.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
