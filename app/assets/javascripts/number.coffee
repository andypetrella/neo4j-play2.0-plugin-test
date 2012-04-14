class StuffCounts extends Spine.Module
    init: (params) ->
      @controller = new Controller(params)

    destroy: () ->
      @controller.destroy()


    class Model extends Spine.Model
      @configure "StuffCount", "count", "date"

    class Controller extends Spine.Controller
      className = "stuffCounts"


      constructor: (params)->
        super

        @selectorEl = params.selectorEl

        @el = $(@selectorEl)

        @xRange = [0, 650]
        @yRange = [400, 0] #inverted because of svg crs


        @render()


        Model.bind "create", @newCount
        Model.bind "fetch", @newCount

        Model.fetch()

        #try with SSE http://www.html5rocks.com/en/tutorials/eventsource/basics/
        #using https://github.com/rwldrn/jquery.eventsource
        $.eventsource({
          label: "stuff-count",
          url: "/stream/stuff/count",
          dataType: "json",

          message: ( data ) ->
            l = Model.last()
            data.n = l.count + data.n if l

            f = Model.first()
            if not @startingDate
              if f
                @startingDate = f.d
              else
                @startingDate = data.d

            data.d = data.d - @startingDate

            m = new Model(count: data.n, date: data.d)
            m.save()
        });

      xAccessor: (d) =>
        d.date

      yAccessor: (d) =>
        d.count

      render: =>
        @svg = d3.select(@selectorEl).append("svg")
            .attr("width", 720)
            .attr("height", 600)
            .append("g")

        @svg.attr("transform", "translate(50,120)");

        @svg.append("svg:path")

        @svg.append("g").attr("class", "y axis")

        @svg.append("g").attr("transform", "translate(0, "+@yRange[0]+")").attr("class", "x axis")

        @


      computeScale: (domain, accessor, range) =>
          d3.scale.linear().domain(d3.extent(domain, accessor)).range(range)


      computeAxis: (scale, orientation) => d3.svg.axis().scale(scale).orient(orientation)


      applyAxis: (selector, ax) => @svg.selectAll(selector).call(ax)


      applyLine: (selector, data, x, y) =>
        @svg
        .selectAll(selector)
        .data([data])
        .attr("d", d3.svg.line()
                    .x((d) -> x(d.date))
                    .y((d) -> y(d.count)))


      newCount: (countEvent) =>
        data = Model.all()

        if data && data.length > 0
          x = @computeScale(data, @xAccessor, @xRange)
          xAxis = @computeAxis(x, "bottom")
          @applyAxis("g.x.axis", xAxis);

          y = @computeScale(data, @yAccessor, @yRange)
          yAxis = @computeAxis(y, "left")
          @applyAxis("g.y.axis", yAxis);

          @applyLine("path", data, x, y)

      destroy:() =>
        @release()

window.StuffCounts = StuffCounts
