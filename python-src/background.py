import requests
import time
import logging
from dataclasses import dataclass
from pydantic import BaseModel
from typing import List, Dict, Any, Optional

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)

# Конфигурация URL-адресов
IMAGES_URL = "http://scala:8888/api/v1/imagesWithoutTags"
DETECT_URL = "http://0.0.0.0:55555/detectObjects"
TAGS_URL = "http://scala:8888/api/v1/imageTags"

# Интервал выполнения в секундах
INTERVAL = 10


@dataclass
class DetectedObject:
    ru_tag: str
    en_tag: str
    confidence: float
    bbox: List[float]


@dataclass
class DetectedObjects:
    detected_objects: List[DetectedObject]
    annotated_image: str


@dataclass
class TagItem:
    image_id: int
    tag: str


class ImageBase64(BaseModel):
    id: int
    image_name: str
    base64Image: str


class ImagesResponse(BaseModel):
    status: Optional[int] = None
    message: Optional[str] = None
    data: Optional[list[ImageBase64]] = None
    error: Optional[str] = None


def fetch_images() -> ImagesResponse:
    """
    Получает список изображений без тегов с сервера.
    """
    try:
        response = requests.get(IMAGES_URL)
        response.raise_for_status()
        images = ImagesResponse(**response.json())
        logging.info(f"Получено {len(images.data)} изображений без тегов.")
        return images
    except requests.RequestException as e:
        logging.error(f"Ошибка при получении изображений: {e}")
        return ImagesResponse()


def detect_objects(base64_image: str) -> Dict[str, Any]:
    """
    Отправляет изображение на сервис распознавания объектов и возвращает результат.
    """
    try:
        payload = {"image_base64": base64_image}
        response = requests.post(DETECT_URL, json=payload)
        response.raise_for_status()
        detected = response.json()
        logging.info("Успешно распознаны объекты на изображении.")
        return detected
    except requests.RequestException as e:
        logging.error(f"Ошибка при распознавании объектов: {e}")
        return {}


def send_tags(tag_items: List[Dict[str, Any]]) -> None:
    """
    Отправляет список тегов на сервер.
    """
    if not tag_items:
        logging.info("Нет тегов для отправки.")
        return

    try:
        response = requests.post(TAGS_URL, json=tag_items)
        response.raise_for_status()
        logging.info(f"Отправлено {len(tag_items)} тегов на сервер.")
    except requests.RequestException as e:
        logging.error(f"Ошибка при отправке тегов: {e}")


def process_images(images: ImagesResponse) -> List[Dict[str, Any]]:
    """
    Обрабатывает список изображений: распознаёт объекты и создаёт список тегов.
    """
    tag_items: List[TagItem] = []

    for image in images.data:
        image_id = image.id
        image_name = image.image_name
        base64_image = image.base64Image

        if not all([image_id, image_name, base64_image]):
            logging.warning(f"Пропущено изображение с неполными данными: {image}")
            continue

        logging.info(f"Обработка изображения ID: {image_id}, Название: {image_name}")

        detected = detect_objects(base64_image)

        if not detected:
            logging.warning(f"Не удалось распознать объекты для изображения ID: {image_id}")
            continue

        detected_objects = detected.get("detected_objects", [])

        for obj in detected_objects:
            ru_tag = obj.get("ru_tag")
            en_tag = obj.get("en_tag")
            confidence = obj.get("confidence")
            bbox = obj.get("bbox")

            if ru_tag:
                tag_items.append(TagItem(image_id=image_id, tag=ru_tag))
            if en_tag:
                tag_items.append(TagItem(image_id=image_id, tag=en_tag))

    # Преобразование TagItem в словари для отправки
    tag_items_dict = [tag_item.__dict__ for tag_item in tag_items]
    return tag_items_dict


def main():
    logging.info("Запуск фонового процесса обработки изображений.")

    while True:
        logging.info("Начало итерации обработки изображений.")

        images = fetch_images()

        if images:
            tag_items = process_images(images)
            send_tags(tag_items)
        else:
            logging.info("Нет изображений для обработки.")

        logging.info(f"Завершение итерации. Ожидание {INTERVAL} секунд.")
        time.sleep(INTERVAL)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        logging.info("Фоновый процесс остановлен пользователем.")
