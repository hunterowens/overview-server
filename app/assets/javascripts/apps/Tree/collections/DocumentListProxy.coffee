define [ 'backbone' ], (Backbone) ->
  Document = Backbone.Model

  Collection = Backbone.Collection.extend
    model: Document

  Model = Backbone.Model

  # Proxies a DocumentList into a Backbone.Model.
  #
  # Usage:
  #
  #   proxy = new DocumentListProxy(documentList)
  #   model = proxy.model
  #   documentList.slice(0, 10) # returns a Deferred and sends a request
  #   model.documents.slice(0, 10) # does neither; it's a Backbone.Collection
  #   model.describeSelection() # returns [ 'node', 'foo' ], for instance
  #   model.get('n') # returns number of documents, or undefined
  #   proxy.destroy() # frees up resources
  #
  # The proxy will be loaded with dummy Backbone.Models. A dummy Model has no
  # id. If it's at the end of the list, it means there are more models that
  # haven't been loaded. If it's in the middle of the list, it means it is
  # already being loaded.
  #
  # Not much can change on the Model:
  #
  # * change:id when the Model becomes defined
  # * change:tagids when the Model's tag IDs change (this will NOT be fired
  #                 when a tag's ID changes, but the underlying array will
  #                 be edited in-place)
  #
  # There's more to listen to, on the Model's `.documents`:
  #
  # * change when a Model within the Collection changes.
  # * add when a Model is added to the Collection.
  #
  # You may use a TagStoreProxy to map from tag ids to tag Models.
  class DocumentListProxy
    constructor: (@documentList) ->
      @cache = @documentList.cache
      @tagStore = @cache.tag_store
      @documentStore = @cache.document_store

      @model = new Model()
      @model.describeSelection = =>
        @cache.describeSelectionWithoutDocuments(@documentList.selection)
      @model.documents = new Collection([])

      @_documentListCallback = => @_updateFromDocumentList()
      @_documentStoreCallback = (document) => @_updateFromDocument(document)
      @_tagStoreCallback = (tagid, tag) => @_updateFromTagId(tagid, tag)

      @documentList.observe(@_documentListCallback)
      @documentStore.observe('document-changed', @_documentStoreCallback)
      @tagStore.observe('id-changed', @_tagStoreCallback)
      @_updateFromDocumentList()

    _updateFromDocumentList: ->
      # Only a certain number of changes can occur:
      #
      # * items in documentList.documents can change from undefined to defined
      # * new items (possibly undefined) can be added to documentList.documents
      # * a document can change (its tagids array)
      #
      # In particular, this can't happen:
      #
      # * a document can't be removed
      # * a document can't revert to undefined

      documentToModel = (document) -> new Document(document || {})

      for document, i in @documentList.documents
        # Deep-copy attributes, so Backbone can detect changes later
        document = JSON.parse(JSON.stringify(document)) if document?

        if @model.documents.length <= i
          @model.documents.add(documentToModel(document))
        else
          model = @model.documents.at(i)
          model.set(document || {})

      if @model.documents.length < @documentList.n
        @model.documents.add(new Document({})) # add a dummy model to show there's more

      @model.set('n', @documentList.n)

    _updateFromTagId: (tagid, tag) ->
      @model.documents.each (document) ->
        tagids = document.get('tagids')
        index = _.indexOf(tagids, tagid)
        if index != -1
          tagids.splice(index, 1, tag.id)

    _updateFromDocument: (document) ->
      # A document will never change ID. Either it's present in the collection
      # or it isn't.
      model = @model.documents.get(document.id)
      if model?
        # Deep-copy attributes, so Backbone can detect changes later
        document = JSON.parse(JSON.stringify(document))
        model.set(document)

    destroy: ->
      @documentList.unobserve(@_callback)
      @documentStore.unobserve('document-changed', @_documentStoreCallback)
      @tagStore.unobserve('id-changed', @_tagStoreCallback)
