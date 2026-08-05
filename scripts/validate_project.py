#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'settings.gradle',
    'build.gradle',
    'gradle.properties',
    'gradlew',
    'gradle/wrapper/gradle-wrapper.jar',
    'gradle/wrapper/gradle-wrapper.properties',
    'app/build.gradle',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/panigalski/pilotstreetview/MainActivity.java',
    'app/src/main/java/com/panigalski/pilotstreetview/SettingsDialog.java',
    'app/src/main/java/com/panigalski/pilotstreetview/StorageSelectionDialog.java',
    'app/src/main/java/com/panigalski/pilotstreetview/StorageResolver.java',
    'app/src/main/java/com/panigalski/pilotstreetview/SegmentedStreetViewRecorder.java',
    'app/src/main/res/raw/start_recording.wav',
    'app/src/main/res/raw/recording_stop_cue.wav',
    'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    'pano/build.gradle',
    'pano/src/main/AndroidManifest.xml',
    'pano/src/main/java/com/pi/pano/PilotSDK.java',
    'pano/src/main/java/com/pi/pano/CameraSurfaceView.java',
    'pano/src/main/jniLibs/arm64-v8a/libPiPano.so',
]

missing = [name for name in REQUIRED if not (ROOT / name).is_file()]
if missing:
    print('Missing required files:')
    for name in missing:
        print(f'  - {name}')
    sys.exit(1)

for xml_path in sorted((ROOT / 'app/src/main/res').rglob('*.xml')):
    ET.parse(xml_path)
ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
ET.parse(ROOT / 'pano/src/main/AndroidManifest.xml')

main_source = (ROOT / 'app/src/main/java/com/panigalski/pilotstreetview/MainActivity.java').read_text(encoding='utf-8')
if 'startActivity(new Intent' in main_source:
    print('Unsafe secondary Activity navigation was reintroduced.')
    sys.exit(1)
if 'StorageSelectionDialog.show' not in main_source:
    print('In-place storage selector hook is missing.')
    sys.exit(1)
if 'PictureAdjustmentsDialog.show' not in main_source:
    print('Direct picture-adjustment button hook is missing.')
    sys.exit(1)

camera_source = (ROOT / 'pano/src/main/java/com/pi/pano/CameraSurfaceView.java').read_text(encoding='utf-8')
if 'automatic camera reopen suppressed' not in camera_source:
    print('Pilot camera stability patch is missing.')
    sys.exit(1)

print(f'Validation passed: {len(REQUIRED)} required files and Android XML parsed successfully.')
