package io.github.spair.strongdmm.gui.map

import io.github.spair.strongdmm.common.*
import io.github.spair.strongdmm.gui.edit.variables.ViewVariablesDialog
import io.github.spair.strongdmm.gui.instancelist.InstanceListView
import io.github.spair.strongdmm.gui.map.select.SelectOperation
import io.github.spair.strongdmm.gui.objtree.ObjectTreeView
import io.github.spair.strongdmm.logic.action.ActionController
import io.github.spair.strongdmm.logic.action.PlaceTileItemAction
import io.github.spair.strongdmm.logic.action.SwapTileItemAction
import io.github.spair.strongdmm.logic.action.SwitchTileItemsAction
import io.github.spair.strongdmm.logic.dmi.DmiProvider
import io.github.spair.strongdmm.logic.map.*
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.Display
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

fun MapPipeline.openTilePopup() {
    if (xMouseMap == OUT_OF_BOUNDS || yMouseMap == OUT_OF_BOUNDS) {
        return
    }

    MapView.createAndShowTilePopup(Mouse.getX(), Display.getHeight() - Mouse.getY()) { popup ->
        val dmm = selectedMapData!!.dmm
        val tile = dmm.getTile(xMouseMap, yMouseMap) ?: return@createAndShowTilePopup

        SelectOperation.depickAreaIfNotInBounds(xMouseMap, yMouseMap)

        with(popup) {
            addResetActions()
            addSeparator()
            addTileActions(dmm, tile)
            addSeparator()

            if (addOptionalSelectedInstanceActions(dmm, tile)) {
                addSeparator()
            }

            addTileItemsActions(dmm, tile)
        }
    }
}

private fun JPopupMenu.addResetActions() {
    add(JMenuItem("Undo (Ctrl+Z)").apply {
        isEnabled = ActionController.hasUndoActions()
        addActionListener { ActionController.undoAction() }
    })

    add(JMenuItem("Redo (Ctrl+Shift+Z)").apply {
        isEnabled = ActionController.hasRedoActions()
        addActionListener { ActionController.redoAction() }
    })
}

private fun JPopupMenu.addTileActions(map: Dmm, currentTile: Tile) {
    add(JMenuItem("Cut (Ctrl+X)").apply {
        addActionListener {
            ModOperation.cut(map, currentTile.x, currentTile.y)
        }
    })

    add(JMenuItem("Copy (Ctrl+C)").apply {
        addActionListener {
            ModOperation.copy(map, currentTile.x, currentTile.y)
        }
    })

    add(JMenuItem("Paste (Ctrl+V)").apply {
        isEnabled = TileOperation.hasTileInBuffer()
        addActionListener {
            ModOperation.paste(map, currentTile.x, currentTile.y)
        }
    })

    add(JMenuItem("Delete (Del)").apply {
        addActionListener {
            ModOperation.delete(map, currentTile.x, currentTile.y)
        }
    })

    if (SelectOperation.isPickType()) {
        add(JMenuItem("Deselect (Esc)").apply {
            addActionListener {
                SelectOperation.depickArea()
            }
        })
    }
}

private fun JPopupMenu.addOptionalSelectedInstanceActions(map: Dmm, currentTile: Tile): Boolean {
    val selectedInstance = InstanceListView.selectedInstance ?: return false

    val selectedType = when {
        isType(
            selectedInstance.type,
            TYPE_TURF
        ) -> TYPE_TURF
        isType(
            selectedInstance.type,
            TYPE_AREA
        ) -> TYPE_AREA
        isType(
            selectedInstance.type,
            TYPE_MOB
        ) -> TYPE_MOB
        else -> TYPE_OBJ
    }

    val selectedTypeName = selectedType.substring(1).capitalize()

    add(JMenuItem("Delete Topmost $selectedTypeName (Shift+Click)").apply {
        addActionListener {
            val topmostItem = currentTile.findTopmostTileItem(selectedType)

            if (topmostItem != null) {
                currentTile.deleteTileItem(topmostItem)
                ActionController.addUndoAction(PlaceTileItemAction(map, currentTile.x, currentTile.y, topmostItem.id))
                Frame.update(true)
            }
        }
    })

    return true
}

private fun JPopupMenu.addTileItemsActions(map: Dmm, currentTile: Tile) {
    currentTile.getTileItems().sortedWith(TileItemComparator).reverseTileMovables().forEach { tileItem ->
        val menu = JMenu("${tileItem.getVarText(VAR_NAME)} [${tileItem.type}]").apply {
            this@addTileItemsActions.add(this)
        }

        DmiProvider.getSpriteFromDmi(tileItem.icon, tileItem.iconState, tileItem.dir)?.let { spite ->
            menu.icon = spite.scaledIcon
        }

        // Moving can be done only for objects and mobs.
        if (tileItem.isType(TYPE_OBJ) || tileItem.isType(TYPE_MOB)) {
            menu.add(JMenuItem("Move to Top").apply {
                addActionListener {
                    val higherItemId = currentTile.getHigherMovableId(tileItem.id)
                    if (higherItemId != NON_EXISTENT_INT) {
                        currentTile.switchTileItems(tileItem.id, higherItemId)
                        ActionController.addUndoAction(SwitchTileItemsAction(currentTile, higherItemId, tileItem.id))
                        Frame.update(true)
                    }
                }
            })

            menu.add(JMenuItem("Move to Bottom").apply {
                addActionListener {
                    val lowerItemId = currentTile.getLowerMovableId(tileItem.id)
                    if (lowerItemId != NON_EXISTENT_INT) {
                        currentTile.switchTileItems(tileItem.id, lowerItemId)
                        ActionController.addUndoAction(SwitchTileItemsAction(currentTile, lowerItemId, tileItem.id))
                        Frame.update(true)
                    }
                }
            })

            menu.addSeparator()
        }

        menu.add(JMenuItem("Make Active Object (Ctrl+Shift+Click)").apply {
            addActionListener {
                ObjectTreeView.findAndSelectItemInstance(tileItem)
            }
        })

        menu.add(JMenuItem("Reset to Default").apply {
            addActionListener {
                val newTileItem = currentTile.setTileItemVars(tileItem, null)
                ActionController.addUndoAction(SwapTileItemAction(currentTile, newTileItem.id, tileItem.id))
                Frame.update(true)
            }
        })

        menu.add(JMenuItem("Delete")).apply {
            addActionListener {
                currentTile.deleteTileItem(tileItem)
                ActionController.addUndoAction(PlaceTileItemAction(map, currentTile.x, currentTile.y, tileItem.id))
                Frame.update(true)
            }
        }

        menu.add(JMenuItem("Edit Variables...")).apply {
            addActionListener {
                if (ViewVariablesDialog(currentTile, tileItem).open()) {
                    Frame.update(true)
                }
            }
        }
    }
}

// Method to reverse all movables in the tile items list.
// Used on the sorted list which will have structure like 'area -> movables -> turf' for sure.
// Method itself is needed to show tile items in popup menu properly.
// Like: area goes first, then all movables sorted from top to bottom and then turf.
private fun List<TileItem>.reverseTileMovables(): List<TileItem> {
    // We have only area and turf
    if (this.size == 2) {
        return this
    }

    val area = this.first()
    val turf = this.last()
    val movables = this.subList(1, this.size - 1).reversed()
    val result = mutableListOf<TileItem>()

    result.add(area)
    result.addAll(movables)
    result.add(turf)

    return result
}
