class Stuff extends Spine.Model
  @configure "Stuff", "neo4jid", "foo", "bar", "baz", "group", "creation"

  @extend Spine.Model.Ajax

  @url: "/rest/stuffs"

  validate: ->
    unless @foo
      "Foo is required (string)"
    unless @bar
      "Bar is required (boolean)"
    unless @baz
      "Baz is required (number)"

window.Stuff = Stuff