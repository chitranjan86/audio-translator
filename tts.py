import sys
from gtts import gTTS

# Usage: python tts.py <text_file> <language_code> <output_mp3_path>
text_file = sys.argv[1]
language_code = sys.argv[2]
output_path = sys.argv[3]

with open(text_file, "r", encoding="utf-8") as f:
    text = f.read()

tts = gTTS(text=text, lang=language_code)
tts.save(output_path)