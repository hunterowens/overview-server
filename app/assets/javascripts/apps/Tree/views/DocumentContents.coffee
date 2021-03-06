define [
  'backbone'
  '../../DocumentDisplay/app'
], (Backbone, DocumentDisplayApp) ->
  Backbone.View.extend
    id: 'document'

    initialize: ->
      throw 'must set options.cache, a Cache' if !@options.cache
      throw 'must set options.state, a State' if !@options.state

      @state = @options.state
      @cache = @options.cache

      @app = new DocumentDisplayApp({ el: @el })

      @listenTo(@state, 'change:selection', => @render())

      @render()

    # Returns a JSON POD document object, or undefined.
    #
    # The value will only be defined if
    #
    # * There is one selected document; and
    # * The document is in the cache.
    _getDocument: ->
      selection = @state.get('selection')
      docids = selection.documents
      return undefined if docids.length != 1
      docid = docids[0]
      @cache.document_store.documents[docid]

    scroll_by_pages: (n) ->
      @app.scrollByPages(n)

    render: ->
      document = @_getDocument()

      @app.setDocument(document) if document

      this
