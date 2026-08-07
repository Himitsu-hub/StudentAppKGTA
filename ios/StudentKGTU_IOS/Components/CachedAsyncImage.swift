import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Like AsyncImage, but uses ImageDiskCache (works offline after first online load).
struct CachedAsyncImage<Placeholder: View, Content: View>: View {
    let urlString: String
    let content: (Image) -> Content
    let placeholder: () -> Placeholder

    @State private var image: Image?
    @State private var failed = false

    init(
        urlString: String,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.urlString = urlString
        self.content = content
        self.placeholder = placeholder
    }

    var body: some View {
        Group {
            if let image {
                content(image)
            } else {
                placeholder()
            }
        }
        .task(id: urlString) {
            await load()
        }
    }

    private func load() async {
        guard !urlString.isEmpty else {
            failed = true
            return
        }
        // Sync disk first for instant offline paint
        if let data = ImageDiskCache.loadData(urlString: urlString),
           let ui = makeImage(from: data) {
            image = ui
            return
        }
        if let data = await ImageDiskCache.data(for: urlString),
           let ui = makeImage(from: data) {
            image = ui
        } else {
            failed = true
        }
    }

    private func makeImage(from data: Data) -> Image? {
        #if canImport(UIKit)
        if let ui = UIImage(data: data) {
            return Image(uiImage: ui)
        }
        #endif
        return nil
    }
}
