This is an emulator of old 8-bit console Dendi/Nintendo for Android.
Supports Video, Sound and two game-pads.
All games in NES format saved in hard cartridge

# NES Emulator for Android

An Android port of the classic NES (Nintendo Entertainment System) game console emulator. This project provides a lightweight, high-performance library and a basic wrapper application to run game ROMs (.nes) on Android-powered mobile devices.

The architecture is centered around porting and optimizing low-level emulation logic (CPU, graphics, and audio co-processors) based on principles similar to .NET NES emulator implementations, fully tailored to the Android lifecycle and graphics subsystem.

## Architecture & Components

The project combines multiple technological layers to achieve maximum framerates and minimize input latency:

1. **Core / Emulation Logic**: A highly optimized core thаt handles registers, memory management, CPU instructions (MOS Technology 6502), PPU (Picture Processing Unit), and APU (Audio Processing Unit) logic.
2. **Low-Level Native Layer (C++/CMake)**: Critical parts of the rendering pipeline are offloaded to the Native layer for direct interaction with screen and audio buffers. This eliminates excessive Garbage Collector (GC) overhead within the JVM/Art runtime.
3. **Android UI Layer (Java/Kotlin)**: 
   - `NESView` Component — a custom UI view dedicated to rendering the game's frame buffer.
   - Virtual Controller — an on-screen gamepad handling input configurations (D-Pad, A, B, Select, Start).

## Features

- **High Performance**: Optimized rendering cycle utilizing direct screen buffer streaming.
- **Aspect Ratio Scaling**: Proper scaling options to handle retro TV aspect ratios on modern displays.
- **Audio Support**: Seamless, stutter-free audio playback via `AudioTrack` interfaces.
- **Save/Load States**: Support for instant game state saving and loading.

## Project Structure

```text
├── app/                      # Demo Android application (emulator wrapper)
├── library/                  # Emulator module (integration library)
│   ├── src/main/java/        # Java/Kotlin wrappers, custom UI component API (NESView, NESKey)
│   ├── src/main/cpp/         # Native C++ core components and Cycloa integration
│   └── CMakeLists.txt        # Native component build configuration
└── build.gradle              # Project build scripts
```

## Build Requirements
- Gradle & Android Studio (Ladybug or newer)
- Android SDK (API level 21+)
- Android NDK (the recommended version is specified in build.gradle / local.properties)

## Initializing and Running a ROM in an Activity/Fragment:
```kotlin
val nesView = findViewById<NESView>(R.id.nes_view)

// Load the game file (.nes) from a data stream (e.g., from assets)
val romInputStream = assets.open("super_mario.nes")
val romBytes = romInputStream.readBytes()
romInputStream.close()

if (nesView.load(romBytes)) {
    // Successful initialization, start the emulator loop
    nesView.start()
}
```

## Input Handling (Controls):
Gameplay controls are managed by passing bitmasks of the controller state using the tick method or built-in listeners:
```kotlin
// Example of passing pressed buttons state (NESKey)
val padState = NESKey.BIT_A or NESKey.BIT_START
nesView.tick(padState)
```
## Donate
USDT TRC20 : THR5VLuZWn7z8iiXwJ1WK9Sg4E6Ae8qUmk

USDT ERC20 : 0x4b5fAF36e95918AD3e65bF9bd42E1FDEca93db2e

Bitcoin BTC : 1AqcSbBw1UDULszboqi8mq8jE7Kykwwncm

BTC TrustWallet: bc1qvlzld7nh7jnjmtvkgj4fn9wwvm98f9c07sc006

## License
This project is licensed under the GPLv3 (GNU General Public License v3). The full license text is available in the LICENSE file.


