package render

import (
	"fmt"
	"github.com/rs/zerolog/log"
	"sdmm/internal/app/render/brush"
	"sdmm/internal/app/render/bucket/level/chunk/unit"
	"sdmm/internal/dmapi/dmicon"
	"sdmm/internal/util"
	"strings"
)

var (
	MultiZRendering  = true
	SimWallSmoothing = true

	multiZShadow = util.MakeColor(0, 0, 0, .35)
)

type unitProcessor interface {
	ProcessUnit(unit.Unit) (visible bool)
}

func (r *Render) batchBucketUnits(viewBounds util.Bounds) {
	if MultiZRendering && r.Camera.Level > 1 {
		for level := 1; level < r.Camera.Level; level++ {
			r.batchLevel(level, viewBounds, false) // Draw everything below.
		}

		// Draw a "shadow" overlay to visually separate different levels.
		brush.RectFilled(viewBounds.X1, viewBounds.Y1, viewBounds.X2, viewBounds.Y2, multiZShadow)
	}

	r.batchLevel(r.Camera.Level, viewBounds, true) // Draw currently visible level.

	if r.overlay != nil {
		r.overlay.FlushUnits()
	}
}

func (r *Render) batchLevel(level int, viewBounds util.Bounds, withUnitHighlight bool) {
	visibleLevel := r.bucket.Level(level)

	// Iterate through every layer to render.
	for _, layer := range visibleLevel.Layers {
		// Iterate through chunks with units on the rendered layer.
		for _, chunk := range visibleLevel.ChunksByLayers[layer] {
			// Out of bounds = skip.
			if !chunk.ViewBounds.ContainsV(viewBounds) {
				continue
			}

			// Get all units in the chunk for the specific layer.
			for _, u := range chunk.UnitsByLayers[layer] {
				// Out of bounds = skip
				if !u.ViewBounds().ContainsV(viewBounds) {
					continue
				}
				// Process unit
				if r.unitProcessor != nil && !r.unitProcessor.ProcessUnit(u) {
					continue
				}

				// TGHACK:
				// This is a particularly nasty way to implement this, but it's an interim hack to achieve the goal
				if strings.HasPrefix(u.Instance().Prefab().Path(), "/turf/closed/wall") && SimWallSmoothing {
					// If we do hit a wall turf, do some funny hacks
					// first we're gonna build a bitmask for the sprite
					// We have to extract the point and then expect adjacent on the dmm

					// Then, due to split vis, we have to overdraw the *four* sprites for each side of the split vis
					// Define direction constants
					const (
						North = 1 << iota
						South
						East
						West
						NorthEast
						SouthEast
						SouthWest
						NorthWest
					)

					currentTurfPath := u.Instance().Prefab().Path()
					// Local function to check if adjacent points have closed turf prefab paths
					isMatchingTurf := func(point util.Point) bool {
						if point.X < 0 || point.Y < 0 || point.X > r.liveDmm.MaxX || point.Y > r.liveDmm.MaxY {
							return false
						}
						log.Debug().Msg(fmt.Sprint(point, ";", r.liveDmm.MaxX, r.liveDmm.MaxY))
						tilePrefabs := r.liveDmm.GetTile(point).Instances().Prefabs()
						for _, prefab := range tilePrefabs {
							if prefab.Path() == currentTurfPath {
								return true
							}
						}
						return false
					}

					// Local function to build the bitmask
					buildAdjacentBitmask := func(u unit.Unit) int {
						point := u.Instance().Coord()
						bitmask := 0

						// Check north
						if isMatchingTurf(point.Plus(util.Point{
							X: 0,
							Y: 1,
							Z: 0,
						})) {
							bitmask |= North
						}

						// Check south
						if isMatchingTurf(point.Plus(util.Point{
							X: 0,
							Y: -1,
							Z: 0,
						})) {
							bitmask |= South
						}

						// Check west
						if isMatchingTurf(point.Plus(util.Point{
							X: -1,
							Y: 0,
							Z: 0,
						})) {
							bitmask |= West
						}

						// Check east
						if isMatchingTurf(point.Plus(util.Point{
							X: 1,
							Y: 0,
							Z: 0,
						})) {
							bitmask |= East
						}

						// Check northeast
						if (bitmask&North) == 1 && (bitmask&East == 1) && isMatchingTurf(point.Plus(util.Point{
							X: 1,
							Y: 1,
							Z: 0,
						})) {
							bitmask |= NorthEast
						}

						// Check southeast
						if (bitmask&South) == 1 && (bitmask&East) == 1 && isMatchingTurf(point.Plus(util.Point{
							X: 1,
							Y: -1,
							Z: 0,
						})) {
							bitmask |= SouthEast
						}

						// Check southwest
						if (bitmask&South) == 1 && (bitmask&West) == 1 && isMatchingTurf(point.Plus(util.Point{
							X: -1,
							Y: -1,
							Z: 0,
						})) {
							bitmask |= SouthWest
						}

						// Check northwest
						if (bitmask&North) == 1 && (bitmask&West) == 1 && isMatchingTurf(point.Plus(util.Point{
							X: -1,
							Y: 1,
							Z: 0,
						})) {
							bitmask |= NorthWest
						}

						return bitmask
					}

					// Calculate the bitmask
					bitmask := buildAdjacentBitmask(u)

					// this is a really bad way of doing this, but this unrolled loop is easier for me to write knowing
					// nothing about go.
					// The rest is also horrible way of doing it too, so w/e
					getSpriteForDir := func(bitmask int, dir int) *dmicon.Sprite {
						dmi := u.Sprite().Dmi()
						state, err := dmi.State(fmt.Sprint(bitmask, "-", dir))
						if err != nil {
							log.Debug().Msg(fmt.Sprint("Uh oh ", err, ";", u.Instance().Prefab().Path()))
						}
						return state.Sprite()
					}
					brush.RectFilled(
						u.ViewBounds().X1+4, u.ViewBounds().Y1, u.ViewBounds().X2-4, u.ViewBounds().Y2, util.MakeColor(0, 0, 0, 1),
					)
					sprite := getSpriteForDir(bitmask, 1)
					brush.RectTexturedV(
						u.ViewBounds().X1, u.ViewBounds().Y1, u.ViewBounds().X2, u.ViewBounds().Y2,
						u.R(), u.G(), u.B(), u.A(),
						sprite.Texture(),
						sprite.U1, sprite.V1, sprite.U2, sprite.V2,
					)
					sprite = getSpriteForDir(bitmask, 2)
					brush.RectTexturedV(
						u.ViewBounds().X1, u.ViewBounds().Y1, u.ViewBounds().X2, u.ViewBounds().Y2,
						u.R(), u.G(), u.B(), u.A(),
						sprite.Texture(),
						sprite.U1, sprite.V1, sprite.U2, sprite.V2,
					)
					sprite = getSpriteForDir(bitmask, 4)
					brush.RectTexturedV(
						u.ViewBounds().X1, u.ViewBounds().Y1, u.ViewBounds().X2, u.ViewBounds().Y2,
						u.R(), u.G(), u.B(), u.A(),
						sprite.Texture(),
						sprite.U1, sprite.V1, sprite.U2, sprite.V2,
					)
					sprite = getSpriteForDir(bitmask, 8)
					brush.RectTexturedV(
						u.ViewBounds().X1, u.ViewBounds().Y1, u.ViewBounds().X2, u.ViewBounds().Y2,
						u.R(), u.G(), u.B(), u.A(),
						sprite.Texture(),
						sprite.U1, sprite.V1, sprite.U2, sprite.V2,
					)
				} else {
					// Otherwise, do the standard original control flow
					brush.RectTexturedV(
						u.ViewBounds().X1, u.ViewBounds().Y1, u.ViewBounds().X2, u.ViewBounds().Y2,
						u.R(), u.G(), u.B(), u.A(),
						u.Sprite().Texture(),
						u.Sprite().U1, u.Sprite().V1, u.Sprite().U2, u.Sprite().V2,
					)
				}

				if withUnitHighlight {
					r.batchUnitHighlight(u)
				}
			}
		}
	}
}

func (r *Render) batchUnitHighlight(u unit.Unit) {
	if r.overlay == nil {
		return
	}
	if highlight := r.overlay.Units()[u.Instance().Id()]; highlight != nil {
		r, g, b, a := highlight.Color().RGBA()
		brush.RectTexturedV(
			u.ViewBounds().X1, u.ViewBounds().Y1, u.ViewBounds().X2, u.ViewBounds().Y2,
			r, g, b, a,
			u.Sprite().Texture(),
			u.Sprite().U1, u.Sprite().V1, u.Sprite().U2, u.Sprite().V2,
		)
	}
}
