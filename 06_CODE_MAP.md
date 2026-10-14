# Дополнение R13

HeroAction.findHookAnchor/counterReady — одинаковая чистая проверка направления/окна/пути; useHook — приоритет контратаки перед ориентиром; finishHook — одно попадание по исходному UID. HeroAction.update — sweep и отмена удара при остановке. WorldRenderer — линия к фиксированному концу крюка. HudScreen — подсказка на прежней кнопке. hookHits — только счётчик фактического урона текущего похода, не новая валюта или ключ профиля.

# Дополнение R12

RegionalBosses — три конечных автомата атак и короткая подсказка фактического действия; BossSystem — общий переход фаз/отмена опасностей. RegionBossModels — три оригинальные анатомии и клипы. Hazard.inGap/HazardSystem/WorldRenderer — общий разрыв волны, сброс пула. RegionMap.rayLength — обрезка новых линейных атак вместе с телеграфом. BotPilot учитывает реальную ближнюю дальность и не убегает постоянно от уже доступной неподвижной цели; это диагностический бот, не автопрохождение для игрока. HudScreen не накладывает длинное сообщение мира на название хранителя. Txt — явные LF/CRLF/пустые строки и максимум 32 строки. AdventureScreens — пятая страница атласа.

# Дополнение R11

RegionMap — авторские координаты/прямоугольники, пересечение отрезка, resolve/sweep. RegionNavigator — переиспользуемый граф видимости. AdventureSystem — логические ID, pin, связанная вода/оборона; World/EnemyAI/CompanionSystem/BotPilot — обход/столкновения. RouteMapScreen — схема без телепорта, Game.ROUTE_MAP — полная пауза с запретом операций вне похода. PauseScreen — дополнительный вход только для экспедиции. Нож/сковорода/EnemyShot проверяют отрезок; остальные эффекты не объявляются полной системой укрытий.

# Дополнение R10

MorphProgress — выбранные боковые формы по UID отдельно от генетики. CreatureForms — геометрия возрастов/форм; SpriteBank — ограниченные параметры и кэш запечённых вариантов. EvolutionScreen — сравнение и явный выбор. AdventureProgress.hybrid — одна ручная фантастическая линия и строгая родословная. HybridScreen — цена, родительская черта, два подтверждения, отмена. CONFLUENCE — оригинальный вид/модель/напор. Game связывает состав и форму с runtime, SaveCodec подписывает morph.state.

# Дополнение R09

- meta/CompanionProgress.kt: UID-пары, уроки, три TeamPreset, строгий decode.
- core/Game.kt: индивидуальная команда, новое совместное открытие, практика резерва, API составов вне похода.
- systems/CompanionSystem.kt: конечное условное эхо и локальная одноразовая защита.
- ui/screens/CompositionScreen.kt: выбор ведущего/резерва/наставника/слота.
- save/SaveCodec.kt: companions.state, совместимость отсутствующего ключа.
- scripts/audit_scope.py: проверяемый инвентарь доступных документов и консервативный остаток, не авто-закрытие.

Ниже историческая карта кода.

# CODE_MAP — карта кода и рецепты «как добавить X»

Пути — относительно `app/src/main/java/com/tavernrush/game/` (тесты — `app/src/test/java/com/tavernrush/game/`). Числа и имена сверены с кодом на `f0c1afb`.

## 1. Потоки и цикл

- `MainActivity` — единственная активность: полноэкранный `SurfaceView` (`platform/GameView`), уход в фон → пауза и страховочная запись награды (`Game.onHostPause`). Запускает фоновый прогрев спрайтов (`SpriteBank.warmUpAsync()`).
- `platform/GameLoop` — поток отрисовки и логики с накопителем времени (`core/FrameClock`); поколения потока (`core/LoopGeneration`) исключают второго писателя.
- `ui/GameApp` — связка «логика ↔ экраны ↔ отрисовка». `update(frameSeconds)` делит кадр на подшаги ≤ 1/60 с (до 6) → `step(dt)` → `Game.tick(dt)` → `World.tick(dt)`. Ввод приходит из UI-потока через `core/InputQueue`. Доступ к состоянию под `synchronized(app)`.
- `core/Game` — машина состояний (`ScreenId`: SPLASH, MENU, RUN, CARD_PICK, PAUSE, RESULTS, HUB, HEROES, SETTINGS, TEAM): `startRun`, `pickCard`, `finishRun`, операции команды (`setTeamMode`, `setTeamRole`, `chooseFriendSpec`), запись нового друга сразу после знакомства.

