class App extends Spine.Controller

  constructor: ->
    @stuffController = new Stuffs(el:$("#stuffsContainer"), selectorEl:"#graph")
    @stuffCountController = new StuffCounts(selectorEl:"#d3")

window.App = App

$ ->
  window.app = new App()

