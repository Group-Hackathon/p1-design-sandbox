import Foundation
import UIKit
import PDFKit

class PdfReportGenerator {
    static func generate(followUp: FollowUpUi, events: [TimelineEventResponse], patientName: String) -> URL? {
        let pdfMetaData = [
            kCGPDFContextCreator: "Pre-Appointment 1 App",
            kCGPDFContextAuthor: patientName,
            kCGPDFContextTitle: "Medical Follow-Up Report"
        ]
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = pdfMetaData as [String: Any]
        
        let pageWidth = 8.5 * 72.0
        let pageHeight = 11 * 72.0
        let pageRect = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)
        
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)
        
        let data = renderer.pdfData { (context) in
            context.beginPage()
            
            let titleBottom = addTitle(pageRect: pageRect, title: "Medical Follow-Up Report", author: patientName, context: context.cgContext)
            var cursor = titleBottom + 20
            
            let summaryBottom = addSummary(pageRect: pageRect, top: cursor, followUp: followUp, context: context.cgContext)
            cursor = summaryBottom + 40
            
            // Add Events
            let headerFont = UIFont.boldSystemFont(ofSize: 18.0)
            let headerAttributes: [NSAttributedString.Key: Any] = [.font: headerFont]
            let eventHeaderString = NSAttributedString(string: "Timeline of Events", attributes: headerAttributes)
            eventHeaderString.draw(at: CGPoint(x: 36, y: cursor))
            cursor += 30
            
            for event in events {
                if cursor > pageHeight - 100 {
                    context.beginPage()
                    cursor = 36
                }
                
                cursor = addEvent(pageRect: pageRect, top: cursor, event: event)
                cursor += 16
            }
        }
        
        // Save to temp directory
        let tempDir = FileManager.default.temporaryDirectory
        let fileName = "Report_\(followUp.id).pdf"
        let fileURL = tempDir.appendingPathComponent(fileName)
        
        do {
            try data.write(to: fileURL)
            return fileURL
        } catch {
            print("Could not save PDF file: \(error)")
            return nil
        }
    }
    
    private static func addTitle(pageRect: CGRect, title: String, author: String, context: CGContext) -> CGFloat {
        let titleFont = UIFont.boldSystemFont(ofSize: 24.0)
        let titleAttributes: [NSAttributedString.Key: Any] = [.font: titleFont]
        let attributedTitle = NSAttributedString(string: title, attributes: titleAttributes)
        attributedTitle.draw(at: CGPoint(x: 36, y: 36))
        
        let authorFont = UIFont.systemFont(ofSize: 14.0)
        let authorAttributes: [NSAttributedString.Key: Any] = [.font: authorFont, .foregroundColor: UIColor.darkGray]
        let attributedAuthor = NSAttributedString(string: "Patient: \(author)\nGenerated: \(Date().formatted())", attributes: authorAttributes)
        attributedAuthor.draw(at: CGPoint(x: 36, y: 70))
        
        return 110.0
    }
    
    private static func addSummary(pageRect: CGRect, top: CGFloat, followUp: FollowUpUi, context: CGContext) -> CGFloat {
        let textFont = UIFont.systemFont(ofSize: 14.0)
        let boldFont = UIFont.boldSystemFont(ofSize: 14.0)
        
        let summaryText = NSMutableAttributedString()
        summaryText.append(NSAttributedString(string: "Protocol: ", attributes: [.font: boldFont]))
        summaryText.append(NSAttributedString(string: "\(followUp.title)\n", attributes: [.font: textFont]))
        summaryText.append(NSAttributedString(string: "Start Date: ", attributes: [.font: boldFont]))
        summaryText.append(NSAttributedString(string: "\(followUp.startsAt.prefix(10))\n", attributes: [.font: textFont]))
        summaryText.append(NSAttributedString(string: "Status: ", attributes: [.font: boldFont]))
        summaryText.append(NSAttributedString(string: followUp.isActive ? "Active" : "Completed", attributes: [.font: textFont]))
        
        summaryText.draw(at: CGPoint(x: 36, y: top))
        
        return top + 60
    }
    
    private static func addEvent(pageRect: CGRect, top: CGFloat, event: TimelineEventResponse) -> CGFloat {
        let dateFont = UIFont.boldSystemFont(ofSize: 12.0)
        let contentFont = UIFont.systemFont(ofSize: 12.0)
        
        let maxWidth = pageRect.width - 72.0
        
        let isAi = event.type == "ai"
        let sender = isAi ? "Assistant (\(event.date_label))" : "Patient (\(event.date_label))"
        let color = isAi ? UIColor.systemBlue : UIColor.black
        
        var currentTop = top
        
        let senderAttr = NSAttributedString(string: sender, attributes: [.font: dateFont, .foregroundColor: color])
        senderAttr.draw(at: CGPoint(x: 36, y: currentTop))
        currentTop += 16
        
        let contentAttr = NSAttributedString(string: event.content, attributes: [.font: contentFont])
        let contentRect = contentAttr.boundingRect(with: CGSize(width: maxWidth, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, context: nil)
        
        contentAttr.draw(in: CGRect(x: 36, y: currentTop, width: maxWidth, height: contentRect.height))
        currentTop += contentRect.height
        
        let regex = try? NSRegularExpression(pattern: "(?i)photo.*?([\\w\\d_-]+\\.jpg)")
        if let match = regex?.firstMatch(in: event.content, range: NSRange(event.content.startIndex..., in: event.content)) {
            if let range = Range(match.range(at: 1), in: event.content) {
                let photoName = String(event.content[range])
                let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent(photoName)
                if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
                    currentTop += 10
                    // Draw thumbnail maintaining aspect ratio
                    let aspect = image.size.width / image.size.height
                    let height: CGFloat = 100
                    let width = height * aspect
                    let imgRect = CGRect(x: 36, y: currentTop, width: width, height: height)
                    image.draw(in: imgRect)
                    currentTop += height
                } else {
                    currentTop += 4
                    NSAttributedString(string: "[Photo unavailable]", attributes: [.font: contentFont, .foregroundColor: UIColor.gray]).draw(at: CGPoint(x: 36, y: currentTop))
                    currentTop += 16
                }
            }
        }
        
        return currentTop
    }
}