## 2. Тик мира (`systems/World.kt`, `tick`)

`director.update` (волны, события) → `encounter.update` → `updatePlayer` → `updateEnemies` (движение, роли через `EnemyAI`, боссы через `BossSystem`) → `hash.rebuild` → `separate` → `reactions.update` (состояния, ожог) → `weaponSystem.update` → `team.update` (друзья) → `combat.contactDamage` → `hazardSystem.update` → `compactEnemies` → `pickupSystem.update` → `fx.update` → `updateCamera` → `trackNearMiss`. Хит-стоп, замедленная смерть и победа ветвятся раньше. Порядок важен: тесты и боты детерминированы относительно него.

**Случайность.** `World.reset(seed, …)` заводит потоки `Rng.stream(seed, 1…6)`: spawn, loot, cards, combat, fx, ai. Поток **7** — `EncounterSystem.arm`. Новые случайности — только новым потоком; вызовы в 1–6 не добавлять.

**Лимиты:** `World.MAX_ENEMIES = 400`; частицы — `FxSystem.MAX_PARTICLES = 1000` (в рантайме потолок задаёт пресет графики); очередь сортировки отрисовки `WorldRenderer.SORT_CAP = 900`.

## 3. Пакеты и файлы

| Пакет | Файлы и роль |
|---|---|
| `core/` | `Game`, `ScreenId`, `GameEvents` (интерфейс событий + `Sfx` (29 id) + `Haptic`), `InputQueue`, `FrameClock`, `LoopGeneration`, `Rng`, `MathUtil`, `TextBuf` |
| `content/` | **данные**: `Weapons`, `Arsenal` (12 оружий), `Passives`, `Evolutions`, `Heroes`, `Dishes`, `HubUpgrades`, `Enemies`, `Waves` (10 волн), `Creatures` (3 друга), `Element` (стихии и `Reactions`), `BuildDefs`; типы — `Defs.kt` (`WeaponDef`, `EnemyDef`, `EnemyBehavior`, `EnemyLook`, `WeaponKind`, `IconId` …); реестр и проверки при загрузке — `Content.kt` |
| `entities/` | состояние объектов: `Enemy`, `Player`, `Projectile`, `WeaponInstance`, `Pickup`, `Hazard`, `Mate` (друг в забеге), `Fx.kt` (частицы, свет, числа, молнии, кольца) |
| `systems/` | `World` (всё состояние + тик), `WaveDirector`, `CombatSystem` (урон, смерть, награды), `EnemyAI` (роли), `BossSystem`, `HazardSystem`, `Nemesis`, `ReactionSystem`, `CompanionSystem`, `EncounterSystem`, `CardSystem`, `PickupSystem`, `WeaponSystem` + `weapons/*Behavior.kt` (по одному на `WeaponKind`), `FxSystem`, `SpatialHash`, `PlayerStats`, `RunStats`, `BotPilot` |
| `meta/` | `Profile` (+ `Friend`), `Meta` (экономика: награды, покупки), `RunResult` |
| `save/` | `SaveCodec` (текстовый формат, подпись, валидация), `SaveManager` (файлы, резервная копия), `Signer` (HMAC) |
| `platform/` | `GameView`, `GameLoop`, `AndroidFeedback` (SoundPool + вибрация), `SoundSynth` (процедурные звуки; `VERSION = 3`) |
| `render/` | `WorldRenderer` (весь мир), `Palette`, `Txt` (текст), `Icons`, `Backdrop` (фоны меню/хаба) |
| `render/pixel/` | `Model` (DSL моделей), `ModelBaker` (запекание), `ModelLibrary`, `SpriteBank` (кэш спрайтов + `warmUp`), `Gfx` (пресеты и `GfxGovernor`), `Postfx` (свет, bloom, цветокоррекция), `GroundLayer`, `PixelAssets`, `PixelFont`, `PixelBlit`, `PxColor`; `models/` — `HeroModels`, `EnemyModels`, `BossModels`, `PropModels`, `CreatureModels` |
| `ui/` | `GameApp`, `Screen`, `Button`, `Joystick`, `UiDraw`, `UiMetrics`, `Ease`; `screens/` — `Splash`, `Menu`, `Hud`, `TeamHud`, `Card`, `Pause`, `Results`, `Hub`, `Hero`, `Settings`, `Team` |

