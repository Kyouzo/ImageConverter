# Image Converter (Android)

A lightweight, fully on-device image-format converter. Pick a photo with the
Android Photo Picker, choose an output format, convert, then save to your
gallery or share — nothing is uploaded, no account, no network.

Built with **Kotlin + Jetpack Compose (Material 3) + MVVM + Coroutines**.

- Package: `dev.kyouzo.imageconverter`
- `minSdk` 24, `targetSdk`/`compileSdk` 37, AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6

---

## Build & run

This is a standard Android Studio project.

```bash
# from the project root (Z:\ImageConverter)
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # run the JVM unit tests
```

Or open the folder in Android Studio and press Run.

> **Note on how this project was generated.** The code was written and the pure
> conversion/logic layer was compiled and unit-tested on a Linux build host, but
> the full Android/Gradle build itself could **not** be run there because the
> Android SDK (platform 37, build-tools) and the Google/Maven artifact servers
> were not reachable from that environment. Run `./gradlew assembleDebug` once on
> your machine (your SDK is at `Z:\AndroidStudioSdk`) to produce the APK. The
> logic layer — format detection, the BMP/TGA/ICO codecs, filename handling,
> capability registry, and validation — is covered by unit tests that passed
> (88 assertions); see `app/src/test/`.

---

## How image selection works (Photo Picker, not a file manager)

Selection uses the AndroidX **Photo Picker** via
`ActivityResultContracts.PickVisualMedia` restricted to `ImageOnly`
(`engine/ImagePickerManager.kt`, wired in `ui/ConverterScreen.kt`). This opens
the system Photos / Photo Picker UI and returns a readable `content://` URI.

- No `ACTION_OPEN_DOCUMENT` / `ACTION_GET_CONTENT` / file-browser intent.
- No `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` permission is requested.
- On devices without the modern platform picker, the AndroidX library falls back
  to the bundled/Play-services photo picker automatically — never a file browser.

---

## Supported format matrix

The app advertises only what it can actually do. Capabilities are computed at
runtime (`format/ImageFormatRegistry.kt`) because HEIC/AVIF decoding depends on
the OS version. Output formats that cannot be encoded are shown in the selector
marked `✕`; tapping one explains why instead of producing a broken file.

| Format   | Decode (input)                    | Encode (output)                  | Engine |
|----------|-----------------------------------|----------------------------------|--------|
| JPEG     | ✅                                | ✅ quality 1–100                 | Platform |
| PNG      | ✅                                | ✅ (lossless)                    | Platform |
| WebP     | ✅ (first frame if animated)      | ✅ quality; lossless at 100      | Platform |
| GIF      | ✅ (first frame only)             | ❌ (palette+LZW not in MVP)      | Platform decode |
| BMP      | ✅                                | ✅ 24/32-bit                     | Platform decode / **bundled** encode |
| WBMP     | ✅                                | ❌                               | Platform decode |
| HEIC     | ✅ Android 9+ (API 28)            | ❌ (no Bitmap.compress HEIC)     | Platform |
| HEIF     | ✅ Android 9+ (API 28)            | ❌                               | Platform |
| AVIF     | ✅ Android 12+ (API 31)           | ❌ (no Bitmap.compress AVIF)     | Platform |
| TGA      | ✅ (type 2 & RLE, 24/32-bit)      | ✅ 24/32-bit                     | **Bundled pure-Kotlin** |
| ICO      | ✅ (largest entry; PNG or DIB)    | ❌                               | **Bundled pure-Kotlin** + platform PNG |
| TIFF     | ❌                                | ❌                               | — |
| JPEG XL  | ❌                                | ❌                               | — |

### Behaviour notes
- **Animated GIF/WebP**: only the first frame is converted; animation is lost.
- **Transparency → JPEG**: JPEG has no alpha, so transparent pixels are flattened
  onto a chosen background (White / Black / Gray, default **White**).
- **Transparency is preserved** when the output format supports it (PNG, WebP,
  BMP-32, TGA-32).
