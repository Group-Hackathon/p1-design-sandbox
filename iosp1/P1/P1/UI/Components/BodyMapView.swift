import SwiftUI
import WebKit

/// PainDiary-style 3D body map (offline WKWebView + Three.js, see shared-bodymap/).
/// The user rotates the mannequin and taps body areas; every change is reported
/// through `onSelectionChanged` with region ids and display labels.
struct BodyMapView: UIViewRepresentable {
    var viewSide: String = "front"
    var onSelectionChanged: (_ regions: [String], _ labels: [String]) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onSelectionChanged: onSelectionChanged)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "bodymap")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .white
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false

        if let url = Bundle.main.url(forResource: "bodymap", withExtension: "html") {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.onSelectionChanged = onSelectionChanged
        webView.evaluateJavaScript(
            "window.bodymapSetView && window.bodymapSetView('\(viewSide)')",
            completionHandler: nil
        )
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        var onSelectionChanged: ([String], [String]) -> Void

        init(onSelectionChanged: @escaping ([String], [String]) -> Void) {
            self.onSelectionChanged = onSelectionChanged
        }

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard let json = message.body as? String,
                  let data = json.data(using: .utf8),
                  let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  payload["type"] as? String == "selection"
            else { return }
            let regions = payload["regions"] as? [String] ?? []
            let labels = payload["labels"] as? [String] ?? []
            DispatchQueue.main.async {
                self.onSelectionChanged(regions, labels)
            }
        }
    }
}
