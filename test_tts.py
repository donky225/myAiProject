"""
MeloTTS(한국어)로 텍스트를 음성으로 변환하는 테스트 스크립트.

사전 설치:
    pip install git+https://github.com/myshell-ai/MeloTTS.git
    python -m unidic download

사용법:
    python test_tts.py "안녕하세요 저는 최영입니다"
"""
import sys
import time

from melo.api import TTS

DEVICE = "cpu"  # GPU가 있으면 "cuda:0"도 가능하지만, CPU로도 충분히 빠름


def main():
    if len(sys.argv) < 2:
        print('사용법: python test_tts.py "변환할 텍스트"')
        sys.exit(1)

    text = sys.argv[1]

    print("모델 로딩 중... (최초 실행 시 모델 다운로드로 시간이 걸릴 수 있습니다)")
    model = TTS(language="KR", device=DEVICE)
    speaker_ids = model.hps.data.spk2id

    output_path = "output.wav"

    print(f"음성 생성 중: {text}")
    start = time.time()

    model.tts_to_file(text, speaker_ids["KR"], output_path, speed=1.0)

    elapsed = time.time() - start
    print(f"완료. {output_path} 에 저장됨. 소요 시간: {elapsed:.2f}초")


if __name__ == "__main__":
    main()