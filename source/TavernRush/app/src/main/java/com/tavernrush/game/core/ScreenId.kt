package com.tavernrush.game.core

/** Экраны игры (машина состояний в core/Game). */
enum class ScreenId {
    SPLASH,     // заставка (≈1.5 с, тап — пропустить)
    MENU,       // главное меню
    RUN,        // забег
    CARD_PICK,  // выбор 1 из 3 карт (уровень, сундук, привал в таверне) — оверлей над забегом
    ROUTE_MAP, // карта на полной паузе, без телепортации
    PAUSE,      // пауза — оверлей над забегом
    RESULTS,    // смерть/победа: итоги и награда
    HUB,        // таверна-хаб: постоянные улучшения
    HEROES,     // выбор стиля игры
    SETTINGS,   // графика, управление, звук
    TEAM,       // друзья-существа: команда, режим, развитие
    CONTROLS,   // левое управление: положение, размер и отклик
    JOURNEY,HOME,WORKSHOP,LINEAGE,TRIALS,ATLAS,SANCTUARY,COMPOSITIONS,EVOLUTION,HYBRIDS,
    PRACTICE    // безопасная тренировка без экономики и статистики
}
