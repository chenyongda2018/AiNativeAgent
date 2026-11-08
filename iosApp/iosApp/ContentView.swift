import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(apiKey: LocalConfig.deepSeekApiKey)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose 自行处理状态栏和底部安全区；宿主重复避让会造成顶部双倍留白。
            // 只忽略 container，保留系统键盘安全区的避让行为。
            .ignoresSafeArea(.container, edges: [.top, .bottom])
    }
}
