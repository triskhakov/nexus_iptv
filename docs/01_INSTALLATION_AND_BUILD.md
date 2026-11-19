# 1. Инструкция по установке и сборке APK

## Системные требования
- **ОС:** Android 7.0 (API 24) и выше, Android TV 7.0+, Google TV.
- **ОЗУ:** от 2 ГБ RAM (встроен автоматический профиль экономии памяти `DeviceResourceBudgetOptimizer`).
- **Архитектуры CPU:** `arm64-v8a`, `armeabi-v7a`, `x86_64`.

## Сборка в Android Studio / через командную строку
1. Откройте корневую папку проекта в **Android Studio Ladybug (2024.2+)** с **JDK 17**.
2. Дождитесь завершения синхронизации Gradle (19 модулей).
3. Для сборки подписанного/тестового APK выполните:
   ```bash
   ./gradlew assembleRelease
   ```
4. Готовые APK-файлы будут сохранены в каталоге `app/build/outputs/apk/release/`.

## Установка на Android TV / Google TV / Android TV Box
- **Способ 1 (USB-накопитель):** скопируйте `nexus-iptv-universal-release.apk` на флешку, подключите к телевизору/приставке и откройте через любой файловый менеджер (разрешив установку из неизвестных источников).
- **Способ 2 (ADB по сети):**
  ```bash
  adb connect <IP-адрес-приставки>:5555
  adb install -r app/build/outputs/apk/release/nexus-iptv-universal-release.apk
  ```
