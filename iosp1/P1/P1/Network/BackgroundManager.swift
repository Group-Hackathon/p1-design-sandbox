import Foundation
import BackgroundTasks

class BackgroundManager {
    static let shared = BackgroundManager()
    
    let backgroundTaskIdentifier = "com.preappointment1.app.sync"
    
    private init() {}
    
    func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: backgroundTaskIdentifier, using: nil) { task in
            self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
    }
    
    func scheduleAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: backgroundTaskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // Fetch no earlier than 15 minutes from now
        
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule app refresh: \(error)")
        }
    }
    
    private func handleAppRefresh(task: BGAppRefreshTask) {
        // Schedule next task right away
        scheduleAppRefresh()
        
        // Execute background sync
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        
        let syncOperation = BlockOperation {
            let semaphore = DispatchSemaphore(value: 0)
            
            Task {
                await SyncManager.shared.syncAll()
                semaphore.signal()
            }
            
            semaphore.wait()
        }
        
        task.expirationHandler = {
            queue.cancelAllOperations()
        }
        
        syncOperation.completionBlock = {
            task.setTaskCompleted(success: !syncOperation.isCancelled)
        }
        
        queue.addOperation(syncOperation)
    }
}
