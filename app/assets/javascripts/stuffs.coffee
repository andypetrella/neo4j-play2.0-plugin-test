class Stuffs extends Spine.Module

  init: (params) ->
    @controller = new Controller(params)

  destroy: () ->
    @controller.destroy()

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
      stuff = Stuff.fromForm(e.target)
      stuff.save()
      @el.hide()
      false

  class Controller extends Spine.Controller
    className: "stuffs"

    elements:
      '.list':  "list"

    events:
      'click i.addStuff': "showForm"

    constructor: (params)->
      super

      @selectorEl = params.selectorEl

      @nodes = []
      @links = []

      @width = 960
      @height = 500


      Stuff.bind "create", @newAdd
      Stuff.bind "refresh", @fetched


      @render()

      Stuff.fetch()

      @addForm = new Form()


      #try with SSE http://www.html5rocks.com/en/tutorials/eventsource/basics/
      #using https://github.com/rwldrn/jquery.eventsource
      $.eventsource({
        label: "stuff-stream",
        url: "/stream/stuff/add",
        dataType: "json",

        message: ( data ) =>
          @newAdd(new Stuff(data))
      })

      @restart()



    newAdd:(stuff) =>
       stuff.x = Math.random()*(@width-50)+25
       stuff.y = Math.random()*(@height-50)+25
       @nodes.push(stuff)
       @links.push({source:@nodes[0], target:stuff})
       @restart()



    render: =>
      t = $('#stuffsTemplate').tmpl()
      @html(t)

      color = d3.scale.category20()

      @svg = d3.select(@selectorEl).append("svg")
         .attr("width", @width)
         .attr("height", @height)

      @force = d3.layout.force()
         #.charge(-120)
         #.linkDistance(30)
         .size([@width, @height])
         .nodes(@nodes)
         .links(@links)

      @force.on("tick", () ->
        @svg.selectAll("line.link")
            .attr("x1", (d) -> d.source.x)
            .attr("y1", (d) -> d.source.y)
            .attr("x2", (d) -> d.target.x)
            .attr("y2", (d) -> d.target.y)

        @svg.selectAll("circle.node")
            .attr("cx", (d) -> d.x)
            .attr("cy", (d) ->  d.y)
      )

      @


    fetched: () =>
      @nodes = Stuff.all().map((i) =>
        i.x = Math.random()*(@width-50)+25
        i.y = Math.random()*(@height-50)+25
        i
      )
      @links = @nodes.map((n) => {source:@nodes[0], target:n})
      @restart()

    restart: () =>
      @svg.selectAll("line.link")
          .data(@links)
        .enter().append("svg:line")
          .attr("class", "link")
          .attr("x1", (d) -> d.source.x )
          .attr("y1", (d) -> d.source.y )
          .attr("x2", (d) -> d.target.x )
          .attr("y2", (d) -> d.target.y )

      @svg.selectAll("circle.node")
          .data(@nodes)
        .enter().append("svg:circle")
          .attr("class", "node")
          .attr("cx", (d) -> d.x )
          .attr("cy", (d) -> d.y )
          .attr("r", 5)
          .call(@force.drag)

      @force.start()


    showForm: () =>
      if @addForm.rendered
        @addForm.el.show()
      else
        @append(@addForm.render())


    destroy:() =>
      @release()

window.Stuffs = Stuffs
