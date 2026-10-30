import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    @Binding var colorScheme: ColorScheme?

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController { dark, followsSystem in
            colorScheme = followsSystem.boolValue ? nil : (dark.boolValue ? .dark : .light)
        }
        // After MainViewController so the startup record exists before the first drawable.
        PresentTiming.install()
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var colorScheme: ColorScheme? = nil

    var body: some View {
        ComposeView(colorScheme: $colorScheme)
            .ignoresSafeArea()
            .preferredColorScheme(colorScheme)
    }
}
