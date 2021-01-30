package app

import "github.com/SpaiR/strongdmm/internal/app/byond/dmm"

func (a *app) openMap(path string) {
	dmmData, _ := dmm.NewData(path)
	a.internalData.AddRecentMap(a.loadedEnvironment.RootFilePath, path)
	dmm := dmm.New(a.loadedEnvironment, dmmData)
	println(dmm)
}
