package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.EnemyLook
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.systems.World
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Прогрев спрайтов: фоновый поток не должен ни блокировать запросы рендера, ни ломать кэш. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpriteWarmUpTest {
    @Test(timeout = 120_000) fun asyncWarmUpOverlapsWithRenderRequests() {
        SpriteBank.warmUpAsync()
        SpriteBank.warmUpAsync() // повторный запуск — без второго потока
        for (l in EnemyLook.entries) assertTrue("${l.name}: есть кадры", SpriteBank.enemy(l).count > 0)
        assertTrue(SpriteBank.heroWalk.size == SpriteBank.HEROES)
        assertNotNull(SpriteBank.elite(EnemyLook.ORC, World.ELITE_SIZE))
        SpriteBank.warmUp() // дожидаемся остатка прогрева, чтобы фоновый поток не пережил тест
    }

    @Test(timeout = 120_000) fun warmUpCoversEliteAndNamedVariantsOfRegularEnemies() {
        SpriteBank.warmUp()
        for (d in Content.enemies) {
            val base = SpriteBank.enemy(d.look)
            assertSame("${d.id}: кэш обычного вида", base, SpriteBank.enemy(d.look))
            if (!d.boss) {
                val e = SpriteBank.elite(d.look, World.ELITE_SIZE)
                val n = SpriteBank.named(d.look, World.NAMED_SIZE)
                assertSame("${d.id}: элита кэшируется", e, SpriteBank.elite(d.look, World.ELITE_SIZE))
                assertSame("${d.id}: немезида кэшируется", n, SpriteBank.named(d.look, World.NAMED_SIZE))
                assertTrue("${d.id}: элита крупнее обычного", e.frame(0, 0).w > base.frame(0, 0).w)
            }
        }
    }
}