- **Dimensions are preserved**; there is no silent resizing. Very large images
  that exceed available memory produce a clear error rather than being downscaled.
- **Orientation** is respected (ImageDecoder applies EXIF automatically on API
  28+; a manual EXIF rotation path covers API 24–27).
- The output extension always matches the chosen format; the image is never
  silently re-labelled.
- **Deleting the original**: after the converted copy is **successfully saved**
  to the gallery, the app deletes the original photo. On Android 11+ the system
  shows its own confirmation dialog (required — no broad storage permission is
  used; see `engine/OriginalDeleter.kt`). The original is never deleted before
  the save succeeds, and if the specific image can't be located/removed the app
  says so rather than failing. Decline the system prompt and the original is
  kept.

---

## Why not TwelveMonkeys ImageIO?

The brief suggested TwelveMonkeys as a possible codec library. **It is not
Android-compatible**: it is built on `javax.imageio` / `java.awt`, which do not
exist on Android. Adding it would compile against the desktop JRE but fail at
runtime. Rather than pretend universal support exists, this MVP uses:

- **Android platform codecs** (`BitmapFactory` / `ImageDecoder` / `Bitmap.compress`)
  for the formats Android genuinely supports; and
- **small, dependency-free pure-Kotlin codecs** for BMP, TGA and ICO
  (`format/codec/`), which are fully unit-tested by round-trip.

TIFF and JPEG XL have no Android-compatible, AWT-free codec bundled, so they are
honestly reported as unsupported. They could be added later via an NDK codec
(e.g. libtiff / libjxl) behind the same `ImageFormatRegistry` capability layer.

---

## Architecture

```
format/            Pure JVM logic (no Android deps) — unit tested on host
  ImageFormat            formats, extensions, mime types, alpha/lossy flags
  FormatSignatures       magic-byte detection (true format, not extension)
  ImageFormatRegistry    decode/encode capability per device (API-aware)
  FileNaming             base name, extension swap, unique-name generation
  PixelImage/ColorUtils  device-independent raster + alpha flatten
  ConversionValidation   request validation, defaults
  codec/                 pure-Kotlin BmpCodec, TgaCodec, IcoCodec

engine/            Android glue
  ImagePickerManager     Photo Picker request
  ImageMetadataReader    name/size/dimensions/true-format from a URI
  AndroidImageDecoder    URI -> ARGB_8888 Bitmap (platform + custom, orientation)
  AndroidImageEncoder    Bitmap -> bytes (platform compress + custom codecs)
  BitmapConversions      Bitmap <-> PixelImage, JPEG background flatten
  ImageConverter         decode -> flatten -> encode -> temp file -> verify
  ImageSaver             MediaStore save to Pictures/ImageConverter/
  ImageSharer            FileProvider + Sharesheet

vm/ConverterViewModel    UI state, validation, background conversion jobs
ui/                      Compose screen, components, Material 3 theme
```

### Storage & permissions
- Save target: `Pictures/ImageConverter/` via `MediaStore`.
- API 29+: scoped storage, **no permission**.
- API 24–28: `WRITE_EXTERNAL_STORAGE` (declared `maxSdkVersion="28"`), requested
  at runtime only when saving.
- Sharing uses a `FileProvider` (`${applicationId}.fileprovider`) with temporary
  read permission; originals are never overwritten (unique names are generated).

---

## Tests

`app/src/test/` (JVM unit tests — no device needed):
- `FormatDetectionTest` — extension mapping + magic-byte detection
- `FileNamingTest` — base names, extension swap, collision handling
- `RegistryTest` — decode/encode capabilities and API gating
- `CodecTest` — BMP/TGA round-trips, ICO directory parsing + DIB decode
- `ValidationTest` — request validation, defaults, JPEG background flatten

Run with `./gradlew testDebugUnitTest`.

> `app/src/main/java/dev/kyouzo/imageconverter/format/Probe.kt` is a leftover
> placeholder from project setup and can be deleted.
