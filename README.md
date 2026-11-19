# NEXUS IPTV v1.0.0 — Next-Generation IPTV Player for Android, Android TV & Google TV

**NEXUS IPTV** — коммерческий многомодульный IPTV-плеер нового поколения для **Android TV**, **Google TV**, **Android TV Box**, **смартфонов** и **планшетов** на базе **Kotlin 2.0**, **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)** и **Clean Architecture**.

## Содержание документации (Раздел 23 ТЗ)

1. [Инструкция по установке и сборке APK](docs/01_INSTALLATION_AND_BUILD.md)
2. [Руководство пользователя по настройке](docs/02_USER_SETUP_GUIDE.md)
3. [Архитектурная документация](docs/03_ARCHITECTURE.md)
4. [Перечень реализованных функций и ограничений](docs/04_FEATURES_AND_LIMITATIONS.md)
5. [Отчёт о тестировании и план дальнейшего развития](docs/05_TESTING_AND_ROADMAP.md)

## Быстрая загрузка на GitHub и автоматическая сборка APK

1. Распакуйте данный архив в локальную папку `nexus-iptv`.
2. Выполните публикацию в свой репозиторий GitHub:
   ```bash
   git init
   git add .
   git commit -m "release: NEXUS IPTV v1.0.0 complete 19-module architecture"
   git branch -M main
   git remote add origin https://github.com/<ваш-логин>/nexus-iptv.git
   git push -u origin main
   ```
3. Откройте вкладку **Actions -> NEXUS IPTV Build & Release APK** в GitHub: система автоматически прогонит все модульные тесты и соберёт готовые установочные файлы:
   - `nexus-iptv-universal-release.apk` (универсальный APK для любых устройств);
   - `nexus-iptv-arm64-v8a-release.apk` (для современных смартфонов и 64-битных TV Box);
   - `nexus-iptv-armeabi-v7a-release.apk` (для 32-битных Android TV Box с 2 ГБ ОЗУ).