**Порядок отрисовки мира** (`WorldRenderer.draw`): земля → облачные тени → реквизит и эффекты на земле → кольцо встречи → тени → пикапы → след рывка → тела с сортировкой по глубине (враги, герой, друзья, существо встречи, реквизит, орбиты) → воздух → снаряды врагов → частицы → кроны → свет → аддитив → bloom → числа урона → постобработка и подача на экран → оверлей (виньетка, вспышка, подписи реакций).

## 4. Рецепты

**Оружие существующего типа.** Допишите `WeaponDef(id, name, desc, kind, icon, color, base, levels, element)` в `content/Weapons.kt` или `Arsenal.kt` (реестр объединяет оба). Иконка — `IconId` в `Defs.kt` и рисование в `render/Icons.kt`. Эволюция — `content/Evolutions.kt`. Сохранение хранит оружие по id — порядок менять можно.

**Оружие нового типа.** Значение в `WeaponKind` (`Defs.kt`) → класс `systems/weapons/XBehavior.kt` (наследник `WeaponBehavior`) → ветка в `WeaponSystem.createBehavior` (там `when` по `WeaponKind`) → отрисовка в `WorldRenderer` (`drawAir`/`drawOrbit` и т. п.) → тест в стиле `ArsenalTest`.

**Обычный враг.** `EnemyDef` в `content/Enemies.kt`; вид — `EnemyLook` (`Defs.kt`) + модель в `render/pixel/models/EnemyModels.kt` и ветка в `ModelLibrary.enemy` (`when` без `else`: без модели не скомпилируется). Прогрев спрайтов обходит все `EnemyLook` сам. Поведение: `CHASE/WAVY/SWARM` — общий код в `World.updateEnemies`; остальные роли — `EnemyBehavior` + ветка в `EnemyAI.update`. Появление — пулы волн в `content/Waves.kt` (`s("id", вес)`).

**Босс.** `EnemyBehavior.BOSS` + логика по id в `systems/BossSystem.kt`; модель в `BossModels.kt` (клипы `walk` и `attack`); событие `WaveEvent(время, BOSS, "id")` в `Waves.kt`; выбор слота — `WaveDirector.pickBoss` (`MID_BOSSES` / `FINAL_BOSSES`). Телеграф каждой атаки обязателен (`ROUND_CONTRACT.md`).

**Друг-существо.** `CreatureDef` в `content/Creatures.kt` (+ `CreatureLook`) → модель в `CreatureModels.kt` (клипы `walk`, `idle`, `act`) и ветка в `ModelLibrary.creature` → константа вида и способность в `systems/CompanionSystem.kt` (`S_*`, `streamAct`/`lynxAct`/`hornAct` как образец, приказ — `order`) → обидчики встречи берутся из `CreatureDef.tormentors` → сохранение подхватывает вид само (`Content.creatures` → ключи `fr.<id>` в `SaveCodec`) → тесты в стиле `Round4CreaturesTest`, кадры в `R04VisualTest`.

