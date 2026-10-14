# Дополнение R13

r13-hook-{ready,travel,arrival}-{size}: обычное касание существующего активного приёма при удержании левого стика. Готовность, линия перелёта, прибытие. Три прежних размера, native Robolectric, сцена подготовлена фикстурой; не честный поход/человеческая приёмка.

# Дополнение R12

r12-keeper-{3,4,5}-phase-{0,1}-{size}: три новых хранителя и предупреждения ранней/средней фазы. r12-strike-{3,4,5}-{size}: ударная поза после предупреждения. r12-root-gap-{size}: действующая волна с разрывом. r12-guide-{size}: пятая страница атласа. Размеры 360×640 / 1080×2340 / 1200×1920. Кадры native Robolectric, позиции/фазы выставлены для изоляции отображения, не доказательство честного боя. Рендер и геометрия не являются человеческой художественной приёмкой.

# Дополнение R11

`r11-map-0..5-*` — шесть схем на трёх размерах; `r11-pause-360x640` — вход карты в паузе; `r11-ground-0..5-360x640` — стены в реальном рендерере. Кадры unit/UI-фитстур: камера/позиция подготовлены, не полноценное прохождение. Автоматическая геометрия/PNG не являются художественной или физической приёмкой.

# SCREENSHOT_INDEX — какой кадр что показывает

Кадры (PNG) рисуют визуальные тесты Robolectric и пишут в `screenshots/` (каталог в `.gitignore`). Всего **103**: 95 нумерованных игровых + 8 листов арта. Размеры экранов: телефон 1080×2340 (основной), малый 720×1280, QHD 1440×3200, планшет 1600×2560. В архиве передачи те же кадры лежат в `screenshots/frames/` как JPEG **половинного разрешения** (чтобы архив не раздувался); обзорные листы — `screenshots/sheets/`. Полноразмерные PNG даёт любой прогон тестов.

Перегенерировать: `./gradlew testDebugUnitTest` (или `scripts/verify.sh <ЭТАП>`).

## R01–R02 (31 кадр, `ScreenshotTest`)

| Кадры | Что |
|---|---|
| `01_splash` … `12_menu_return` | заставка, меню, начало забега, карты, забег на 1-й минуте и поздно, пауза, смерть, итоги, таверна, покупка, возврат в меню |
| `13_small_a_run`, `13_small_b_cards` | то же на малом экране 720×1280 |
| `14_qhd_a_run`, `14_qhd_b_cards` | QHD 1440×3200 |
| `15_tablet_a_run`, `15_tablet_b_cards`, `15_tablet_c_results` | планшет 1600×2560 |
| `16_heroes`, `17_hero_sage_selected`, `23_heroes_small`, `24_ranger_menu_small` | выбор героя, чародейка, малый экран, следопыт в меню |
| `18_new_arsenal_run` | новое оружие в бою |
| `19_evolution_offer_small`, `21_evolved_run_small` | предложение эволюции и эволюционировавшее оружие |
| `20_reroll_small`, `22_jackpot_qhd` | переброс карт, джекпот |
| `25_guardian_tablet`, `26_guardian_arsenal_tablet` | страж на планшете |
| `27_emergency_heal_small` | экстренная лечебная карта |

## R03 (30 кадров, `R03VisualTest`)

| Кадры | Что |
|---|---|
| `41a`–`41d_rat_king_*` | Король Крыс: вход, телеграф рывка, после атаки, смерть |
| `42a`–`42d_ogre_chef_*` | Людоед-повар: вход, телеграф удара, после, смерть |
| `43a`–`43d_banshee_*` | Банши: вход, спираль, после, смерть |
| `44_ogre_small` | Людоед на малом экране |
| `45_dash_trail`, `45b_dash_cooldown` | рывок: остаточные образы и перезарядка |
| `46a_nemesis_banner`, `46b_nemesis_fight`, `46c_results_nemesis` | немезида: баннер, бой, итоги после гибели |
| `47_roles_and_affixes`, `47b_roles_telegraphs` | роли врагов и аффиксы элит, телеграфы |
| `48a_rat_king_tablet`, `48b_banshee_small`, `48c_roles_small`, `48d_roles_tablet` | новый контент на планшете и малом экране |
| `50_preset_low` … `53_preset_ultra` | четыре пресета графики на одной сцене |
| `55_hud_fixed_stick_left_dash`, `56_settings` | фиксированный стик и кнопка рывка слева; экран настроек |

## R04 (34 кадра, `R04VisualTest`)

| Кадры | Что |
|---|---|
| `60a_wet_group`, `60b_reaction_conduct` | облитая группа; реакция «Проводимость» |
| `61_reaction_freeze`, `62_reaction_shatter`, `63_reaction_steam` | заморозка, раскол, пар |
| `64_status_normal_wet_burn_frozen` | четыре состояния врага рядом |
| `65a_cards_elements`, `65b_cards_elements_small` | карты со стихией и подсказкой реакции |
| `66a_stream_jet`, `66b_stream_wet_group` | Ручьеглот: струя и облитая группа |
| `66c_lynx_leap`, `66d_lynx_recover_after_conduction` | Грозорыс: прыжок и отдышка после проводимости |
| `66e_horn_bash` | Камнерог: щит и контрудар |
| `67a_encounter_called_far` … `67d_encounter_joined` | встреча: зов издалека, обидчики, доверие, «Новый друг!» |
| `67e_encounter_leaving_soon`, `67f_encounter_left` | предупреждение «скоро уйдёт», баннер ухода |
| `69a_encounter_small`, `69b_encounter_tablet` | встреча на малом экране и планшете |
| `70a_hud_swap_mode`, `70b_after_swap`, `70c_hud_duo_small`, `70d_hud_solo_order` | HUD трёх режимов команды |
| `71a_menu_no_friends`, `71b_team_empty`, `71c_menu_friends`, `71d_team_two_friends`, `71e_team_after_choices` | меню и экран «Друзья таверны» |
| `72a_menu_small_friends`, `72b_team_small` | то же на малом экране |
| `73a_results_friend`, `73b_results_friend_small` | итоги с новым другом и реакциями |

## Листы арта (8, `ArtSheetTest`)

`art_heroes`, `art_enemies`, `art_enemies2`, `art_bosses`, `art_bosses_attack`, `art_creatures`, `art_creatures_act`, `art_props` — запечённые модели в увеличении (ходьба/покой/атака).
