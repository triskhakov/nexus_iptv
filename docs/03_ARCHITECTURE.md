# 3. Архитектурная документация

Проект построен по принципам **Clean Architecture**, **SOLID**, **MVVM + MVI** и разделён на **19 независимых Gradle-модулей**:
- **Слой ядра (`:core:*`):**
  - `:core:common` — типизированная монада `NexusResult`, иерархия ошибок `NexusException`, маскировщик секретов `UrlRedactor`, динамический балансировщик ОЗУ `DeviceResourceBudgetOptimizer`.
  - `:core:model` — неизменяемые доменные модели тем, навигации, форм-факторов и карточек.
  - `:core:security` — криптографическое хранилище `AndroidKeyStoreCredentialVault` (`AES-256-GCM`).
  - `:core:database` — локальная БД Room v3 (7 сущностей, полнотекстовый индекс `@Fts4`, пакетная вставка транзакциями по 500 записей).
  - `:core:datastore` — реактивное хранилище пользовательских настроек `Preferences DataStore`.
  - `:core:network` — потоковый HTTP/GZIP-клиент OkHttp и клиент `Xtream Codes API`.
  - `:core:player` — контракт `VideoPlayerEngine` и реализация `Media3VideoPlayerEngine` (`ExoPlayer 1.4.1`), `ExternalPlayerLauncher`, `NexusVideoPlayerOverlay`.
  - `:core:stream-engine` — супервизор `SmartStreamEngine` (контроль буфера, экспоненциальный реконнект, автопереключение резервных потоков `Failover`, расчёт `ChannelHealthScore`).
  - `:core:ui-tv` и `:core:ui-mobile` — специализированные UI-компоненты для пульта D-Pad и сенсорного управления.
- **Функциональные модули (`:feature:*`):** `sources`, `livetv`, `epg`, `vod`, `dvr`, `multiview`, `diagnostics`, `settings`.
- **Интеграционный модуль (`:app`):** `NexusAppCoordinatorViewModel`, `MainActivity`, автодетектор устройства `DeviceFormFactorDetector` и тема `NexusIptvTheme`.
