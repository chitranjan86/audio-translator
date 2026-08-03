import sys
from deep_translator import GoogleTranslator

# Usage: python translate.py <input_file> <source_lang> <target_lang> <output_file>
input_file = sys.argv[1]
source_lang = sys.argv[2]
target_lang = sys.argv[3]
output_file = sys.argv[4]

with open(input_file, "r", encoding="utf-8") as f:
    text = f.read()

translated = GoogleTranslator(source=source_lang, target=target_lang).translate(text)

with open(output_file, "w", encoding="utf-8") as f:
    f.write(translated)