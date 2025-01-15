screen -dm bash -c "poetry run python background.py"
poetry run uvicorn main:app --workers 2 --host 0.0.0.0 --port 55555
