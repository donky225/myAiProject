"""
faster-whisper로 음성 파일을 텍스트로 전사하는 테스트 스크립트.
m4a, mp3, wav 등 어떤 포맷이든 입력 가능합니다 (내부적으로 ffmpeg로 16kHz mono wav 변환 후 처리).

사용법:
    python test_stt.py my_voice.m4a
    python test_stt.py my_voice.mp3
    python test_stt.py my_voice.wav
"""
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from faster_whisper import WhisperModel

MODEL_SIZE = "small"  # tiny/base/small/medium/large-v3 중 선택. 실시간 대화용으로는 small이 속도/정확도 균형이 좋음
DEVICE = "cpu"         # GPU(cuda)를 쓰려면 cuBLAS/cuDNN 별도 설치 필요. 우선 CPU로 검증 (small 모델은 CPU도 충분히 빠름)
COMPUTE_TYPE = "int8"  # CPU에서의 양자화 설정


def convert_to_wav(input_path: str) -> str:
    """
    입력 오디오 파일을 16kHz mono WAV로 변환합니다.
    이미 조건에 맞는 wav라도 일관성을 위해 항상 변환을 거칩니다.
    변환된 파일은 임시 파일로 생성되며, 호출 측에서 사용 후 삭제해야 합니다.
    """
    output_path = tempfile.NamedTemporaryFile(suffix=".wav", delete=False).name

    result = subprocess.run(
        [
            "ffmpeg", "-y",
            "-i", input_path,
            "-ar", "16000",   # Whisper가 기대하는 샘플레이트
            "-ac", "1",       # mono
            "-c:a", "pcm_s16le",
            output_path,
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="ignore",  # ffmpeg 로그에 섞인 일부 바이트가 시스템 기본 인코딩과 안 맞아도 무시
    )

    if result.returncode != 0:
        raise RuntimeError(
            f"ffmpeg 변환 실패 (ffmpeg가 설치되어 있는지 확인하세요: winget install Gyan.FFmpeg)\n{result.stderr}"
        )

    return output_path


def main():
    if len(sys.argv) < 2:
        print("사용법: python test_stt.py <음성파일.m4a|.mp3|.wav>")
        sys.exit(1)

    input_path = sys.argv[1]
    if not Path(input_path).exists():
        print(f"파일을 찾을 수 없습니다: {input_path}")
        sys.exit(1)

    print(f"오디오 변환 중: {input_path} -> 16kHz mono WAV")
    wav_path = convert_to_wav(input_path)

    try:
        print(f"모델 로딩 중... ({MODEL_SIZE}, {DEVICE}, {COMPUTE_TYPE})")
        model = WhisperModel(MODEL_SIZE, device=DEVICE, compute_type=COMPUTE_TYPE)

        print(f"전사 시작: {input_path}")
        start = time.time()

        segments, info = model.transcribe(wav_path, language="ko", beam_size=5)

        print(f"감지된 언어: {info.language} (확률 {info.language_probability:.2f})")
        print("\n=== 전사 결과 ===")

        full_text = []
        for segment in segments:
            print(f"[{segment.start:.1f}s -> {segment.end:.1f}s] {segment.text}")
            full_text.append(segment.text)

        elapsed = time.time() - start
        print(f"\n전체 소요 시간: {elapsed:.2f}초")
        print(f"전체 텍스트: {''.join(full_text).strip()}")

    finally:
        # 임시 wav 파일 정리
        Path(wav_path).unlink(missing_ok=True)


if __name__ == "__main__":
    main()