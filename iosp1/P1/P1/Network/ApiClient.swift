import Foundation

enum ApiError: Error {
    case invalidURL
    case serverError(statusCode: Int)
    case decodingError(Error)
    case underlying(Error)
    case authRefreshFailed
}

class ApiClient {
    static let shared = ApiClient()

    private let baseURL = URL(string: "https://living-patient-memory-api-772480669824.us-central1.run.app/")!
    private let session: URLSession
    private let refreshLock = NSLock()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    private func buildRequest(path: String, method: String, body: Data?) -> URLRequest {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = method

        if let token = SessionManager.shared.getToken() {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = body {
            request.httpBody = body
            request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        return request
    }

    private func isAuthPath(_ path: String) -> Bool {
        path.contains("auth/")
    }

    private func execute(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        allowAuthRetry: Bool
    ) async throws -> (Data, HTTPURLResponse) {
        let request = buildRequest(path: path, method: method, body: body)
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidURL
        }

        if httpResponse.statusCode == 401,
           allowAuthRetry,
           !isAuthPath(path) {
            let refreshed = await refreshTokenIfNeeded()
            if refreshed {
                return try await execute(
                    path: path,
                    method: method,
                    body: body,
                    allowAuthRetry: false
                )
            }
            throw ApiError.authRefreshFailed
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            throw ApiError.serverError(statusCode: httpResponse.statusCode)
        }

        return (data, httpResponse)
    }

    private func refreshTokenIfNeeded() async -> Bool {
        refreshLock.lock()
        defer { refreshLock.unlock() }
        return await AuthHelper.shared.refreshToken()
    }

    func performRequest<T: Decodable>(
        path: String,
        method: String = "GET",
        body: Encodable? = nil,
        allowAuthRetry: Bool = true
    ) async throws -> T {
        var bodyData: Data? = nil
        if let body = body {
            bodyData = try JSONEncoder().encode(body)
        }

        let (data, _) = try await execute(
            path: path,
            method: method,
            body: bodyData,
            allowAuthRetry: allowAuthRetry
        )

        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw ApiError.decodingError(error)
        }
    }

    func performRequest(
        path: String,
        method: String = "GET",
        body: Encodable? = nil,
        allowAuthRetry: Bool = true
    ) async throws {
        var bodyData: Data? = nil
        if let body = body {
            bodyData = try JSONEncoder().encode(body)
        }

        _ = try await execute(
            path: path,
            method: method,
            body: bodyData,
            allowAuthRetry: allowAuthRetry
        )
    }
}
