FROM python:3.11-slim

WORKDIR /app

# More resilient pip on flaky VPS networks (timeouts to pypi.org)
ENV PIP_DEFAULT_TIMEOUT=120 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_RETRIES=10

COPY requirements.txt .
RUN pip install --no-cache-dir --retries 10 --timeout 120 -r requirements.txt

COPY . .

RUN mkdir -p uploads static

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
