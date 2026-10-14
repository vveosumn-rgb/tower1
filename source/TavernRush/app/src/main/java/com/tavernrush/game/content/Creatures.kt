package com.tavernrush.game.content

/** Внешний вид существа: ключ модели в render/pixel/ModelLibrary. */
enum class CreatureLook { STREAM, LYNX, HORN, MOSS, CONFLUENCE }

/** Характер: небольшие различия в поведении, а не «+10% ко всему». */
class TraitDef(val id: String, val name: String, val desc: String)

/**
 * Вид существа-друга. Первая версия — три разных «глагола»: намочить (Ручьеглот), ударить разрядом (Грозорыс),
 * защитить (Камнерог). Это проектные образы по CREATURES_V3, а не окончательный каталог.
 */
class CreatureDef(
    val id: String,
    val name: String,
    val look: CreatureLook,
    val element: Element,
    /** Одна строка: чем он полезен. */
    val role: String,
    /** Что делает сам (способность) и что по приказу. */
    val ability: String,
    val order: String,
    /** Подпись кнопки приказа. */
    val orderLabel: String,
    /** Как он знакомится: показывается в баннере и в журнале. */
    val meeting: String,
    val color: Int,
    val names: Array<String>,
    /** Враги, которые его обижают во время встречи (id из Enemies). */
    val tormentors: Array<String>,
    /** Две специализации после практики: [название, описание]. */
    val specs: Array<Array<String>>,
) {
    var wild = true
    var index = -1
        internal set
}

object Creatures {
    val traits = arrayOf(
        TraitDef("bold", "Смелый", "Работает дальше от героя и бьёт на 15% дальше"),
        TraitDef("careful", "Осторожный", "Держится рядом, крепче и встаёт быстрее"),
        TraitDef("lively", "Задорный", "Способность готова на 10% быстрее"),
    )

    fun all(): Array<CreatureDef> = arrayOf(
        CreatureDef(
            id = "stream", name = "Ручьеглот", look = CreatureLook.STREAM, element = Element.WATER,
            role = "Обливает врагов: они проводят разряд, мёрзнут и дают пар",
            ability = "Струя воды по ближайшему сухому врагу", order = "Приливная волна: обливает всех вокруг и отталкивает",
            orderLabel = "Волна",
            meeting = "Его заводь пересохла, а крысы вытаптывают берег. Защитите его и не торопите.",
            color = 0xFF4FC3E8.toInt(),
            names = arrayOf("Булька", "Капля", "Плюх", "Ручеёк", "Квакша", "Брызга"),
            tormentors = arrayOf("rat", "slime"),
            specs = arrayOf(
                arrayOf("Широкий поток", "Струя в 1,8 раза шире, слабее бьёт"),
                arrayOf("Тонкая струя", "Бьёт дальше и толкает сильнее"),
            ),
        ),
        CreatureDef(
            id = "lynx", name = "Грозорыс", look = CreatureLook.LYNX, element = Element.SHOCK,
            role = "Копит заряд от бега и прыгает на облитых врагов",
            ability = "Прыжок-разряд: копит заряд, пока герой бежит", order = "Перегрузка: прыжок сейчас, сильнее обычного",
            orderLabel = "Разряд",
            meeting = "Гоблины загнали его в угол сетью. Он злится, но к вам присматривается.",
            color = 0xFFB59CFF.toInt(),
            names = arrayOf("Искра", "Громка", "Шорох", "Мурлыка", "Зарница", "Трескун"),
            tormentors = arrayOf("goblin", "goblin", "rat"),
            specs = arrayOf(
                arrayOf("Точный разряд", "Один мощный удар вместо дуг"),
                arrayOf("Цепной разряд", "Дуги достают ещё двоих"),
            ),
        ),
        CreatureDef(
            id = "horn", name = "Камнерог", look = CreatureLook.HORN, element = Element.HEAVY,
            role = "Принимает удары за героя и отвечает тараном",
            ability = "Щит: гасит три удара подряд и бьёт в ответ", order = "Стена: щит заряжается полностью, удар вокруг",
            orderLabel = "Стена",
            meeting = "Орки обложили его, а он стоит и ждёт. Он не нападает — он проверяет, кто вы.",
            color = 0xFFB08D6A.toInt(),
            names = arrayOf("Булыжник", "Гранитка", "Валун", "Кремень", "Топтыга", "Утёс"),
            tormentors = arrayOf("orc", "goblin"),
            specs = arrayOf(
                arrayOf("Заслон", "Щит гасит четыре удара, а не три"),
                arrayOf("Таран", "Контрудар шире и оглушает дольше"),
            ),
        ),
        CreatureDef(
            id="moss",name="Мохокрыл",look=CreatureLook.MOSS,element=Element.NONE,
            role="Раскрывает скрытые следы и создаёт короткое защитное окно",
            ability="При угрозе создаёт неподвижный полог: урон внутри меньше, за пределами защиты нет",
            order="Полог в выбранной позиции и поиск скрытого следа поблизости",orderLabel="Полог",
            meeting="После возвращения воды восстановите заросли и спокойно наблюдайте: он приблизится сам.",
            color=0xFF9CDC86.toInt(),names=arrayOf("Листик","Моха","Тихоня","Шелест","Пушинка","Росток"),
            tormentors=arrayOf("rat","goblin"),
            specs=arrayOf(arrayOf("Защитный полог","Зона шире и держится дольше"),arrayOf("Живые следы","Поиск следа достаёт дальше")),
        ),
        CreatureDef(
            id="confluence",name="Пороговик",look=CreatureLook.CONFLUENCE,element=Element.WATER,
            role="Копит напор: потратить на толчок или одноразовую местную защиту",
            ability="После накопления напора короткая волна сдвигает и мочит близких врагов",
            order="Потратить напор на волну и опору: один удар рядом, не постоянная броня",orderLabel="Опора",
            meeting="Ручная фантастическая линия Ручьеглота и Камнерога, открытая совместной помощью миру.",
            color=0xff72b6a5.toInt(),names=arrayOf("Порог","Шлюзик","Бережок","Перекат","Уступ","Плотик"),
            tormentors=emptyArray(),specs=arrayOf(arrayOf("Береговая опора","Шире волна и дольше однократная защита; слабее удар"),arrayOf("Быстрый напор","Уже волна, сильнее толчок; защита короче")),
        ).also{it.wild=false},
    ).also { for (i in it.indices) it[i].index = i }

    /** Практика (реакции, вызванные существом), после которой открывается выбор специализации. */
    const val SPEC_PRACTICE = 6

    /** Режимы команды: сравниваются на плейтестах (PLAYTEST_FLOW). */
    const val MODE_SOLO = 0
    const val MODE_SWAP = 1
    const val MODE_DUO = 2
    val modeNames = arrayOf("Один спутник", "Ведущий и резерв", "Двое сразу")
    val modeHints = arrayOf(
        "Один друг рядом. Кнопка приказа даёт ему особый приём.",
        "Один друг в бою, второй отдыхает. Кнопка меняет их местами.",
        "Оба друга действуют сразу, приказов нет.",
    )
    const val MODES = 3
}
