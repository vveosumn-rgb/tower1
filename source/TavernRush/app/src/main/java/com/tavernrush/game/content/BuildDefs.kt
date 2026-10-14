package com.tavernrush.game.content

class HeroDef(
    val id: String, val name: String, val weaponId: String, val trait: String,
    val color: Int, val bonuses: FloatArray,
) {
    var index = -1
        internal set
    var weaponIndex = -1
        internal set
}

/** Связка включается с первым уровнем пассивки; эволюция — на максимуме оружия. */
class EvolutionDef(
    val weaponId: String, val passiveId: String, val passiveLevel: Int,
    val name: String, val desc: String,
    val might: Float = 1.5f, val cooldown: Float = 0.75f, val amount: Int = 0,
    val area: Float = 1.25f, val duration: Float = 1f, val chains: Int = 0,
) {
    var weaponIndex = -1
        internal set
    var passiveIndex = -1
        internal set
}