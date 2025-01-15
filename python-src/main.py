from traceback import format_exc
from fastapi import FastAPI, HTTPException
from fastapi.responses import UJSONResponse
from pydantic import BaseModel
import base64
from io import BytesIO
from ultralytics import YOLO
from PIL import Image, ImageDraw, ImageFont
from googletrans import Translator

app = FastAPI(title="YOLOv11 Object Detection API")

MODEL_PATH = 'yolo11m.pt'
translator = Translator()
model = YOLO(MODEL_PATH)
model.to('cpu')


class ImageInput(BaseModel):
    image_base64: str


class DetectedObject(BaseModel):
    ru_tag: str
    en_tag: str
    confidence: float
    bbox: list[float]


class DetectedObjects(BaseModel):
    detected_objects: list[DetectedObject]
    annotated_image: str


@app.post("/detectObjects", response_model=DetectedObjects, response_class=UJSONResponse)
async def detect_objects(image_input: ImageInput):
    try:
        image_data = base64.b64decode(image_input.image_base64)
        image = Image.open(BytesIO(image_data)).convert('RGB')

        results = model.predict(image, verbose=False)

        detected_objects: list[DetectedObject] = []
        draw = ImageDraw.Draw(image)

        try:
            font = ImageFont.truetype("arial.ttf", size=16)
        except IOError:
            font = ImageFont.load_default()

        for result in results:
            for box in result.boxes:
                class_idx = int(box.cls.cpu().numpy())
                cls_en = model.names[class_idx]
                translation = translator.translate(cls_en, src='en', dest='ru')
                cls_ru = translation.text
                class_names = [cls_ru, cls_en]
                confidence = float(box.conf.cpu().numpy())
                bbox = box.xyxy.cpu().numpy().tolist()[0]

                detected_objects.append(
                    DetectedObject(
                        ru_tag=class_names[0],
                        en_tag=class_names[1],
                        confidence=confidence,
                        bbox=bbox
                    )
                )

                x1, y1, x2, y2 = bbox
                draw.rectangle([x1, y1, x2, y2], outline="red", width=2)
                label = f"{cls_en} {confidence:.2f}"

                text_bbox = draw.textbbox((0, 0), label, font=font)
                text_width = text_bbox[2] - text_bbox[0]
                text_height = text_bbox[3] - text_bbox[1]

                draw.rectangle([x1, y1 - text_height, x1 + text_width, y1], fill="red")
                draw.text((x1, y1 - text_height), label, fill="white", font=font)

        buffered = BytesIO()
        image.save(buffered, format="JPEG")
        annotated_image_base64 = base64.b64encode(buffered.getvalue()).decode('utf-8')

        return UJSONResponse(
            DetectedObjects(
                detected_objects=detected_objects,
                annotated_image=annotated_image_base64
            ).model_dump()
        )

    except Exception:
        print(format_exc())
        raise HTTPException(
            status_code=400,
            detail=f"Ошибка обработки изображения: {str(format_exc())}"
        )
