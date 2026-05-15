<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="AetherWave Logo" width="120" />
  <h1>AetherWave Music Player 🌊</h1>
  <p><strong>A Premium, Audiophile-Grade Local Music Player for Android</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Kotlin-100%25-blue?style=for-the-badge&logo=kotlin" alt="Kotlin" />
    <img src="https://img.shields.io/badge/C++-Native_DSP-purple?style=for-the-badge&logo=c%2B%2B" alt="C++" />
    <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-green?style=for-the-badge&logo=jetpackcompose" alt="Compose" />
  </p>
</div>

---

**AetherWave** is not just another music player. It is a meticulously crafted audio engine wrapped in a breathtaking, fluid Material 3 interface. Built for true audiophiles, it extracts every ounce of detail from your high-resolution audio files while providing a seamless, visually stunning experience.

Developed with ❤️ by [Aniket Kumar](https://github.com/0xANIKET0x).

## 🎛️ The Engine: SONIC Forge & Audiophile Mode

At the heart of AetherWave lies **SONIC Forge**, our custom-built native C++ Digital Signal Processing (DSP) engine. Unlike standard Android audio players that rely on the system's heavily resampled media framework, SONIC Forge processes audio at the lowest possible level.

✨ **Audiophile Mode**
- **Bit-Perfect Playback**: Bypasses standard Android resampling to deliver pure, untouched audio directly to your DAC.
- **High-Res Audio Support**: Flawless decoding for FLAC, ALAC, WAV, and high-bitrate formats.
- **Dynamic Bitrate Scaling**: Intelligently handles extreme sample rates and deep bit depths without stuttering.
- **Ultra-Low Latency**: Engineered via Native C++ (Oboe/AAudio) to ensure zero dropouts even under heavy device load.

## 🎨 A Masterpiece of UI/UX

AetherWave's interface is designed to be as premium as its sound.
- **Dynamic Material 3**: Glassmorphism, smooth gradients, and micro-animations that make the app feel alive.
- **Collapsing Hero Headers**: A stunning settings and library interface that scales gracefully as you scroll.
- **Theme Engine**: Choose from curated palettes including *Midnight, Emerald, Lavender, and Light*, all featuring beautifully animated theme swatches.
- **Scrollytelling Lyrics**: Fluid, synchronized lyric displays that immerse you in the music.

## ⚙️ Power User Features

- **Granular Folder Management**: Manually include or exclude specific directories. No more WhatsApp voice notes in your music library!
- **State Persistence**: Perfect queue retention. Close the app, restart your phone, and AetherWave resumes exactly where you left off—down to the exact millisecond.
- **Full Backup & Restore**: Export your themes, playlists, customized audio settings, and cached covers into a portable `.zip` archive. Restore it instantly on any device.

## 🤝 Support the Project

AetherWave is completely free, ad-free, and open-source. Building and maintaining a custom C++ audio engine and premium UI takes hundreds of hours of work. 

If you love the app and want to support its continued development, consider buying me a coffee!

💖 **Support via Patreon (Global)**  
[Become a Patron and join the AetherWave community!](https://www.patreon.com/posts/aetherwave-158326210)

🇮🇳 **Support via UPI (India)**  
UPI ID: `aniketkumar00123@okicici`  
*(Or simply tap the "Support via UPI" button directly inside the app's Settings!)*

## 🛠️ Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/0xANIKET0x/AetherWave-Music-Player.git
   ```
2. Open the project in **Android Studio**.
3. Ensure you have the **NDK** and **CMake** installed via the SDK Manager (required for compiling the SONIC Forge native engine).
4. Sync the Gradle project and hit Run!

## 📜 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
