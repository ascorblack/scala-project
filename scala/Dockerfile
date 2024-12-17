# Используем образ с JDK 17 для Scala
FROM openjdk:17-jdk-slim

# Устанавливаем необходимые инструменты (SBT и curl)
ARG SBT_VERSION=1.10.6
RUN apt-get update && apt-get install -y curl gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x99e82a75642ac823" | gpg --dearmor > /etc/apt/trusted.gpg.d/sbt.gpg && \
    apt-get update && apt-get install -y sbt=${SBT_VERSION} && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Создаем рабочую директорию
WORKDIR /app

# Копируем build.sbt для предварительной загрузки зависимостей
COPY build.sbt /app/

# Кэшируем зависимости
RUN sbt update

ENV SBT_OPTS="--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"

# Основная команда для запуска контейнера
CMD ["sbt", "run"]
