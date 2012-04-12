class App extends Spine.Controller

  constructor: ->
    @stuffController = new Stuffs(el:$("#stuffsContainer"))


window.App = App

$ ->
  window.app = new App()

