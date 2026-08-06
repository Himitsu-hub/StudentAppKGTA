import SwiftUI

/// Matches Android BrandSplash: theme color + university logo.
struct BrandSplash: View {
    let darkTheme: Bool

    var body: some View {
        ZStack {
            (darkTheme ? AppColors.darkNavy : AppColors.blueKGTA)
                .ignoresSafeArea()
            Image("KgtaLogo")
                .resizable()
                .scaledToFit()
                .frame(maxWidth: 200)
                .padding(.horizontal, 32)
        }
    }
}
