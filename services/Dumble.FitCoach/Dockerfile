FROM python:3.11-slim

# System deps for OpenCV / MediaPipe
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 libgomp1 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python deps first (better layer caching).
#
# sentence-transformers pulls in PyTorch as a transitive dep. PyTorch's default
# wheel on Linux x86_64 brings ~500MB of CUDA shared libraries (triton,
# nvidia-cuda-cupti, nvidia-cuda-nvrtc, etc.) that this service never uses —
# the embedder is all-MiniLM-L6-v2 running on CPU. Install the CPU-only torch
# wheel first from PyTorch's wheel index so the sentence-transformers install
# resolves against the already-present CPU build instead of pulling the GPU
# stack. Saves ~5GB of image size and shaves ~10 min off cold builds.
COPY requirements.txt .
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu \
        torch torchvision \
 && pip install --no-cache-dir -r requirements.txt

# Pre-download sentence-transformers model so first request isn't slow
# Remove this RUN line if you want a smaller image and don't mind the delay
RUN python -c "\
from sentence_transformers import SentenceTransformer; \
SentenceTransformer('all-MiniLM-L6-v2')" || true

# Download MediaPipe pose model — baked in so container FS doesn't need network at runtime
RUN mkdir -p /app/model && \
    curl -fsSL \
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task" \
    -o /app/model/pose_full.task || true

# Copy application code
COPY . .

# Create memory directory for per-user memory files
RUN mkdir -p /app/memory_data

# Run as non-root for security
RUN useradd -m -u 1000 appuser && \
    chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

# Environment variable defaults (override via --env-file .env)
ENV POSE_MODEL_PATH=/app/model/pose_full.task \
    MEMORY_DIR=/app/memory_data

HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
