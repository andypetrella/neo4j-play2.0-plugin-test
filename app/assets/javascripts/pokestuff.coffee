class PokeStuff extends Spine.Model
  @configure "PokeStuff", "stuff", "how", "poked"

  @extend Spine.Model.Ajax

  @url: "/rest/pokestuffs"

  validate: ->
    unless @stuff
      "Stuff id is required (int)"
    unless @how
      "How is required (string)"
    unless @poked
      "Poked Stuff is required (int)"

window.PokeStuff = PokeStuff