class Stuffs extends Spine.Module

  init: (params) =>
    @controller = new Controller(params)

  destroy: () =>
    @controller.destroy()

  class Form extends Spine.Controller
    className:  'form'
    events:
      "submit form.addStuff" :     "create"

    render: (item) =>
      @html $("#stuffFormTemplate").tmpl ( item )
      @rendered = true
      @

    create: (e)=>
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

      @data =
        stuffs: []
        relations: []

      @width = 400
      @height = 500
      @color = d3.scale.category10()


      Stuff.bind "create", @newAdd
      Stuff.bind "refresh", @fetched

      PokeStuff.bind "create", @newPoke


      @render()

      Stuff.fetch()
      PokeStuff.fetch()

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
      $.eventsource({
        label: "stuff-stream",
        url: "/stream/stuff/pokes",
        dataType: "json",

        message: ( data ) =>
          console.log("new poke")
          console.dir(data)
          console.log("new poke")
      })

      #@restart()

    newAdd:(stuff) =>
       #stuff.x = Math.random()*(@width-50)+25
       #stuff.y = Math.random()*(@height-50)+25
       @data.stuffs.push(stuff)
       @restart()

    createRelation: (poke) =>{
      source:  @data.stuffs.filter((element, index, array) -> element.neo4jid is poke.stuff)[0],
      target: @data.stuffs.filter((element, index, array) -> element.neo4jid is poke.poked)[0]
    }

    newPoke:(poke) =>
       relation = @createRelation(poke)
       console.dir(relation)
       @data.relations.push(relation)
       @restart()

    render: =>
      t = $('#stuffsTemplate').tmpl()
      @html(t)

      @svg = d3.select(@selectorEl).append("svg")
         .attr("width", @width)
         .attr("height", @height)

      @force = d3.layout.force()
        .charge(-120)
        .linkDistance(30)
        .size([@width, @height])
      @
    @

    fetched: () =>
      @data.stuffs = Stuff.all()
      @data.relations = []
      @data.stuffs.forEach((element, index, array) =>
        console.dir(element)
        @data.relations.push(@createRelation(p)) for p in element.pokes
      )

      @restart()

    restart: () =>
      @links = @svg.selectAll("line.link")
          .data(@data.relations)
      @links
        .enter().append("svg:line")
          .attr("class", "link")
      @links
        .exit()
          .remove()

      @nodes = @svg.selectAll("circle.node").data(@data.stuffs)
      @nodes
        .exit()
          .remove()
      @nodes
        .enter()
          .append("svg:circle")
            .attr("class", "node")
            .attr("r", 5)
            .attr("fill", (d) =>
              if d.group
                @color(d.group)
              else
                "red"
            )
            .on("click", (stuff) =>
              if @currentStart
                how = prompt("How ?")
                if not how
                  alert("poke cancelled")
                  @currentStart = stuff
                  return
                #create poke
                console.log("create poke between")
                console.dir(@currentStart.neo4jid)
                console.log("and")
                console.dir(stuff.neo4jid)
                console.log("-------")
                ps = new PokeStuff(stuff:@currentStart.neo4jid, how:how, poked:stuff.neo4jid)
                console.dir(ps)
                unless ps.save()
                  alert(ps.validate())
                console.log("poke saved")
                #reset @currentStart
                @currentStart = undefined
              else
                @currentStart = stuff
            )
            .call(@force.drag)


      @force
      .nodes(@data.stuffs)
      .links(@data.relations)
      .on("tick", () =>
          @nodes
            .attr("cx", (d) -> d.x = Math.max(5, Math.min(400 - 5, d.x)))
            .attr("cy", (d) -> d.y = Math.max(5, Math.min(500 - 5, d.y)))

          @links
            .attr("x1", (d) -> d.source.x)
            .attr("y1", (d) -> d.source.y)
            .attr("x2", (d) -> d.target.x)
            .attr("y2", (d) -> d.target.y)
      )
      .start()

      @

    showForm: () =>
      if @addForm.rendered
        if @addForm.el.is(":visible")
          @addForm.el.hide()
        else
          @addForm.el.show()
      else
        @append(@addForm.render())

    destroy:() =>
      @release()

window.Stuffs = Stuffs
