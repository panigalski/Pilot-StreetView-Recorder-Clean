# Temperature Sound Threshold — v2.2

Adds a user-configurable CPU temperature warning sound threshold.

## Access
Tap the **Pro** badge on the upper-right of the main screen, then choose **Temperature Warning**.

## Behaviour
- Adjustable range: 60°C to 95°C.
- Default: 80°C.
- Setting is persisted in app preferences.
- **Test Sound** plays the same alarm tone without changing the threshold.
- When CPU temperature reaches or exceeds the selected threshold, a warning tone is played once.
- The tone re-arms only after temperature falls at least 3°C below the selected threshold. This prevents repeated alarms when temperature fluctuates around one value.
- Existing Labpano-style visual warnings at 80°C and 85°C remain unchanged.
- Recording is not stopped automatically.
- Monitoring continues on the existing 1-second background temperature watcher.

No GitHub Actions workflow files are changed.
