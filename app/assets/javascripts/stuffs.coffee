class Stuffs extends Spine.Module

  init: (element) ->
    @controller = new Controller(element)

  destroy: () ->
    @controller.destroy()

  class Model extends Spine.Model
    @configure "Stuff", "neo4jid", "foo", "bar", "baz", "creation"

    @extend Spine.Model.Ajax

    @url: "/rest/stuffs"

    validate: ->
      unless @foo
        "Foo is required (string)"
      unless @bar
        "Bar is required (boolean)"
      unless @baz
        "Baz is required (number)"

  class Item extends Spine.Controller
    events:
      "click" :     "click"

    constructor: ->
      super
      throw "@item required" unless @item
      @item.bind("update", @render)
      @item.bind("destroy", @remove)

    render: (item) =>
      @item = item if item
      @html $("#stuffTemplate").tmpl ( @item )
      @

    remove: =>
      @el.remove()

    click: (e) =>
      alert $(e.target).item().firstName

  class Form extends Spine.Controller
    className:  'form'
    events:
      "submit form.addStuff" :     "create"

    render: (item) =>
      @html $("#stuffFormTemplate").tmpl ( item )
      @rendered = true
      @

    create: (e)->
      e.preventDefault()
      stuff = Model.fromForm(e.target)
      stuff.save()
      @el.hide()
      false

  class Controller extends Spine.Controller
    className: "stuffs"

    elements:
      '.list':  "list"

    events:
      'click i.addStuff': "showForm"

    constructor: ->
      super

      @items = []

      @render()

      Model.bind "refresh", @addAll
      Model.bind "create", @addOne

      @addForm = new Form()

      Model.fetch()

      #try with SSE http://www.html5rocks.com/en/tutorials/eventsource/basics/
      #using https://github.com/rwldrn/jquery.eventsource
      $.eventsource({
        label: "stuff-count",
        url: "/stream/stuff/count",
        dataType: "text",

        message: ( data ) ->
          console.log( data );
          $("#count").text(1+parseInt($("#count").text()))
          #todo $.eventsource("close", "stuff-count");

      });

    render: =>
      t = $('#stuffsTemplate').tmpl()
      @html(t)
      @

    showForm: () =>
      if @addForm.rendered
        @addForm.el.show()
      else
        @append(@addForm.render())

    addOne: (item) =>
      stuff = new Item(item: item)
      stuff.render()
      @items.push(stuff)
      @list.append(stuff.el)

    addAll: =>
      @clean()
      all = Model.all()
      $("#count").text(all.length)
      @addOne i for i in all

    clean: =>
      @items.length = 0
      @list.empty()

    destroy:() =>
      @clean()
      @release()

window.Stuffs = Stuffs
