FROM python:3.11-slim
WORKDIR /app
RUN pip install --no-cache-dir kafka-python==2.0.2 lz4
COPY bridge.py .
CMD ["python", "-u", "bridge.py"]
