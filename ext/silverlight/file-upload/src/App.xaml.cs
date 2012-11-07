using System;
using System.Windows;
using System.Windows.Browser;
using System.Windows.Controls;
using System.Windows.Media;

namespace OverviewProject.FileUpload {
  public partial class App : Application {
    private UploadRequestFactory uploadRequestFactory;

    public App() {
      this.Startup += Application_Startup;
      this.UnhandledException += this.Application_UnhandledException;

      InitializeComponent();
    }

    private void Application_Startup(object sender, StartupEventArgs e) {
      this.RootVisual = new FilePickerControl();
      HtmlPage.RegisterScriptableObject("FilePickerControl", this.RootVisual);

      this.uploadRequestFactory = new UploadRequestFactory();
      HtmlPage.RegisterScriptableObject("UploadRequestFactory", this.uploadRequestFactory);
    }

    private void Application_UnhandledException(object sender, ApplicationUnhandledExceptionEventArgs e) {
      MessageBox.Show("Exception!");
      MessageBox.Show(e.ExceptionObject.Message);
    }
  }
}