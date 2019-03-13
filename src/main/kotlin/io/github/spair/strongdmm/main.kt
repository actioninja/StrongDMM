package io.github.spair.strongdmm

import io.github.spair.strongdmm.gui.PrimaryFrame
import io.github.spair.strongdmm.gui.controller.MapCanvasController
import io.github.spair.strongdmm.gui.controller.MenuBarController
import io.github.spair.strongdmm.gui.controller.ObjectTreeController
import io.github.spair.strongdmm.gui.view.*
import io.github.spair.strongdmm.logic.Environment
import io.github.spair.strongdmm.logic.render.MapDrawerGL
import org.kodein.di.Kodein
import org.kodein.di.direct
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance
import org.kodein.di.erased.singleton

// Entry point
fun main() {
    primaryFrame().init()
}

// Application DI context
val DI = Kodein {
    bind() from singleton { PrimaryFrame() }

    // Subviews
    bind() from singleton { MenuBarView() }
    bind() from singleton { LeftScreenView() }
    bind() from singleton { ObjectTreeView() }
    bind() from singleton { RightScreenView() }
    bind() from singleton { MapCanvasView() }

    // Controllers
    bind() from singleton { MenuBarController() }
    bind() from singleton { ObjectTreeController() }
    bind() from singleton { MapCanvasController() }

    // Logic
    bind() from singleton { Environment() }
}

fun primaryFrame() = DI.direct.instance<PrimaryFrame>()
