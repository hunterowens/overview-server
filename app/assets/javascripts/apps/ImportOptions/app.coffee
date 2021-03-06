define [ 'jquery', 'bootstrap-dialog', './models/Options', './views/Options', 'i18n' ], ($, BootstrapDialog, Options, OptionsView, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.index.ImportOptions.#{key}", args...)

  # Produces document-set import options, either inline in a form or
  # through a dialog.
  #
  # Usage:
  #     requiredOptions = {
  #       supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'}]
  #       defaultLanguageCode: 'en'
  #     }
  #
  #     # Inline
  #     app = new OptionsApp(requiredOptions)
  #     $('form.document-set-import').append(app.el)
  #
  #     # Dialog
  #     $('form.document-set-import').on 'submit', (e) ->
  #       OptionsApp.interceptSubmitEvent(e, requiredOptions)
  #
  # Inline
  # ======
  #
  # If you want to add document-set import options to an existing form, simply
  # append the `.el` value, which is an HTML fieldset element.
  #
  # Dialog
  # ======
  #
  # When you call `interceptSubmitEvent(submitEvent)`, this app will intercept
  # the event. This means:
  #
  # 1. It calls `event.preventDefault()`
  # 2. It presents a dialog box with additional options
  # 3. If the user presses "Submit" in the dialog, the options are added to the
  #    original form and `form.submit()` is called. (Step 1 does not apply when
  #    the options are added.)
  # 4. If the user presses "Cancel" in the dialog, the dialog disappears and
  #    the page is left alone.
  #
  # Options
  # =======
  #
  # Required:
  #
  # * `supportedLanguages`: An Array of `{ code: "en", name: "English" }` values
  # * `defaultLanguageCode`: A language code like `"en"`
  #
  # Optional:
  #
  # * `excludeOptions`: An Array of options to exclude, such as `split_documents`.
  class App
    constructor: (@options) ->
      throw 'Must pass supportedLanguages, an Array of { code: "en", name: "English" } values' if !@options.supportedLanguages?
      throw 'Must pass defaultLanguageCode, a language code like "en"' if !@options.defaultLanguageCode?

      @model = new Options({}, @options)
      view = new OptionsView({ model: @model })
      @el = view.el

    @addHiddenInputsThroughDialog: (form, options) ->
      $form = $(form)
      app = new App(options)

      submit = ->
        for k, v of app.model.attributes
          $input = $('<input type="hidden" />')
            .attr('name', k)
            .attr('value', v)
          $form.append($input)
        options.callback()

      $h3 = $('<h3></h3>').text(t('dialog.title'))
      $close = $('<a href="#" class="close" data-dismiss="modal">×</a>')

      dialog = new BootstrapDialog
        title: $close.after($h3)
        content: app.el
        buttons: [
          {
            label: t('dialog.cancel')
            cssClass: 'btn-default'
            onclick: -> dialog.close()
          }
          {
            label: t('dialog.submit')
            cssClass: 'btn-primary'
            onclick: ->
              dialog.close()
              submit()
          }
        ]
      dialog.open()
      dialog.getDialog().on('hidden', -> dialog.destroy())

      undefined

    @interceptSubmitEvent: (e, options) ->
      form = e.target

      if form.hasAttribute('import-options-submitting')
        # We're intercepting a submit event that was generated by us. Stop.
      else
        # We're intercepting a user's submit event
        e.preventDefault()

        options = _.extend {}, options, callback: ->
          $(form)
            .attr('import-options-submitting', true)
            .submit()

        @addHiddenInputsThroughDialog(form, options)

      undefined
