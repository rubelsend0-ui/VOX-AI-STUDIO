# VOX AI STUDIO

Personal AI Voice Cloning, Multilingual Text-to-Speech, and Dubbing Studio built for smartphone-only Termux development, Render cloud hosting, and GitHub Actions APK compilation.

## Architecture
- **Android Client (`android/`)**: Kotlin, Jetpack Compose, Material 3 (AMOLED Glassmorphism), MVVM, Retrofit, Room Cache, Media3 ExoPlayer.
- **Backend API (`backend/`)**: Node.js, Express, TypeScript, Prisma ORM, PostgreSQL, FFmpeg, S3-compatible persistent object storage.
- **AI Engine Abstraction**: Supports external GPU-hosted XTTS-v2 inference servers (`AI_PROVIDER=xtts`) or external cloud voice cloning APIs (`AI_PROVIDER=elevenlabs`).
