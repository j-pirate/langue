Synthetic silence fixtures for Android decoder tests only. No educational recordings.
Generated locally with FFmpeg from anullsrc, with metadata removed:

ffmpeg -f lavfi -i anullsrc=r=16000:cl=mono -t 0.6 -map_metadata -1 -c:a libmp3lame -b:a 32k silence.mp3
ffmpeg -f lavfi -i anullsrc=r=16000:cl=mono -t 0.6 -map_metadata -1 -c:a aac -b:a 32k -movflags +faststart silence.m4a

silence.mp3: MP3, mono 16000 Hz, about 0.68 seconds including codec padding.
silence.m4a: AAC in an M4A container, mono 16000 Hz, 0.6 seconds.
WAV is generated as PCM in TestPacks.tinyWave().
