import SwiftUI
import PDFKit

struct ReportScreen: View {
    let followUp: FollowUpUi
    let onBack: () -> Void
    
    @State private var isGenerating = true
    @State private var pdfURL: URL? = nil
    @State private var errorMessage: String? = nil
    @State private var isShareSheetPresented = false
    
    var body: some View {
        VStack(spacing: 0) {
            LpmTopBar(title: "Medical Report", onBack: onBack)
            
            ZStack {
                Color(UIColor.systemGray6).edgesIgnoringSafeArea(.all)
                
                if isGenerating {
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .black))
                            .scaleEffect(1.5)
                        Text("Generating report...")
                            .foregroundColor(.gray)
                    }
                } else if let error = errorMessage {
                    VStack(spacing: 16) {
                        Text("⚠️")
                            .font(.system(size: 40))
                        Text(error)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                        
                        LpmPrimaryButton(text: "Try again", action: onBack)
                            .padding()
                    }
                } else if let url = pdfURL {
                    VStack {
                        PDFKitRepresentedView(url: url)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .padding()
                        
                        HStack(spacing: 12) {
                            Button(action: {
                                // Simple Print / Share logic is identical in iOS
                                isShareSheetPresented = true
                            }) {
                                HStack {
                                    Image(systemName: "printer")
                                    Text("Print")
                                }
                                .font(.headline)
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.black, lineWidth: 1))
                            }
                            
                            Button(action: {
                                isShareSheetPresented = true
                            }) {
                                HStack {
                                    Image(systemName: "square.and.arrow.up")
                                    Text("Share")
                                }
                                .font(.headline)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.black)
                                .cornerRadius(8)
                            }
                        }
                        .padding()
                        .background(Color.white.shadow(radius: 5))
                    }
                }
            }
        }
        .task {
            await generateReport()
        }
        .sheet(isPresented: $isShareSheetPresented) {
            if let url = pdfURL {
                ShareSheet(activityItems: [url])
            }
        }
    }
    
    private func generateReport() async {
        let patientName = SessionManager.shared.getUserName() ?? "Patient"
        if let url = await ReportRepository.generateOfflineReport(followUp: followUp, patientName: patientName) {
            self.pdfURL = url
        } else if let cached = LocalStore.shared.cachedReportURL(subscriptionId: followUp.id) {
            self.pdfURL = cached
        } else {
            self.errorMessage = "Failed to create PDF file."
        }
        self.isGenerating = false
    }
}

private struct PDFKitRepresentedView: UIViewRepresentable {
    let url: URL
    
    func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        return pdfView
    }
    
    func updateUIView(_ uiView: PDFView, context: Context) {
        if let document = PDFDocument(url: url) {
            uiView.document = document
        }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
