# Руководство по запуску проекта

Этот гайд объясняет, как запустить проект с использованием Docker Compose. Проект состоит из трёх сервисов: PostgreSQL, Scala-бэкенд и фонового AI-сервиса.

## Предварительные требования

- Установленный [Docker](https://docs.docker.com/get-docker/)
- Установленный [Docker Compose](https://docs.docker.com/compose/install/)

## Структура проекта

Проект включает следующие сервисы:
- **postgres**: база данных PostgreSQL с инициализацией схемы.
- **scala**: Scala-бэкенд-приложение.
- **background-ai**: Python-сервис для фоновых задач ИИ.

## Шаги для запуска

### 1. Клонировать репозиторий

Склонируйте репозиторий проекта на локальную машину:
```bash
git clone git@gitlab.education.tbank.ru:scala-course-autumn-2024/Students/ascorblack/Projects/Project.git
cd Project
git checkout review
```

### 2. Настроить инициализационные скрипты
Убедитесь, что у вас есть папка db-init с инициализационным SQL-скриптом для создания таблиц:

```csharp
db-init/
└── init.sql
```

Содержимое init.sql должно включать:

```sql
create table if not exists public.images
(
    id          serial
        constraint images_pk
            primary key,
    image_name  varchar(255)  not null
        constraint images_pk_2
            unique,
    image_path  varchar(4096) not null,
    last_update timestamp
);

alter table public.images
    owner to dev;

create table if not exists public.image_tags
(
    id       serial
        constraint image_tags_pk
            primary key,
    image_id integer not null
        constraint image_tags_images_id_fk
            references public.images,
    tag      text
);

alter table public.image_tags
    owner to dev;

create index if not exists idx_image_tags_tag_tsv
    on public.image_tags using gin (to_tsvector('russian'::regconfig, tag));
```


### 3. Запустить Docker Compose
В корне проекта, где находится файл docker-compose.yml, выполните команду:

```bash
docker-compose up --build -d # для detach запуска
```


### 3.1 Остановка сервисов
Чтобы остановить все запущенные контейнеры выполните:

```bash
docker-compose down
```


## Очень краткое описание проекта

Идея проекта: поиск по фото

#### Для этого было сделано:
- Бэкенд на Scala (Основное API)
- Бэкенд на Python (в качестве прослойки между background таской и Scala API)
- Фоновый процесс на Python (который при помощи YOLO распознаёт объекты на картинках и добавляет их в базу через Scala API)

## Основные роуты:
- http://0.0.0.0:8888/docs - Здесь расположена документация к API (Swagger API)
- http://0.0.0.0:8888/ui - Здесь можно загрузить картинку (т.к. API для добавления картинки требует не файл, а base64-строку)

