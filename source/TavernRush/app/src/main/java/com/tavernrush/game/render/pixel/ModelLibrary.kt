package com.tavernrush.game.render.pixel

import com.tavernrush.game.content.CreatureLook
import com.tavernrush.game.content.EnemyLook
import com.tavernrush.game.render.pixel.models.BossModels
import com.tavernrush.game.render.pixel.models.CreatureModels
import com.tavernrush.game.render.pixel.models.EnemyModels
import com.tavernrush.game.render.pixel.models.HeroModels

/** Единый реестр моделей: новый EnemyLook без модели не скомпилируется (when без else). */
object ModelLibrary {
    fun hero(index: Int): Model = when (index) {
        1 -> HeroModels.guardian()
        2 -> HeroModels.sage()
        3 -> HeroModels.ranger()
        else -> HeroModels.innkeeper()
    }

    fun creature(look: CreatureLook): Model = when (look) {
        CreatureLook.STREAM -> CreatureModels.stream()
        CreatureLook.LYNX -> CreatureModels.lynx()
        CreatureLook.HORN -> CreatureModels.horn()
        CreatureLook.MOSS -> CreatureModels.moss()
        CreatureLook.CONFLUENCE -> CreatureModels.confluence()
    }

    fun enemy(look: EnemyLook): Model = when (look) {
        EnemyLook.RAT -> EnemyModels.rat()
        EnemyLook.GOBLIN -> EnemyModels.goblin()
        EnemyLook.BAT -> EnemyModels.bat()
        EnemyLook.SLIME -> EnemyModels.slime()
        EnemyLook.ORC -> EnemyModels.orc()
        EnemyLook.ARCHER -> EnemyModels.archer()
        EnemyLook.BOAR -> EnemyModels.boar()
        EnemyLook.BOMBER -> EnemyModels.bomber()
        EnemyLook.SHAMAN -> EnemyModels.shaman()
        EnemyLook.GHOST -> EnemyModels.ghost()
        EnemyLook.SPIDER -> EnemyModels.spider()
        EnemyLook.SLIMELET -> EnemyModels.slime(scale = 0.92f, foot = 4f, id = "slimelet")
        EnemyLook.RAT_KING -> BossModels.ratKing()
        EnemyLook.OGRE_CHEF -> BossModels.ogreChef()
        EnemyLook.BANSHEE -> BossModels.banshee()
        EnemyLook.STORM_HERON -> com.tavernrush.game.render.pixel.models.RegionBossModels.stormHeron()
        EnemyLook.ROOTBACK -> com.tavernrush.game.render.pixel.models.RegionBossModels.rootback()
        EnemyLook.PRISM_CRAB -> com.tavernrush.game.render.pixel.models.RegionBossModels.prismCrab()
    }
}
