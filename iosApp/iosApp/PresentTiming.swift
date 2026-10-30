import Foundation
import ObjectiveC
import QuartzCore
import Shared

/// Startup record only. Compose 1.12 parallel rendering draws on a background queue into
/// CMPMetalLayer drawables and the main thread swaps them into the layer contents, so CAMetalLayer
/// is never used. Both CMPMetalLayer steps are timed for the first seconds after launch.
enum PresentTiming {
    private static var deadline: CFTimeInterval = 0

    static func install() {
        guard deadline == 0, let layer = NSClassFromString("CMPMetalLayer"),
              let present = class_getInstanceMethod(layer, NSSelectorFromString("presentOnMainThread:onDisplay:")),
              let next = class_getInstanceMethod(layer, NSSelectorFromString("nextDrawable"))
        else { return }
        // Longer than the 20 s record window, which filters samples itself.
        deadline = CACurrentMediaTime() + 25
        wrapPresent(present)
        wrapNextDrawable(next)
        PresentTimingKt.startupPresentHookInstalled()
    }

    private static func wrapPresent(_ method: Method) {
        typealias Present = @convention(c) (AnyObject, Selector, AnyObject?, AnyObject?) -> Void
        let original = unsafeBitCast(method_getImplementation(method), to: Present.self)
        let selector = method_getName(method)
        let block: @convention(block) (CALayer, AnyObject?, AnyObject?) -> Void = { layer, drawable, onDisplay in
            guard CACurrentMediaTime() < deadline else {
                original(layer, selector, drawable, onDisplay)
                return
            }
            let before = layer.contents.map { $0 as AnyObject }
            original(layer, selector, drawable, onDisplay)
            // presentOnMainThread drops stale or resized drawables without touching contents.
            guard let after = layer.contents.map({ $0 as AnyObject }), after !== before,
                  PresentTimingKt.startupPresentRecording() else { return }
            PresentTimingKt.startupPresented(seconds: CACurrentMediaTime())
        }
        method_setImplementation(method, imp_implementationWithBlock(block))
    }

    private static func wrapNextDrawable(_ method: Method) {
        typealias Next = @convention(c) (AnyObject, Selector) -> AnyObject?
        let original = unsafeBitCast(method_getImplementation(method), to: Next.self)
        let selector = method_getName(method)
        let block: @convention(block) (AnyObject) -> AnyObject? = { layer in
            let start = CACurrentMediaTime()
            let drawable = original(layer, selector)
            let end = CACurrentMediaTime()
            // nextDrawable polls every 1 ms while the previous frame's GPU work is unfinished.
            if end < deadline, end - start >= 0.001 {
                DispatchQueue.main.async {
                    PresentTimingKt.startupDrawableWaited(endSeconds: end, waitSeconds: end - start)
                }
            }
            return drawable
        }
        method_setImplementation(method, imp_implementationWithBlock(block))
    }
}