**Стихия или реакция.** Значение в `Element`/`Reactions` (`content/Element.kt`), логика в `ReactionSystem.onHit` и `react`; стихию оружию задаёт поле `element` в `WeaponDef`. Подсказки на картах (`Reactions.hint`) и подписи работают автоматически; нужен звук (ниже).

**Звук.** Константа в `Sfx` (`core/GameEvents.kt`, увеличить `COUNT`) → ветка в `SoundSynth.render` → **поднять `SoundSynth.VERSION`** (иначе на устройстве останутся старые WAV из кэша).

**Экран.** Значение в `ScreenId` → класс-наследник `ui/Screen` в `ui/screens/` → строка в `GameApp.screenFor` и `layout()` → переход в `Game` (и `Game.back()`) → кнопка входа (обычно в `MenuScreen`).

**Поле профиля.** Поле в `meta/Profile.kt` (+ `copyFrom`) → ключ в `SaveCodec.reservedKeys`, запись в `encode`, чтение с диапазоном в `decode` → тест в стиле `forgedFriendAndTeamFieldsAreRejected` и тест «старое сохранение без поля читается».

**Карта/волны/события.** `content/Waves.kt` (`WaveDef`, `WaveEvent`, типы `ELITE/SWARM/RING/BOSS/NEMESIS`) и `systems/WaveDirector.kt`.

**Графика.** Модели — DSL `ModelBuilder` (`ell`/`box`/`cyl`/`pair`, теги `Tag.*`, материалы `Mat`, клипы `Anims`); пресеты — `render/pixel/Gfx.kt`. Новые спрайты внести в `SpriteBank.warmUp()`.

## 5. Тесты

| Класс | Что |
|---|---|
| `FoundationTest`, `FrameClockTest`, `LifecycleTest` | ввод, награды, сохранения, часы кадра, жизненный цикл потока |
| `GameplayTest`, `ArsenalTest`, `QuickSimTest` (+ `SimRunner`) | оружие, герои, эволюции, перебросы, симуляции ботов |
| `Round3Test`, `Round4Test`, `Round4CreaturesTest` | логика `World` без Android: роли врагов, боссы, немезида, рывок, реакции, друзья, встреча, команда, сохранения, боты |
| `ControlsTest`, `SpriteWarmUpTest` | управление; прогрев спрайтов |
| `ScreenshotTest` (+ `Rig`), `R03VisualTest`, `R04VisualTest`, `ArtSheetTest` (+ `PngWriter`) | Robolectric (`GraphicsMode.NATIVE`): реальные касания, кадры в `screenshots/` |

Приёмы: в логических тестах мир создаётся `World().also { it.reset(seed, null) }`; вспомогательные `step(w, keep)` (убирает режиссёрских врагов), `spawn(...)`, `armed(seed)` (встреча); визуальные — `ScreenshotTest.Rig(w, h, left, top, right, bottom, seed)` с `advance`, `until`, `tap`, `shot`. Каталог кадров задаёт свойство `screenshots.dir` (см. `app/build.gradle.kts`).

## 6. Где искать при поломке

| Симптом | Сначала смотреть |
|---|---|
| враги ведут себя странно | `EnemyAI`, `World.updateEnemies`, `huntSite` (обидчики встречи) |
| урон/награды | `CombatSystem.hitEnemy`, `killEnemy`, `hurtPlayer` |
| реакции | `ReactionSystem.onHit/react`, состояния в `Enemy` (`wetT`, `burnT`, `frozenT`) |
| друг не помогает | `CompanionSystem.step` и конкретная `*Act`; `Mate.state` |
| встреча не начинается | `EncounterSystem.arm/update`, волна встречи (`plannedWave` 1–2 по индексу), босс на арене закрывает встречу |
| рывки кадра | `WorldRenderer` (аллокации), `SpriteBank.warmUp`, пресет в `Gfx.kt` |
| тест кадров красный | `ScreenshotTest.Rig`, ожидание экрана через `until`, размеры экранов |
| сохранение отвергается | `SaveCodec.decode` (диапазоны), `reservedKeys`, подпись `Signer` |
