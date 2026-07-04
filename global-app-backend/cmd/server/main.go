package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/joho/godotenv"

	"github.com/Group-Hackathon/p1/global-app-backend/internal/auth"
	"github.com/Group-Hackathon/p1/global-app-backend/internal/db"
	"github.com/Group-Hackathon/p1/global-app-backend/internal/gemini"
	"github.com/Group-Hackathon/p1/global-app-backend/internal/handlers"
	ratelimitmw "github.com/Group-Hackathon/p1/global-app-backend/internal/middleware"
)

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

func main() {
	// Load .env file (optional, for local development)
	_ = godotenv.Load()

	// Database connection
	databaseURL := os.Getenv("DATABASE_URL")
	if databaseURL == "" {
		databaseURL = "postgres://patient:secure_password@localhost:5432/living_patient_memory?sslmode=disable"
	}

	ctx := context.Background()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		log.Fatalf("Unable to connect to database: %v", err)
	}
	defer pool.Close()

	// Run migrations
	if err := db.RunMigrations(ctx, pool); err != nil {
		log.Fatalf("Failed to run migrations: %v", err)
	}
	log.Println("Database migrations completed successfully")

	// Initialize dependencies
	jwtSecret := os.Getenv("JWT_SECRET")
	if jwtSecret == "" {
		jwtSecret = "dev-secret-change-in-production"
	}
	authService := auth.NewService(jwtSecret)
	// Initialize Gemini: AI Studio (API key) primary, Vertex AI fallback.
	geminiClient, err := gemini.NewClient(ctx, gemini.Config{
		APIKey:        os.Getenv("GEMINI_API_KEY"),
		Project:       firstNonEmpty(os.Getenv("GCP_PROJECT_ID"), os.Getenv("GOOGLE_CLOUD_PROJECT"), "living-patient-memory"),
		Location:      firstNonEmpty(os.Getenv("GCP_LOCATION"), os.Getenv("GOOGLE_CLOUD_REGION"), "us-central1"),
		Model:         firstNonEmpty(os.Getenv("GEMINI_MODEL"), "gemini-3.5-flash"),
		FallbackModel: firstNonEmpty(os.Getenv("VERTEX_FALLBACK_MODEL"), "gemini-2.5-flash"),
	})
	if err != nil {
		log.Printf("Warning: Failed to initialize Gemini client: %v", err)
	}
	defer func() {
		if geminiClient != nil {
			geminiClient.Close()
		}
	}()

	h := handlers.New(pool, authService, geminiClient)

	// Router
	r := chi.NewRouter()

	// Middleware
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(ratelimitmw.LimitBodySize)
	r.Use(ratelimitmw.GlobalPerIP())
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: true,
		MaxAge:           300,
	}))

	// Health check
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	// Public routes (no auth required)
	r.Group(func(r chi.Router) {
		r.Use(ratelimitmw.AuthPerIP())
		r.Post("/api/v1/auth/register", h.Register)
		r.Post("/api/v1/auth/login", h.Login)
		r.Post("/api/v1/auth/challenge", h.DeviceChallenge)
		r.Post("/api/v1/auth/device/register", h.DeviceRegister)
		r.Post("/api/v1/auth/device/verify", h.DeviceVerify)
		r.Post("/api/v1/auth/device/recover", h.DeviceRecover)
		r.Post("/api/v1/auth/refresh", h.RefreshTokens)
	})

	// Protected routes (JWT required)
	r.Group(func(r chi.Router) {
		r.Use(authService.Middleware)
		r.Use(ratelimitmw.APIPerIP())

		// Profiles
		r.Get("/api/v1/profiles", h.ListProfiles)
		r.Post("/api/v1/profiles", h.CreateProfile)

		// Agent catalog
		r.Get("/api/v1/agents", h.ListAgents)
		r.With(
			ratelimitmw.RecommendPerIP(),
			ratelimitmw.RecommendPerUser(func(r *http.Request) string {
				if uid, ok := r.Context().Value(auth.UserIDKey).(string); ok {
					return uid
				}
				return ""
			}),
		).Post("/api/v1/agents/recommend", handlers.RecommendAgentHandler(pool, geminiClient))

		// Subscriptions / Follow-ups
		r.Post("/api/v1/subscriptions", h.CreateSubscription)
		r.Get("/api/v1/subscriptions", h.ListSubscriptions)
		r.Patch("/api/v1/subscriptions/{id}", h.PatchSubscription)
		r.Delete("/api/v1/subscriptions/{id}", h.DeleteSubscription)

		// Account
		r.Delete("/api/v1/auth/delete", h.DeleteAccount)

		// Timeline
		r.Get("/api/v1/subscriptions/{id}/timeline", h.GetTimeline)
		r.Post("/api/v1/subscriptions/{id}/timeline", h.PostTimelineEvent)
		r.Delete("/api/v1/subscriptions/{id}/timeline/{eventId}", h.DeleteTimelineEvent)
	})

	// Start server
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	srv := &http.Server{
		Addr:    ":" + port,
		Handler: r,
	}

	// Graceful shutdown
	go func() {
		log.Printf("Server starting on port %s", port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}
	log.Println("Server exited")
}
