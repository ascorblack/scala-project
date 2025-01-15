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
