class StuffGraph extends Spine.Module
  init: (params) ->
    @controller = new Controller(params)

  destroy: () ->
    @controller.destroy()


  class Controller extends Spine.Controller
    className = "stuffGraph"


    constructor:(params) ->
      super

      @selectorEl = params.selectorEl

      @el = $(@selectorEl)

      @nodes = []
      @links = []

      @width = 960
      @height = 500

      @render()

      Stuff.bind "create", @newAdd
      Stuff.bind "fetch", @restart

      Stuff.fetch()

      #try with SSE http://www.html5rocks.com/en/tutorials/eventsource/basics/
      #using https://github.com/rwldrn/jquery.eventsource
      $.eventsource({
        label: "stuff-stream",
        url: "/stream/stuff/add",
        dataType: "json",

        message: ( data ) =>
          @newAdd(new Stuff(data))
      });

    newAdd:(stuff) =>
       stuff.x = Math.random()*@width
       stuff.y = Math.random()*@height
       @nodes.push(stuff)
       @restart()

    render: =>

      color = d3.scale.category20();

      @force = d3.layout.force()
         .charge(-120)
         .linkDistance(30)
         .size([@width, @height]);

      @svg = d3.select(@selectorEl).append("svg")
         .attr("width", @width)
         .attr("height", @height);

      @force
         .nodes(@nodes)
         .links(@links)
         .start()

      link = @svg.selectAll("line.link")
           .data(@links)
         .enter().append("line")
           .attr("class", "link")
           .style("stroke-width", (d) -> Math.sqrt(d.value));

      node = @svg.selectAll("circle.node")
          .data(@nodes)
        .enter().append("circle")
          .attr("class", "node")
          .attr("r", 5)
          .style("fill", (d) -> color(d.group))
          .call(@force.drag)

      node.append("title").text((d) -> d.name)

      @force.on("tick", () ->
        link.attr("x1", (d) -> d.source.x)
            .attr("y1", (d) -> d.source.y)
            .attr("x2", (d) -> d.target.x)
            .attr("y2", (d) -> d.target.y);

        node.attr("cx", (d) -> d.x)
            .attr("cy", (d) ->  d.y);
      )

      @

    restart: () =>
      @nodes = Stuff.all().map((i) =>
        i.x = Math.random()*@width
        i.y = Math.random()*@height
        i
      )
      console.dir(@nodes)
      @svg.selectAll("line.link")
          .data(@links)
        .enter().insert("svg:line", "circle.node")
          .attr("class", "link")
          .attr("x1", (d) -> d.source.x )
          .attr("y1", (d) -> d.source.y )
          .attr("x2", (d) -> d.target.x )
          .attr("y2", (d) -> d.target.y );

      @svg.selectAll("circle.node")
          .data(@nodes)
        .enter().insert("svg:circle", "circle.cursor")
          .attr("class", "node")
          .attr("cx", (d) -> d.x )
          .attr("cy", (d) -> d.y )
          .attr("r", 5)
          .call(@force.drag);

      @force.start();


    destroy:() =>
      @release()

window.StuffGraph = StuffGraph
