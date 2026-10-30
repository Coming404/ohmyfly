import Foundation
import Metal
import ObjectiveC
import QuartzCore
import Shared

/// Startup record only: Compose owns its drawables, so the presented time is read by hooking
/// CAMetalLayer.nextDrawable for the 20-second startup window.
enum PresentTiming {
    private static var installed = false

    static func install() {
        guard !installed,
              let original = class_getInstanceMethod(CAMetalLayer.self, #selector(CAMetalLayer.nextDrawable)),
              let replacement = class_getInstanceMethod(CAMetalLayer.self, #selector(CAMetalLayer.omf_nextDrawable))
        else { return }
        installed = true
        method_exchangeImplementations(original, replacement)
        PresentTimingKt.startupPresentHookInstalled()
    }

    fileprivate static func observe(_ drawable: CAMetalDrawable) {
        guard Thread.isMainThread, PresentTimingKt.startupPresentRecording() else { return }
        PresentTimingKt.startupDrawableObserved()
        drawable.addPresentedHandler { presented in
            let seconds = presented.presentedTime
            DispatchQueue.main.async { PresentTimingKt.startupPresented(seconds: seconds) }
        }
    }
}

extension CAMetalLayer {
    // After the exchange this name points at the system implementation.
    @objc fileprivate func omf_nextDrawable() -> CAMetalDrawable? {
        let drawable = omf_nextDrawable()
        if let drawable { PresentTiming.observe(drawable) }
        return drawable
    }
}
