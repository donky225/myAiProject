"""
STT(faster-whisper)와 TTS(MeloTTS)를 REST API로 제공하는 서버.
Spring Boot 앱(:8080)이 이 서버(:8001)를 호출하는 구조입니다.

사전 설치:
    pip install fastapi uvicorn python-multipart faster-whisper
    pip install git+https://github.com/myshell-ai/MeloTTS.git
    python -m unidic download

실행:
    uvicorn voice_service:app --host 0.0.0.0 --port 8001
"""
import subprocess
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel

from faster_whisper import WhisperModel
from melo.api import TTS

app = FastAPI(title="Voice Pipeline Service")

# 모델은 서버 기동 시 한 번만 로딩해서 재사용합니다 (요청마다 로딩하면 매우 느림).
print("STT 모델 로딩 중...")
stt_model = WhisperModel("small", device="cpu", compute_type="int8")

print("TTS 모델 로딩 중...")
tts_model = TTS(language="KR", device="cpu")
tts_speaker_id = tts_model.hps.data.spk2id["KR"]

print("모든 모델 로딩 완료. 서버 준비됨.")


def convert_to_wav(input_path: str) -> str:
    output_path = tempfile.NamedTemporaryFile(suffix=".wav", delete=False).name
    result = subprocess.run(
        ["ffmpeg", "-y", "-i", input_path, "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", output_path],
        capture_output=True, text=True, encoding="utf-8", errors="ignore",
    )
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg 변환 실패: {result.stderr}")
    return output_path


@app.post("/stt")
async def speech_to_text(file: UploadFile = File(...)):
    """음성 파일(m4a/mp3/wav 등)을 받아 텍스트로 전사합니다."""
    suffix = Path(file.filename).suffix or ".tmp"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(await file.read())
        input_path = tmp.name

    try:
        wav_path = convert_to_wav(input_path)
        try:
            segments, info = stt_model.transcribe(wav_path, language="ko", beam_size=5)
            text = "".join(segment.text for segment in segments).strip()
            return {"text": text, "language": info.language, "language_probability": info.language_probability}
        finally:
            Path(wav_path).unlink(missing_ok=True)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        Path(input_path).unlink(missing_ok=True)


import re
import traceback

class TtsRequest(BaseModel):
    text: str
    speed: float = 1.0


# MeloTTS 한국어 g2p가 인식하지 못하는 특수문자(=, *, #, @ 등)를 만나면
# KeyError로 죽는 문제가 있어, TTS로 보내기 전에 안전한 문자만 남기고 정리합니다.
# 한글, 영문, 숫자, 기본 문장부호(.,!?:;'"()-~ 공백)는 유지하고 나머지는 공백으로 치환합니다.
_SAFE_TEXT_PATTERN = re.compile(
    r"[^가-힣ㄱ-ㅎㅏ-ㅣ0-9a-zA-Z.,!?:;'\"()\-~\s]"
)


def sanitize_for_tts(text: str) -> str:
    cleaned = _SAFE_TEXT_PATTERN.sub(" ", text)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


@app.post("/tts")
async def text_to_speech(request: TtsRequest):
    """텍스트를 받아 음성 WAV 파일을 반환합니다."""
    if not request.text or not request.text.strip():
        raise HTTPException(status_code=400, detail="text가 비어있습니다.")

    safe_text = sanitize_for_tts(request.text)
    if not safe_text:
        raise HTTPException(status_code=400, detail="TTS로 변환 가능한 텍스트가 없습니다 (특수문자만 존재).")

    output_path = tempfile.NamedTemporaryFile(suffix=".wav", delete=False).name

    try:
        tts_model.tts_to_file(safe_text, tts_speaker_id, output_path, speed=request.speed)
        return FileResponse(output_path, media_type="audio/wav", filename="speech.wav")
    except Exception as e:
        traceback.print_exc()  # 콘솔에 전체 스택트레이스 출력 (원인 진단용)
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
async def health():
    return {"status": "ok"}