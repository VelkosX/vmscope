import SwiftUI
import SampleShared

/// Bridges Kotlin's `SampleLog` into SwiftUI state. Subscribes once at init, receives lines
/// from both `initVmScope()` (bootstrap log) and `SharedSampleViewModel` (per-button logs) and
/// from the configured `onUnhandledException` callback (throwing-button path).
///
/// The `DispatchQueue.main.async` hop guards against the handler firing on a non-main
/// coroutine dispatcher — `@Published` mutations outside the main queue trip SwiftUI's
/// thread-check assertions. In practice `vmScope` uses `Dispatchers.Main.immediate` on iOS,
/// so the hop is defensive rather than functionally required.
final class LogStore: ObservableObject {
    @Published var lines: [String] = []

    init() {
        SampleLog.shared.onLine = { [weak self] line in
            DispatchQueue.main.async {
                self?.lines.append(line)
            }
        }
    }
}

/// Four-button demo, identical to the Android KMP sample (`sample-kmp/android-app`). Both
/// consumers drive the same `SharedSampleViewModel` (commonMain). Buttons delegate to the
/// ViewModel; results and handler callbacks surface in the log view via `SampleLog.onLine`.
struct ContentView: View {
    @StateObject private var log = LogStore()
    private let viewModel = SharedSampleViewModel()

    var body: some View {
        VStack(spacing: 12) {
            Text("vmScope sample")
                .font(.title2)
                .padding(.top, 24)
                .padding(.bottom, 12)

            Button("Successful launch") {
                viewModel.launchSuccessful()
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)

            Button("Throwing launch (vmScope)") {
                viewModel.launchThrowing()
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)

            Button("Deliberate viewModelScope (lint demo)") {
                viewModel.launchOnViewModelScopeEquivalent()
            }
            .buttonStyle(.bordered)
            .frame(maxWidth: .infinity)

            // iOS-specific comparison button: raw viewModelScope with no vmScope handler in
            // its CoroutineContext. Default iOS behavior → prints to Xcode console + app keeps
            // running. Contrast with the vmScope Throwing launch above which aborts in debug.
            Button("Throw on raw viewModelScope") {
                viewModel.launchThrowingOnViewModelScope()
            }
            .buttonStyle(.bordered)
            .tint(.orange)
            .frame(maxWidth: .infinity)

            Divider().padding(.vertical, 8)

            Text("Log")
                .font(.caption)
                .frame(maxWidth: .infinity, alignment: .leading)

            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(log.lines.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.system(.footnote, design: .monospaced))
                            .textSelection(.enabled)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            Spacer()
        }
        .padding(.horizontal, 20)
    }
}

#Preview {
    ContentView()
}
